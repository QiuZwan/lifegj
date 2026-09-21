# -*- coding: utf-8 -*-
"""把 glb 渲成一组"绕着转"的序列帧(PNG),给软件渲染设备当可旋转的替身。

为什么要它:Filament 在 SwiftShader(模拟器的软件 GPU)上跑不起来,而少帅就在模拟器上看效果 ——
"立在那里可以旋转"这件事必须在没有 GPU 的设备上也成立。
办法:事先在宿主机上用**纯 CPU** 把模型按固定角度渲染成 N 张图,应用里拖动就把角度映射成
"显示第几帧",于是不需要任何 GPU 也能转。

为什么不用 pyglet/pyrender:trimesh 的离屏渲染要 pyglet<2(本机是 2.x),而且那套还是要真 GL 上下文。
自己写的这个只用 numpy + Pillow,和现有环境一致,输出完全可控(取景、光照、对齐都自己定)。

关键点:
- **取景必须逐帧一致**:先按所有帧的并集包围盒裁一次,再统一缩放/居中,否则转动时会"抖"。
- 背面剔除 + 屏幕包围盒裁剪,60k 面在 24 帧下大约一两分钟。
- 光照只用 baseColor × (环境光 + 主光 Lambert),和官方预览的观感接近即可 —— 反正它在应用里
  只是软件渲染设备的替身,真机走 Filament。

用法:
  python design/butler3d/render_spin.py --glb design/butler3d/robot2_raw/model/butler2b_small.glb \
      --out-dir app/src/main/assets/butler_spin --frames 24 --cell 380 640
"""
import argparse
import io
import json
import math
import os
import struct
import sys

import numpy as np
from PIL import Image

AMBIENT = 0.86
DIFFUSE = 0.28
LIGHT = np.array([-0.42, 0.72, 0.55], dtype=np.float32)
LIGHT /= np.linalg.norm(LIGHT)


def load_glb(path):
    """读 GLB:返回 (positions, normals, uvs, faces, basecolor_image)。"""
    d = open(path, "rb").read()
    off = 12
    clen, ctype = struct.unpack("<I4s", d[off:off + 8]); off += 8
    g = json.loads(d[off:off + clen]); off += clen
    blen, btype = struct.unpack("<I4s", d[off:off + 8]); off += 8
    blob = d[off:off + blen]

    def acc(i):
        a = g["accessors"][i]
        bv = g["bufferViews"][a["bufferView"]]
        start = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
        ncomp = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a["type"]]
        dtype = {5120: "<i1", 5121: "<u1", 5122: "<i2", 5123: "<u2", 5125: "<u4", 5126: "<f4"}[a["componentType"]]
        cnt = a["count"] * ncomp
        arr = np.frombuffer(blob, dtype=dtype, count=cnt, offset=start)
        return arr.reshape(a["count"], ncomp) if ncomp > 1 else arr

    prim = g["meshes"][0]["primitives"][0]
    pos = acc(prim["attributes"]["POSITION"]).astype(np.float32)
    nrm = acc(prim["attributes"]["NORMAL"]).astype(np.float32)
    uv = acc(prim["attributes"]["TEXCOORD_0"]).astype(np.float32)
    idx = acc(prim["indices"]).astype(np.int32).reshape(-1, 3)
    print("  顶点 %d 三角 %d" % (len(pos), len(idx)))

    tex = None
    mat = g["materials"][prim["material"]] if "material" in prim else None
    if mat and "pbrMetallicRoughness" in mat and "baseColorTexture" in mat["pbrMetallicRoughness"]:
        ti = mat["pbrMetallicRoughness"]["baseColorTexture"]["index"]
        img = g["images"][g["textures"][ti]["source"]]
        bv = g["bufferViews"][img["bufferView"]]
        raw = blob[bv.get("byteOffset", 0): bv.get("byteOffset", 0) + bv["byteLength"]]
        tex = np.asarray(Image.open(io.BytesIO(raw)).convert("RGB"), dtype=np.float32) / 255.0
        print("  贴图 %s  %s" % (tex.shape, img.get("mimeType")))
    return pos, nrm, uv, idx, tex


def sample(tex, u, v):
    h, w, _ = tex.shape
    x = np.clip((u % 1.0) * (w - 1), 0, w - 1).astype(np.int32)
    y = np.clip((v % 1.0) * (h - 1), 0, h - 1).astype(np.int32)
    return tex[y, x]


