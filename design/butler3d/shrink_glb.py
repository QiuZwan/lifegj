# -*- coding: utf-8 -*-
"""把图生 3D 出的 GLB 瘦身:贴图降分辨率 + 换编码,几何不动。

为什么单写这个:图生 3D 的输出默认是 4K PNG 贴图(实测一张 16MB),整个 glb 36MB,
直接塞进 APK 会让安装包翻三倍。而几何只有 1.6MB —— 体积全在贴图上。
降成 1024 之后角色在屏幕上最多占三四百像素,1024 已经绰绰有余,肉眼几乎无差。

编码策略(按贴图类型分开对待,不是一刀切):
  baseColor  -> JPEG。最大头,而且卡通角色的底色图没有 alpha,压 JPEG 收益最大。
  normal     -> PNG。法线图是"数据不是照片",JPEG 的块状伪影会在高光上显出来,
                所以只降分辨率不换编码。
  metallicRoughness -> PNG。同理,它是数值图。

GLB 结构上做的是"原地换血":
  几何的 bufferView 一个字节都不动(只重排索引),三张图的 bufferView 换成新编码的字节,
  重新拼 JSON chunk + BIN chunk。不引 gltf-transform 这类工具链,少一套 node 依赖。

用法:
  python design/butler3d/shrink_glb.py --src model/butler3d.glb --out model/butler3d_small.glb \
      --size 1024 --jpeg-quality 88
"""
import argparse
import io
import json
import os
import struct
import sys

from PIL import Image


def is_opaque(png_bytes):
    im = Image.open(io.BytesIO(png_bytes))
    return im.mode in ("RGB", "L") or (im.mode == "RGBA" and im.getextrema()[3] == (255, 255))


def encode(png_bytes, size, jpeg_quality, label):
    im = Image.open(io.BytesIO(png_bytes))
    w, h = im.size
    if max(w, h) > size:
        k = size / max(w, h)
        im = im.resize((max(1, round(w * k)), max(1, round(h * k))), Image.LANCZOS)
    if im.mode == "P":
        im = im.convert("RGBA")
    has_alpha = im.mode == "RGBA" and im.getextrema()[3][0] < 255
    if label == "normal" or label == "mr" or has_alpha:
        buf = io.BytesIO()
        im.save(buf, "PNG", optimize=True)
        mime, ext = "image/png", ".png"
    else:
        buf = io.BytesIO()
        im.convert("RGB").save(buf, "JPEG", quality=jpeg_quality, optimize=True)
        mime, ext = "image/jpeg", ".jpg"
    return buf.getvalue(), mime, im.size, has_alpha


def kind_of(name):
    n = (name or "").lower()
    if "normal" in n:
        return "normal"
    if "metallic" in n or "roughness" in n or "occlusion" in n or "orm" in n:
        return "mr"
    return "base"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=1024, help="贴图最长边")
    ap.add_argument("--jpeg-quality", type=int, default=88)
    args = ap.parse_args()

    d = open(args.src, "rb").read()
    off = 12
    clen, ctype = struct.unpack("<I4s", d[off:off + 8]); off += 8
    if ctype != b"JSON":
        sys.exit("第一个 chunk 不是 JSON,不是标准 GLB")
    g = json.loads(d[off:off + clen]); off += clen
    bin_len, bin_type = struct.unpack("<I4s", d[off:off + 8]); off += 8
    if bin_type != b"BIN\x00":
        sys.exit("第二个 chunk 不是 BIN")
    blob = d[off:off + bin_len]

    images = g.get("images", [])
    if not images:
        sys.exit("GLB 里没有图片,不用瘦身")
    img_bv = {im["bufferView"] for im in images if "bufferView" in im}

    out_blob = bytearray()
    new_views = []
    index_map = {}
    # 1) 几何的 bufferView 原字节照搬,只重排到新的位置
    for i, bv in enumerate(g["bufferViews"]):
        if i in img_bv:
            continue
        data = blob[bv.get("byteOffset", 0): bv.get("byteOffset", 0) + bv["byteLength"]]
        index_map[i] = len(new_views)
        nb = dict(bv); nb["byteOffset"] = len(out_blob)
        new_views.append(nb)
        out_blob += data
        if len(out_blob) % 4:
            out_blob += b"\x00" * (4 - len(out_blob) % 4)

    print("[in ] %s  %.1f MB, %d 张贴图, %d 个几何 bufferView"
          % (os.path.basename(args.src), len(d) / 1e6, len(images), len(new_views)))

    used = {index_map[i] for i in index_map}
    for i, im in enumerate(images):
        old = im["bufferView"]
        src = blob[g["bufferViews"][old].get("byteOffset", 0):
                   g["bufferViews"][old].get("byteOffset", 0) + g["bufferViews"][old]["byteLength"]]
        data, mime, size, has_alpha = encode(src, args.size, args.jpeg_quality, kind_of(im.get("name")))
        new_off = len(out_blob)
        out_blob += data
        if len(out_blob) % 4:
            out_blob += b"\x00" * (4 - len(out_blob) % 4)
        # buffer 必须带上:漏了它 Filament 会直接 "Unable to parse glTF file"
        # (校验器报 UNDEFINED_PROPERTY "Property 'buffer' must be defined",
        #  Blender 出的源文件每张图的 bufferView 都有 buffer:0,重建时不能丢)
        nb = {"buffer": 0, "byteOffset": new_off, "byteLength": len(data)}
        index_map[old] = len(new_views)
        new_views.append(nb)
        im["mimeType"] = mime
        if "name" in im:
            im["name"] = im["name"] + "_small"
        print("  image[%d] %-38s %8.1f KB -> %6.1f KB  %s %s%s"
              % (i, (im.get("name") or "")[:38],
                 g["bufferViews"][old]["byteLength"] / 1024, len(data) / 1024,
                 mime, size, "  (带alpha)" if has_alpha else ""))

    g["bufferViews"] = new_views
    for a in g.get("accessors", []):
        if "bufferView" in a:
            a["bufferView"] = index_map[a["bufferView"]]
    for im in images:
        im["bufferView"] = index_map[im["bufferView"]]
    # !! 这个必须跟着改,不然解析器发现 BIN chunk 和 buffers[0].byteLength 对不上,
    #    直接 "Unable to parse glTF file"(第一版就是漏了它,模型加载不出来)。
    for b in g.get("buffers", []):
        b["byteLength"] = len(out_blob)

    jbin = json.dumps(g, separators=(",", ":")).encode("utf-8")
    if len(jbin) % 4:
        jbin += b" " * (4 - len(jbin) % 4)
    if len(out_blob) % 4:
        out_blob += b"\x00" * (4 - len(out_blob) % 4)
    total = 12 + 8 + len(jbin) + 8 + len(out_blob)
    with open(args.out, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, total))
        f.write(struct.pack("<I4s", len(jbin), b"JSON"))
        f.write(jbin)
        f.write(struct.pack("<I4s", len(out_blob), b"BIN\x00"))
        f.write(out_blob)
    print("[out] %s  %.1f MB (原 %.1f MB, 压到 %.1f%%)"
          % (args.out, os.path.getsize(args.out) / 1e6, len(d) / 1e6,
             100 * os.path.getsize(args.out) / len(d)))


if __name__ == "__main__":
    main()
