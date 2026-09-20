# -*- coding: utf-8 -*-
"""把 ImageGen 出的 3D 管家图处理成 App 能直接用的透明位图。

做四件事:
  1) 抠掉背景(棋盘格/洋红底) -> 真 alpha
  2) 裁掉右下角"AI生成"水印 + 裁成"卡片自下方出画"的构图
  3) 缩到目标宽度
  4) 存 WebP(带 alpha) + 出浅/深色底预览图,方便肉眼验收

背景识别走"颜色谓词 + 边界漫水填充",不是全图按颜色删:
棋盘格是低对比近白(两色只差 ~12 灰阶),主体里也有近白高光,
只有"从画布边缘连通过来的"那片才算背景,才不会把机器人身上的白啃出洞。

用法(用**系统** Python 跑,它带 Pillow;托管 Python 里没有,numpy 也没有所以不用):
  # 现用的这组参数(切卡片左右边 / 自下方出画 / 切掉右下角水印)
  "C:/Users/26518/AppData/Local/Programs/Python/Python313/python.exe" design/butler3d/make_butler3d.py \
      --src design/butler3d/butler3d_source.png \
      --out app/src/main/res/drawable-xxhdpi/lb_butler3d.webp \
      --preview-dir design/butler3d/preview \
      --crop-left 257 --crop-right 1277 --crop-top 12 --crop-bottom 862 \
      --width 960 --quality 90

  不给 --crop-* 时,会自动按 alpha 外接框 + --pad 裁紧。
  出图后**一定要看 preview 里的深色底那张**有没有白边,并重新构建比对 APK。
"""
import argparse
import os
import sys

from PIL import Image, ImageFilter

LUMA = (299, 587, 114)


def bg_predicate(im, min_lum, max_sat, edge):
    """返回 bytearray,1 表示"颜色上像背景"。同时把 border 一圈强制当背景。"""
    w, h = im.size
    px = im.load()
    mask = bytearray(w * h)
    for y in range(h):
        row = y * w
        for x in range(w):
            r, g, b = px[x, y]
            lum = (r * LUMA[0] + g * LUMA[1] + b * LUMA[2]) // 1000
            sat = max(r, g, b) - min(r, g, b)
            if lum >= min_lum and sat <= max_sat:
                mask[row + x] = 1
    if edge:
        for x in range(w):
            mask[x] = 1
            mask[(h - 1) * w + x] = 1
        for y in range(h):
            mask[y * w] = 1
            mask[y * w + w - 1] = 1
    return mask


def erode(mask, w, h, r):
    """用 Pillow 的 MinFilter 做腐蚀(半径 r)。"""
    img = Image.frombytes("L", (w, h), bytes(255 if v else 0 for v in mask))
    img = img.filter(ImageFilter.MinFilter(2 * r + 1))
    return bytearray(1 if v else 0 for v in img.tobytes())


def dilate(mask, w, h, r):
    img = Image.frombytes("L", (w, h), bytes(255 if v else 0 for v in mask))
    img = img.filter(ImageFilter.MaxFilter(2 * r + 1))
    return bytearray(1 if v else 0 for v in img.tobytes())


def flood_from_border(mask, w, h):
    """扫描线漫水填充:从画布四边的候选点出发,标记连通的整片背景。"""
    out = bytearray(w * h)
    stack = []
    for x in range(w):
        stack.append((x, 0))
        stack.append((x, h - 1))
    for y in range(h):
        stack.append((0, y))
        stack.append((w - 1, y))
    while stack:
        x, y = stack.pop()
        i = y * w + x
        if out[i] or not mask[i]:
            continue
        xl = x
        while xl > 0 and mask[y * w + xl - 1] and not out[y * w + xl - 1]:
            xl -= 1
        xr = x
        while xr < w - 1 and mask[y * w + xr + 1] and not out[y * w + xr + 1]:
            xr += 1
        row = y * w
        for xx in range(xl, xr + 1):
            out[row + xx] = 1
        for ny in (y - 1, y + 1):
            if not (0 <= ny < h):
                continue
            base = ny * w
            xx = xl
            while xx <= xr:
                if mask[base + xx] and not out[base + xx]:
                    stack.append((xx, ny))
                    while xx <= xr and mask[base + xx]:
                        xx += 1
                else:
                    xx += 1
    return out