def render(pos, nrm, uv, idx, tex, yaw, w, h, cam):
    """渲染一帧,返回 (RGB float 数组, alpha uint8 数组)。"""
    cx, cy, cz, dist, fov = cam
    a = math.radians(yaw)
    ca, sa = math.cos(a), math.sin(a)
    # 世界 -> 相机(绕 Y 旋转,视线朝 -Z)
    R = np.array([[ca, 0, -sa], [0, 1, 0], [sa, 0, ca]], dtype=np.float32)
    center = np.array([cx, cy, cz], dtype=np.float32)
    v = (pos - center) @ R.T
    v[:, 2] += dist
    n = nrm @ R.T

    f = (h / 2) / math.tan(math.radians(fov / 2))
    z = np.maximum(v[:, 2], 1e-4)
    sx = w / 2 + v[:, 0] / z * f
    sy = h / 2 - v[:, 1] / z * f

    color = np.zeros((h, w, 3), dtype=np.float32)
    alpha = np.zeros((h, w), dtype=np.uint8)
    zbuf = np.full((h, w), 1e9, dtype=np.float32)

    # 背面剔除:按**面**算。注意要在旋转后的空间里做 —— 相机在原点、朝 +Z,
    # 所以面的视线方向就是"面心坐标归一化"(第一版拿未旋转的 pos 和假的相机位置算,索引直接对不上)。
    fc = v[idx].mean(axis=1)
    fdir = fc / np.maximum(np.linalg.norm(fc, axis=1, keepdims=True), 1e-9)
    fn = n[idx].mean(axis=1)
    facing = np.einsum("ij,ij->i", fn, fdir) < -0.02
    tri = idx[facing]

    lam = np.clip(n @ LIGHT, 0, 1) * DIFFUSE + AMBIENT

    for t in tri:
        i0, i1, i2 = t
        x0, y0, z0 = sx[i0], sy[i0], z[i0]
        x1, y1, z1 = sx[i1], sy[i1], z[i1]
        x2, y2, z2 = sx[i2], sy[i2], z[i2]
        xmin = int(max(0, math.floor(min(x0, x1, x2)))); xmax = int(min(w - 1, math.ceil(max(x0, x1, x2))))
        ymin = int(max(0, math.floor(min(y0, y1, y2)))); ymax = int(min(h - 1, math.ceil(max(y0, y1, y2))))
        if xmax < xmin or ymax < ymin:
            continue
        px = np.arange(xmin, xmax + 1) + 0.5
        py = np.arange(ymin, ymax + 1) + 0.5
        gx, gy = np.meshgrid(px, py)
        d = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(d) < 1e-9:
            continue
        b0 = ((y1 - y2) * (gx - x2) + (x2 - x1) * (gy - y2)) / d
        b1 = ((y2 - y0) * (gx - x2) + (x0 - x2) * (gy - y2)) / d
        b2 = 1.0 - b0 - b1
        m = (b0 >= -1e-6) & (b1 >= -1e-6) & (b2 >= -1e-6)
        if not m.any():
            continue
        iz = b0 / z0 + b1 / z1 + b2 / z2
        zz = 1.0 / np.maximum(iz, 1e-9)
        sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
        m &= zz < sub
        if not m.any():
            continue
        sub[m] = zz[m]
        wsum = np.maximum(iz, 1e-9)
        uu = (b0 * uv[i0, 0] / z0 + b1 * uv[i1, 0] / z1 + b2 * uv[i2, 0] / z2) / wsum
        vv = (b0 * uv[i0, 1] / z0 + b1 * uv[i1, 1] / z1 + b2 * uv[i2, 1] / z2) / wsum
        base = sample(tex, uu, vv)
        shade = (lam[i0] * b0 + lam[i1] * b1 + lam[i2] * b2)[..., None]
        rgb = np.clip(base * shade, 0, 1)
        csub = color[ymin:ymax + 1, xmin:xmax + 1]
        csub[m] = rgb[m]
        asub = alpha[ymin:ymax + 1, xmin:xmax + 1]
        asub[m] = 255
    return color, alpha


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--glb", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--frames", type=int, default=24)
    ap.add_argument("--cell", type=int, default=380, help="输出单帧宽")
    ap.add_argument("--cell-h", type=int, default=640)
    ap.add_argument("--render-w", type=int, default=460, help="渲染分辨率(略大,便于统一缩放)")
    ap.add_argument("--render-h", type=int, default=780)
    ap.add_argument("--fov", type=float, default=30.0)
    ap.add_argument("--elev", type=float, default=10.0)
    ap.add_argument("--start-yaw", type=float, default=0.0)
    ap.add_argument("--contact", action="store_true", help="出一张拼图预览便于验收")
    ap.add_argument("--format", default="webp", choices=("webp", "png"),
                    help="帧格式。webp 带 alpha 且体积只有 png 的 1/4,Android 原生能解")
    ap.add_argument("--quality", type=int, default=88)
    args = ap.parse_args()

    pos, nrm, uv, idx, tex = load_glb(args.glb)

    # 这个服务导出的 glb 里,顶点数据是 **Z 轴朝上**的(高度落在 z 上,而且是负 z 方向),
    # 靠节点上的旋转摆正。我们读的是原始 accessor、不套节点变换,所以这里自己绕 X 转 +90°:
    # (x, y, z) -> (x, -z, y)。**注意是 -z**(写成 +z 会把机器人倒过来,第一版就是这样)。
    ext = pos.max(axis=0) - pos.min(axis=0)
    if ext[2] > ext[1] * 1.3:
        pos = np.stack([pos[:, 0], -pos[:, 2], pos[:, 1]], axis=1)
        nrm = np.stack([nrm[:, 0], -nrm[:, 2], nrm[:, 1]], axis=1)
        print("  顶点是 Z 朝上,已绕 X 转 +90° 换成 Y 朝上 (原 ext=%s)" % np.round(ext, 3))
    ext = pos.max(axis=0) - pos.min(axis=0)
    print("尺寸 (宽,高,深) = %s" % np.round(ext, 3))

    # 仰角靠把模型沿 X 轴反向旋转(等价于相机抬高一点)
    el = math.radians(args.elev)
    Rz = np.array([[1, 0, 0], [0, math.cos(el), -math.sin(el)], [0, math.sin(el), math.cos(el)]], dtype=np.float32)
    pos = pos @ Rz.T
    nrm = nrm @ Rz.T
    lo, hi = pos.min(axis=0), pos.max(axis=0)
    center = (lo + hi) / 2.0

    # 相机距离:按"模型要刚好装进画面"反算,再留 18% 边距 ——
    # 第一版用包围盒对角线当距离,结果相机落在模型内部,渲染出来是一片灰板。
    half = (hi - lo) / 2.0
    f = 1.0 / math.tan(math.radians(args.fov / 2))
    aspect = args.render_w / args.render_h
    need_h = half[1] * f
    need_w = half[0] * f / aspect
    dist = float(max(need_h, need_w) * 1.18 + half[2])
    print("取景: 中心 %s  距离 %.3f  半边 %s" % (np.round(center, 3), dist, np.round(half, 3)))

    cam = (float(center[0]), float(center[1]), float(center[2]), dist, args.fov)

    print("取景: 中心 %s  距离 %.3f" % (np.round(center, 3), dist))
    os.makedirs(args.out_dir, exist_ok=True)

    raws = []
    for k in range(args.frames):
        yaw = args.start_yaw + 360.0 * k / args.frames
        color, alpha = render(pos, nrm, uv, idx, tex, yaw, args.render_w, args.render_h, cam)
        raws.append((color, alpha))
        print("  第 %2d/%d 帧 yaw=%6.1f°" % (k + 1, args.frames, yaw))

    # 统一裁剪:所有帧的并集包围盒
    union = None
    for _, a in raws:
        ys, xs = np.nonzero(a > 0)
        if len(xs) == 0:
            continue
        box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
        union = box if union is None else (min(union[0], box[0]), min(union[1], box[1]),
                                           max(union[2], box[2]), max(union[3], box[3]))
    x0, y0, x1, y1 = union
    print("并集包围盒 %s  %dx%d" % ((x0, y0, x1, y1), x1 - x0, y1 - y0))

    pad = 6
    cw, chh = args.cell - 2 * pad, args.cell_h - 2 * pad
    bw, bh = x1 - x0, y1 - y0
    k = min(cw / bw, chh / bh)
    tw, th = max(1, round(bw * k)), max(1, round(bh * k))
    ox, oy = pad + (cw - tw) // 2, pad + (chh - th) // 2

    for i, (color, alpha) in enumerate(raws):
        rgba = np.concatenate([(color * 255).astype(np.uint8), alpha[..., None]], axis=2)
        im = Image.fromarray(rgba, "RGBA").crop((x0, y0, x1, y1)).resize((tw, th), Image.LANCZOS)
        out = Image.new("RGBA", (args.cell, args.cell_h), (0, 0, 0, 0))
        # 帧名:先用 png 名,最后按 --format 统一转(超采样下 LANCZOS 缩放已经消了锯齿)
        out.alpha_composite(im, (ox, oy))
        name = "f%02d.%s" % (i, args.format)
        if args.format == "webp":
            out.save(os.path.join(args.out_dir, name), "WEBP", quality=args.quality, method=6)
        else:
            out.save(os.path.join(args.out_dir, name), optimize=True)
    files = [os.path.join(args.out_dir, "f%02d.%s" % (i, args.format)) for i in range(args.frames)]
    total = sum(os.path.getsize(p) for p in files)
    print("输出 %d 帧 -> %s  (共 %.2f MB)" % (args.frames, args.out_dir, total / 1e6))

    if args.contact:
        cols = 8
        rows = (args.frames + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * args.cell // 3, rows * args.cell_h // 3), (0, 0, 0, 0))
        for i in range(args.frames):
            f = Image.open(files[i]).resize((args.cell // 3, args.cell_h // 3), Image.LANCZOS)
            sheet.alpha_composite(f, ((i % cols) * args.cell // 3, (i // cols) * args.cell_h // 3))
        p = os.path.join(os.path.dirname(args.out_dir.rstrip("/")), "spin_contact.png")
        bg = Image.new("RGBA", sheet.size, (246, 245, 241, 255))
        bg.alpha_composite(sheet)
        bg.convert("RGB").save(p)
        print("预览 -> %s" % p)


if __name__ == "__main__":
    main()
