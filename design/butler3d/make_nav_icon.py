# -*- coding: utf-8 -*-
"""把 ImageGen 出的「洋红底 3D 机器人头」做成导航栏图标（带 alpha 的方形位图）。

和 make_butler3d.py 的区别（所以单独一个脚本，别混用）：
  - 那张大图背景是**近白低对比**，靠 luma+sat 谓词；这张是**纯洋红**，靠色度谓词。
  - 洋红抠完边缘会留一层**洋红镶边**（抗锯齿把洋红和主体混在一起），
    所以抠完要做「去色边」：把带洋红偏色的像素的 r/b 压回 g 附近，再啃掉 1px 边缘。
  - 图标要**方形**（导航栏 Icon 是 24dp 方框），所以裁紧之后要补成正方形，
    并留出 --fill 比例的空隙，好和其它 22dp 线性图标视觉等大。

用法（用**系统** Python，它带 Pillow）：
  "C:/Users/26518/AppData/Local/Programs/Python/Python313/python.exe" design/butler3d/make_nav_icon.py \
      --src design/butler3d/icon_raw/xxx.png \
      --out app/src/main/res/drawable-xxhdpi/lb_nav_butler.webp \
      --preview-dir design/butler3d/icon_preview
"""
import argparse
import os
import sys

from PIL import Image, ImageFilter

LUMA = (299, 587, 114)


def magenta_candidates(im, tol):
    """返回 bytearray,1 = "颜色上像洋红背景"。"""
    w, h = im.size
    px = im.load()
    mask = bytearray(w * h)
    n = 0
    for y in range(h):
        row = y * w
        for x in range(w):
            r, g, b = px[x, y]
            # 洋红 = (255,0,255):红蓝高、绿低,且红蓝彼此接近
            if g < 160 and (r - g) > tol and (b - g) > tol and abs(r - b) < 110:
                mask[row + x] = 1
                n += 1
    return mask, n / (w * h)


def erode(mask, w, h, r):
    if r <= 0:
        return bytearray(mask)
    img = Image.frombytes("L", (w, h), bytes(255 if v else 0 for v in mask))
    img = img.filter(ImageFilter.MinFilter(2 * r + 1))
    return bytearray(1 if v else 0 for v in img.tobytes())


def dilate(mask, w, h, r):
    if r <= 0:
        return bytearray(mask)
    img = Image.frombytes("L", (w, h), bytes(255 if v else 0 for v in mask))
    img = img.filter(ImageFilter.MaxFilter(2 * r + 1))
    return bytearray(1 if v else 0 for v in img.tobytes())


def flood_from_border(mask, w, h):
    """扫描线漫水:只把"从画布边缘连通过来的"那片当背景。"""
    out = bytearray(w * h)
    stack = [(x, 0) for x in range(w)] + [(x, h - 1) for x in range(w)]
    stack += [(0, y) for y in range(h)] + [(w - 1, y) for y in range(h)]
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


def defringe(rgba, thresh=20):
    """去掉边缘的洋红偏色:把 r/b 比 g 高出来的那部分压回去。

    只在**没被抠成透明**的像素上做,所以不会伤到主体内部的正常颜色
    (主体里绿通道都不低,r/b 不会同时高出 g 很多)。

    thresh 是"r 和 b 都高出 g 多少才动手"。默认 20 只治**边缘镶边**;
    洋红背景还会给主体打一层**环境反光**(背光弹到身上的粉调),那层很淡,
    r-g / b-g 可能只有 8~19,所以治反光要把它降到 8 左右。
    绿色主体(r-g 为负)和中性金属(只有单边偏高)都不满足"两边同时偏高",不会被误伤。
    """
    w, h = rgba.size
    px = rgba.load()
    fixed = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            if (r - g) > thresh and (b - g) > thresh:
                excess = min(r - g, b - g)
                px[x, y] = (max(0, r - excess), g, max(0, b - excess), a)
                fixed += 1
    return fixed


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
    return None if maxx < 0 else (minx, miny, maxx + 1, maxy + 1)