def bbox_of_alpha(alpha, w, h, thresh=8):
    minx, miny, maxx, maxy = w, h, -1, -1
    for y in range(h):
        row = y * w
        for x in range(w):
            if alpha[row + x] > thresh:
                if x < minx:
                    minx = x
                if x > maxx:
                    maxx = x
                if y < miny:
                    miny = y
                if y > maxy:
                    maxy = y
    if maxx < 0:
        return None
    return (minx, miny, maxx + 1, maxy + 1)


def make_alpha(im, args):
    w, h = im.size
    cand = bg_predicate(im, args.min_lum, args.max_sat, edge=True)
    print("[key] 颜色候选占比 %.1f%%" % (100.0 * sum(cand) / (w * h)))
    cand_e = erode(cand, w, h, args.erode)
    print("[key] 腐蚀 r=%d 后,从边缘漫水..." % args.erode)
    bg_e = flood_from_border(cand_e, w, h)
    print("[key] 连通背景占比 %.1f%%" % (100.0 * sum(bg_e) / (w * h)))
    bg = dilate(bg_e, w, h, args.dilate)
    alpha = bytearray(255 - (255 if v else 0) for v in bg)
    return alpha


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True, help="输出 webp 路径")
    ap.add_argument("--preview-dir", default=None)
    ap.add_argument("--min-lum", type=int, default=213)
    ap.add_argument("--max-sat", type=int, default=10)
    ap.add_argument("--erode", type=int, default=2)
    ap.add_argument("--dilate", type=int, default=2)
    ap.add_argument("--feather", type=float, default=0.7)
    ap.add_argument("--width", type=int, default=960)
    # 裁切:显式给了就用显式值(用来切掉水印 / 让卡片"自画面下方出画")
    ap.add_argument("--crop-left", type=int, default=None)
    ap.add_argument("--crop-right", type=int, default=None)
    ap.add_argument("--crop-top", type=int, default=None)
    ap.add_argument("--crop-bottom", type=int, default=None, help="裁到这个 y(含)以上,用来切掉水印")
    ap.add_argument("--pad", type=int, default=8, help="没给显式裁切时,裁紧 bbox 后四周留的空隙")
    ap.add_argument("--quality", type=int, default=88)
    args = ap.parse_args()

    im = Image.open(args.src).convert("RGB")
    w, h = im.size
    print("[in ]", os.path.basename(args.src), im.size)

    alpha = make_alpha(im, args)
    rgba = im.convert("RGBA")
    rgba.putalpha(Image.frombytes("L", (w, h), bytes(alpha)))

    box = bbox_of_alpha(alpha, w, h)
    print("[bbox] 主体", box)
    if box is None:
        sys.exit("没抠出任何主体,谓词阈值需要调")

    x0, y0, x1, y1 = box
    x0 = args.crop_left if args.crop_left is not None else max(0, x0 - args.pad)
    y0 = args.crop_top if args.crop_top is not None else max(0, y0 - args.pad)
    x1 = args.crop_right if args.crop_right is not None else min(w, x1 + args.pad)
    y1 = args.crop_bottom if args.crop_bottom is not None else min(h, y1 + args.pad)
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(w, x1), min(h, y1)
    if x1 - x0 < 8 or y1 - y0 < 8:
        sys.exit("裁切框太小: %s" % ((x0, y0, x1, y1),))
    print("[crop]", (x0, y0, x1, y1), "->", (x1 - x0, y1 - y0),
          "宽高比 %.3f" % ((x1 - x0) / (y1 - y0)))
    rgba = rgba.crop((x0, y0, x1, y1))

    if args.feather:
        a = rgba.split()[3].filter(ImageFilter.GaussianBlur(args.feather))
        rgba.putalpha(a)

    if rgba.width > args.width:
        th = round(rgba.height * args.width / rgba.width)
        rgba = rgba.resize((args.width, th), Image.LANCZOS)
    print("[out ]", rgba.size)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    rgba.save(args.out, "WEBP", quality=args.quality, method=6)
    print("[webp]", args.out, os.path.getsize(args.out), "B")

    if args.preview_dir:
        os.makedirs(args.preview_dir, exist_ok=True)
        base = os.path.splitext(os.path.basename(args.out))[0]
        for name, col in (("light", (247, 245, 241, 255)), ("dark", (18, 24, 20, 255))):
            canvas = Image.new("RGBA", (rgba.width + 40, rgba.height + 40), col)
            canvas.alpha_composite(rgba, (20, 20))
            p = os.path.join(args.preview_dir, "%s_preview_%s.png" % (base, name))
            canvas.convert("RGB").save(p)
            print("[prev]", p)


if __name__ == "__main__":
    main()
