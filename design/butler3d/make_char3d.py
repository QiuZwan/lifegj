# -*- coding: utf-8 -*-
"""把 ImageGen 出的「洋红底 3D 全身机器人」做成 App 用的两张图:
  1) 智能管家页的大插图(带 alpha,保持人物自身比例)
  2) 底部导航栏的图标(从同一张图裁出头,补成正方形)

为什么和 make_nav_icon.py 并存而不是改它:
  那个脚本处理的是「本来就只画了一个头」的图,输出**恒为正方形**;
  这里的主产出是人物大图,比例是人物自己的,还多一个"从大图裁头"的步骤。
  但**像素处理那套完全一样**(洋红色度谓词 + 边界漫水 + 去色边),所以直接 import 复用,
  不复制一遍 —— 复制出去将来改一处漏一处。

两张图必须同源:导航栏图标是页面角色的头,不然用户会觉得是两个人。

用法(用**系统** Python,它带 Pillow):
  "C:/Users/26518/AppData/Local/Programs/Python/Python313/python.exe" design/butler3d/make_char3d.py \
      --src design/butler3d/char_raw/xxx.png \
      --out app/src/main/res/drawable-xxhdpi/lb_butler3d.webp \
      --nav-out app/src/main/res/drawable-xxhdpi/lb_nav_butler.webp \
      --preview-dir design/butler3d/preview \
      --nav-preview-dir design/butler3d/icon_preview \
      --clear-rect 855,930,1024,1024 \
      --head-rect 272,78,802,572 \
      --width 1100
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_nav_icon import (  # noqa: E402  复用洋红抠图那一套
    LIGHT, DARK, at_size, bbox_of_alpha, defringe, dilate, erode,
    flood_from_border, grayscale, magenta_candidates, square_pad,
)


def clear_rects(alpha, w, h, specs):
    """把指定矩形强行弄成全透明(生成水印是浅色文字压在洋红上,洋红谓词抠不掉)。"""
    for spec in specs:
        try:
            x0, y0, x1, y1 = (int(v) for v in spec.split(","))
        except ValueError:
            sys.exit("--clear-rect 要 x0,y0,x1,y1: %r" % spec)
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(w, x1), min(h, y1)
        for y in range(y0, y1):
            row = y * w
            for x in range(x0, x1):
                alpha[row + x] = 0
        print("[key] 挖掉水印/杂物区 (%d,%d)-(%d,%d)" % (x0, y0, x1, y1))


def components(alpha, w, h, thresh=8):
    """把 alpha 上不透明的像素分成 4-连通的小块,按大小从大到小返回 [像素索引列表]。

    为什么要这个:从全身图里裁头的时候,矩形框里除了头还会捎上**旁边那条举起来的手臂**,
    裁出来就是头旁边挂一块飘着的碎片。头和手臂在框内并不相连,所以"只留最大那块"就能干净地滤掉它。
    """
    seen = bytearray(w * h)
    comps = []
    for start in range(w * h):
        if seen[start] or alpha[start] <= thresh:
            continue
        seen[start] = 1
        stack = [start]
        comp = []
        while stack:
            i = stack.pop()
            comp.append(i)
            x, y = i % w, i // w
            if x > 0 and not seen[i - 1] and alpha[i - 1] > thresh:
                seen[i - 1] = 1
                stack.append(i - 1)
            if x < w - 1 and not seen[i + 1] and alpha[i + 1] > thresh:
                seen[i + 1] = 1
                stack.append(i + 1)
            if y > 0 and not seen[i - w] and alpha[i - w] > thresh:
                seen[i - w] = 1
                stack.append(i - w)
            if y < h - 1 and not seen[i + w] and alpha[i + w] > thresh:
                seen[i + w] = 1
                stack.append(i + w)
        comps.append(comp)
    comps.sort(key=len, reverse=True)
    return comps


def keep_largest(rgba, thresh=8):
    w, h = rgba.size
    alpha = bytearray(rgba.split()[3].tobytes())
    comps = components(alpha, w, h, thresh)
    if not comps:
        return rgba, 0, []
    sizes = [len(c) for c in comps]
    keep = set(comps[0])
    out = bytearray(w * h)
    for i in keep:
        out[i] = alpha[i]
    rgba.putalpha(Image.frombytes("L", (w, h), bytes(out)))
    return rgba, sizes[0], sizes[1:]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True, help="页面大插图 webp")
    ap.add_argument("--nav-out", default=None, help="导航栏图标 webp(从同一张图裁头)")
    ap.add_argument("--preview-dir", default=None)
    ap.add_argument("--nav-preview-dir", default=None)
    ap.add_argument("--clear-rect", action="append", default=[])
    ap.add_argument("--head-rect", default=None,
                    help="头部裁切框 x0,y0,x1,y1(在**原图**坐标系里量)")
    ap.add_argument("--head-keep-largest", dest="head_keep_largest",
                    action="store_true", default=True,
                    help="裁头时只保留最大那块连通区域(默认开),用来滤掉捎带进来的手臂")
    ap.add_argument("--no-head-keep-largest", dest="head_keep_largest",
                    action="store_false")
    # 抠图参数:和 make_nav_icon.py 同默认值
    ap.add_argument("--tol", type=int, default=55)
    ap.add_argument("--erode", type=int, default=1)
    ap.add_argument("--dilate", type=int, default=2)
    ap.add_argument("--eat", type=int, default=2)
    ap.add_argument("--feather", type=float, default=0.6)
    ap.add_argument("--defringe", type=int, default=8,
                    help="去洋红偏色的阈值。默认 8:洋红背景会把粉调**反光**弹到机器人身上"
                         "(深色底上看得出来),那个偏色很淡,20 治不掉")
    # 大图
    ap.add_argument("--width", type=int, default=1100,
                    help="输出宽度(px),两个方向都会重采样。放在 drawable-xxhdpi 时,"
                         "屏幕上的物理像素 ≈ width * (设备dpi/480)。想不让 GPU 缩放,"
                         "就把它设成显示框的物理像素数(420dpi 模拟器上,占屏宽 0.76 的 274dp 框 ≈ 719px)")
    ap.add_argument("--quality", type=int, default=90)
    ap.add_argument("--pad", type=int, default=6, help="裁紧 bbox 后四周留的空隙")
    # 导航栏图标
    ap.add_argument("--nav-size", type=int, default=256)
    ap.add_argument("--nav-fill", type=float, default=0.86)
    args = ap.parse_args()

    im = Image.open(args.src).convert("RGB")
    w, h = im.size
    print("[in ] %s %s" % (os.path.basename(args.src), im.size))

    cand, frac = magenta_candidates(im, args.tol)
    print("[key] 洋红候选占比 %.1f%%" % (100 * frac))
    bg_e = flood_from_border(erode(cand, w, h, args.erode), w, h)
    print("[key] 连通背景占比 %.1f%%" % (100 * sum(bg_e) / (w * h)))
    bg = dilate(bg_e, w, h, args.dilate + args.eat)
    alpha = bytearray(0 if v else 255 for v in bg)
    clear_rects(alpha, w, h, args.clear_rect)

    keyed = im.convert("RGBA")
    keyed.putalpha(Image.frombytes("L", (w, h), bytes(alpha)))
    print("[key] 去色边/去反光像素 %d 个(阈值 %d)" % (defringe(keyed, args.defringe), args.defringe))

    # ---- 主产出:人物大图 ----
    box = bbox_of_alpha(bytearray(keyed.split()[3].tobytes()), w, h)
    if box is None:
        sys.exit("没抠出主体,调 --tol")
    x0, y0, x1, y1 = box
    print("[bbox] %s -> %dx%d  人物宽高比 %.3f"
          % (box, x1 - x0, y1 - y0, (x1 - x0) / (y1 - y0)))
    big = keyed.crop((max(0, x0 - args.pad), max(0, y0 - args.pad),
                      min(w, x1 + args.pad), min(h, y1 + args.pad)))
    if big.width != args.width:
        big = big.resize((args.width, max(1, round(big.height * args.width / big.width))),
                         Image.LANCZOS)
    if args.feather:
        big.putalpha(big.split()[3].filter(ImageFilter.GaussianBlur(args.feather)))
    print("[out ] 大插图 %s  宽高比 %.3f  <- Kotlin 的 aspectRatio() 要填这个数"
          % (big.size, big.width / big.height))

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    big.save(args.out, "WEBP", quality=args.quality, method=6)
    print("[webp] %s %d B" % (args.out, os.path.getsize(args.out)))

    if args.preview_dir:
        os.makedirs(args.preview_dir, exist_ok=True)
        base = os.path.splitext(os.path.basename(args.out))[0]
        for name, col in (("light", LIGHT), ("dark", DARK)):
            canvas = Image.new("RGB", (big.width + 40, big.height + 40), col)
            canvas.paste(big, (20, 20), big)
            p = os.path.join(args.preview_dir, "%s_preview_%s.png" % (base, name))
            canvas.convert("RGB").save(p)
            print("[prev]", p)

    # ---- 导航栏图标:同一张图裁头 ----
    if not args.nav_out:
        return
    if not args.head_rect:
        sys.exit("给了 --nav-out 就必须给 --head-rect")
    hx0, hy0, hx1, hy1 = (int(v) for v in args.head_rect.split(","))
    head = keyed.crop((hx0, hy0, hx1, hy1))
    if args.head_keep_largest:
        head, biggest, dropped = keep_largest(head)
        print("[head] 连通块 %d 个,最大 %d px;丢掉 %s"
              % (len(dropped) + 1, biggest, dropped if dropped else "无"))
    hb = bbox_of_alpha(bytearray(head.split()[3].tobytes()), head.width, head.height)
    if hb is None:
        sys.exit("头部裁切框里没有主体,重新量 --head-rect")
    print("[head] 裁框 %s -> alpha 外接框 %s" % ((hx0, hy0, hx1, hy1), hb))
    head = head.crop(hb)

    icon = square_pad(head, args.nav_fill)
    if icon.width != args.nav_size:
        icon = icon.resize((args.nav_size, args.nav_size), Image.LANCZOS)
    if args.feather:
        icon.putalpha(icon.split()[3].filter(ImageFilter.GaussianBlur(args.feather)))

    a = icon.split()[3]
    s = args.nav_size - 1
    print("[chk] 图标 alpha min/max %s; 全透明 %.1f%%"
          % (a.getextrema(), 100 * a.histogram()[0] / (args.nav_size ** 2)))
    print("[chk] 四角 alpha(应为 0): %s;  中心 alpha(应 >0): %d"
          % ([icon.getpixel(p)[3] for p in ((0, 0), (s, 0), (0, s), (s, s))],
             icon.getpixel((args.nav_size // 2, args.nav_size // 2))[3]))

    os.makedirs(os.path.dirname(args.nav_out), exist_ok=True)
    icon.save(args.nav_out, "WEBP", quality=92, method=6)
    print("[webp] %s %d B" % (args.nav_out, os.path.getsize(args.nav_out)))

    if args.nav_preview_dir:
        os.makedirs(args.nav_preview_dir, exist_ok=True)
        nb = os.path.splitext(os.path.basename(args.nav_out))[0]
        for name, col in (("light", LIGHT), ("dark", DARK)):
            canvas = Image.new("RGB", (icon.width + 40, icon.height + 40), col)
            canvas.paste(icon, (20, 20), icon)
            p = os.path.join(args.nav_preview_dir, "%s_preview_%s.png" % (nb, name))
            canvas.save(p)
            print("[prev]", p)
        cell, gap = 66, 18
        strip = Image.new("RGB", (cell * 4 + gap * 5, cell + gap * 2 + 26), (255, 255, 255))
        d = ImageDraw.Draw(strip)
        for i, (tile, label) in enumerate([
            (at_size(icon, cell, LIGHT), "浅底 彩色"),
            (at_size(grayscale(icon), cell, LIGHT), "浅底 灰度"),
            (at_size(icon, cell, DARK), "深底 彩色"),
            (at_size(grayscale(icon), cell, DARK), "深底 灰度"),
        ]):
            x = gap + i * (cell + gap)
            strip.paste(tile, (x, gap))
            d.text((x, gap + cell + 8), label, fill=(60, 60, 60))
        p = os.path.join(args.nav_preview_dir, "%s_at22dp.png" % nb)
        strip.save(p)
        print("[prev]", p, "(66px ≈ 22dp @3x,和导航栏实际显示一样大)")


if __name__ == "__main__":
    main()