def square_pad(rgba, fill):
    """补成正方形,主体占边长 fill 的比例(导航栏图标要留空隙,好和线性图标等大)。"""
    w, h = rgba.size
    side = max(1, int(round(max(w, h) / fill)))
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(rgba, ((side - w) // 2, (side - h) // 2))
    return canvas


def at_size(rgba, box_px, bg):
    """把图标按目标像素框渲染到一块底色上,用来**在构建之前**肉眼判断小尺寸下糊不糊。"""
    tile = Image.new("RGB", (box_px, box_px), bg)
    ic = rgba.resize((box_px, box_px), Image.LANCZOS)
    tile.paste(ic, (0, 0), ic)
    return tile


def grayscale(rgba):
    """模拟 Compose 的 ColorMatrix.setToSaturation(0f):按亮度转灰,alpha 不动。"""
    r, g, b, a = rgba.split()
    lum = Image.merge("RGB", (r, g, b)).convert("L")
    out = Image.merge("RGBA", (lum, lum, lum, a))
    return out


LIGHT = (247, 245, 241)
DARK = (18, 22, 18)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--preview-dir", default=None)
    ap.add_argument("--tol", type=int, default=55, help="洋红判定:r-g 与 b-g 都要大于它")
    ap.add_argument("--defringe", type=int, default=20,
                    help="去洋红偏色的阈值,见 defringe()。治边缘镶边用 20,治背景反光用 8 左右")
    ap.add_argument("--erode", type=int, default=1)
    ap.add_argument("--dilate", type=int, default=2)
    ap.add_argument("--eat", type=int, default=2, help="背景再往外扩几像素,啃掉洋红镶边")
    ap.add_argument("--feather", type=float, default=0.6)
    ap.add_argument("--size", type=int, default=256, help="输出边长(方形)")
    ap.add_argument("--fill", type=float, default=0.86, help="主体占边长比例")
    ap.add_argument("--cut-bottom", type=int, default=None,
                    help="先裁掉这个 y(含)以下的整条,用来切掉右下角生成水印")
    ap.add_argument("--clear-rect", action="append", default=[],
                    help="强行把这块矩形弄成全透明,格式 x0,y0,x1,y1(可给多次)。"
                         "生成水印是**浅色文字压在洋红上**,洋红谓词抠不掉它,只能这样挖掉;"
                         "比 --cut-bottom 好的地方是不必为了右下角一条把整个下边切掉")
    ap.add_argument("--quality", type=int, default=92)
    args = ap.parse_args()

    im = Image.open(args.src).convert("RGB")
    if args.cut_bottom is not None:
        im = im.crop((0, 0, im.width, args.cut_bottom))
    w, h = im.size
    print("[in ] %s %s" % (os.path.basename(args.src), im.size))

    cand, frac = magenta_candidates(im, args.tol)
    print("[key] 洋红候选占比 %.1f%%" % (100 * frac))
    bg_e = flood_from_border(erode(cand, w, h, args.erode), w, h)
    print("[key] 连通背景占比 %.1f%%" % (100 * sum(bg_e) / (w * h)))
    bg = dilate(bg_e, w, h, args.dilate + args.eat)     # eat: 多啃掉一圈镶边
    alpha = bytearray(0 if v else 255 for v in bg)

    for spec in args.clear_rect:
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

    rgba = im.convert("RGBA")
    rgba.putalpha(Image.frombytes("L", (w, h), bytes(alpha)))
    print("[key] 去色边像素 %d 个" % defringe(rgba, args.defringe))

    box = bbox_of_alpha(bytearray(rgba.split()[3].tobytes()), w, h)
    if box is None:
        sys.exit("没抠出主体,调 --tol")
    print("[bbox] %s -> %dx%d" % (box, box[2] - box[0], box[3] - box[1]))
    rgba = rgba.crop(box)

    rgba = square_pad(rgba, args.fill)
    if rgba.width != args.size:
        rgba = rgba.resize((args.size, args.size), Image.LANCZOS)
    if args.feather:
        rgba.putalpha(rgba.split()[3].filter(ImageFilter.GaussianBlur(args.feather)))
    print("[out ] %s" % (rgba.size,))

    a = rgba.split()[3]
    print("[chk] alpha 最小/最大 %s; 全透明 %.1f%%; 全不透明 %.1f%%"
          % (a.getextrema(),
             100 * a.histogram()[0] / (args.size ** 2),
             100 * a.histogram()[255] / (args.size ** 2)))
    s = args.size - 1
    corners = [rgba.getpixel(p)[3] for p in ((0, 0), (s, 0), (0, s), (s, s))]
    print("[chk] 四角 alpha(应为 0): %s" % corners)
    print("[chk] 中心 alpha(应 >0): %d" % rgba.getpixel((args.size // 2, args.size // 2))[3])

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    rgba.save(args.out, "WEBP", quality=args.quality, method=6)
    print("[webp] %s %d B" % (args.out, os.path.getsize(args.out)))

    if args.preview_dir:
        os.makedirs(args.preview_dir, exist_ok=True)
        base = os.path.splitext(os.path.basename(args.out))[0]
        # 1) 大图预览(浅底/深底)
        for name, col in (("light", LIGHT), ("dark", DARK)):
            canvas = Image.new("RGB", (rgba.width + 40, rgba.height + 40), col)
            canvas.paste(rgba, (20, 20), rgba)
            p = os.path.join(args.preview_dir, "%s_preview_%s.png" % (base, name))
            canvas.save(p)
            print("[prev]", p)
        # 2) 实际显示尺寸对照:22dp @ xxhdpi(3x) ≈ 66px,和导航栏里一样大
        cell = 66
        gap = 18
        strip = Image.new("RGB", (cell * 4 + gap * 5, cell + gap * 2 + 26), (255, 255, 255))
        from PIL import ImageDraw
        d = ImageDraw.Draw(strip)
        cells = [
            (at_size(rgba, cell, LIGHT), "浅底 彩色"),
            (at_size(grayscale(rgba), cell, LIGHT), "浅底 灰度"),
            (at_size(rgba, cell, DARK), "深底 彩色"),
            (at_size(grayscale(rgba), cell, DARK), "深底 灰度"),
        ]
        for i, (tile, label) in enumerate(cells):
            x = gap + i * (cell + gap)
            strip.paste(tile, (x, gap))
            d.text((x, gap + cell + 8), label, fill=(60, 60, 60))
        p = os.path.join(args.preview_dir, "%s_at22dp.png" % base)
        strip.save(p)
        print("[prev]", p, "(66px ≈ 22dp @3x,和导航栏实际显示一样大)")


if __name__ == "__main__":
    main()
