# -*- coding: utf-8 -*-
"""从一张抠好的机器人 RGBA 图,产出应用里的两件静态资源。

  lb_butler3d.webp —— 智能管家页的静态回退图(软件渲染设备 / 模型加载失败时显示)
  lb_nav_butler.webp —— 底部 tab「智能管家」的图标(头部特写)

两件都从**同一张抠图**出,所以和 3D 模型是同一台机器人,不会出现"真机是新机器人、
模拟器是旧机器人"的错位。

用法:
  python design/butler3d/make_assets_from_cut.py \
      --cut design/butler3d/robot2_raw/v2e_cut.png \
      --out-dir app/src/main/res/drawable-xxhdpi \
      --preview-dir design/butler3d/robot2_preview
"""
import argparse
import os

from PIL import Image


def content_box(im, floor=32):
    return im.split()[3].point(lambda v: 255 if v >= floor else 0).getbbox()


def page_image(cut, height=900, pad_ratio=0.04):
    """整身图:按内容裁紧 → 等比缩放到指定高度 → 留少量边(padding 透明)。"""
    b = content_box(cut)
    tight = cut.crop(b)
    k = height / tight.height
    tw, th = max(1, round(tight.width * k)), height
    tight = tight.resize((tw, th), Image.LANCZOS)
    pad = round(height * pad_ratio)
    out = Image.new("RGBA", (tw + 2 * pad, th + 2 * pad), (0, 0, 0, 0))
    out.alpha_composite(tight, (pad, pad))
    return out


def head_square(cut, band=0.46, zoom=1.06):
    """头部图标:取内容最上面 band 比例的横带当"头部"区域,在其四周取正方形。"""
    b = content_box(cut)
    x0, y0, x1, y1 = b
    ch = y1 - y0
    yb = y0 + round(ch * band)
    px = cut.split()[3].load()
    xs = []
    for y in range(y0, yb):
        for x in range(x0, x1):
            if px[x, y] >= 32:
                xs.append(x)
    if not xs:
        return cut.crop(b)
    hx0, hx1 = min(xs), max(xs)
    cy = (y0 + yb) // 2
    side = max(hx1 - hx0, yb - y0)
    side = round(side * zoom)
    cx = (hx0 + hx1) // 2
    half = side // 2
    box = (cx - half, cy - half, cx - half + side, cy - half + side)
    return cut.crop(box)


def checker_bg(im, light=(246, 245, 241), dark=(18, 22, 18)):
    """出一张"棋盘底"?不。出浅底/深底两张预览,便于验收边缘。"""
    outs = []
    for name, c in (("light", light), ("dark", dark)):
        bg = Image.new("RGBA", im.size, c + (255,))
        bg.alpha_composite(im)
        outs.append((name, bg.convert("RGB")))
    return outs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cut", required=True, help="抠好的 RGBA png")
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--preview-dir", default=None)
    ap.add_argument("--page-height", type=int, default=900)
    ap.add_argument("--nav-size", type=int, default=192)
    args = ap.parse_args()

    cut = Image.open(args.cut).convert("RGBA")
    os.makedirs(args.out_dir, exist_ok=True)

    page = page_image(cut, height=args.page_height)
    p1 = os.path.join(args.out_dir, "lb_butler3d.webp")
    page.save(p1, "WEBP", quality=88, method=6)
    print("页面回退图 %s  %s  %d KB" % (p1, page.size, os.path.getsize(p1) // 1024))

    head = head_square(cut)
    head = head.resize((args.nav_size, args.nav_size), Image.LANCZOS)
    p2 = os.path.join(args.out_dir, "lb_nav_butler.webp")
    head.save(p2, "WEBP", quality=90, method=6)
    print("底栏图标   %s  %s  %d KB" % (p2, head.size, os.path.getsize(p2) // 1024))

    if args.preview_dir:
        os.makedirs(args.preview_dir, exist_ok=True)
        for name, im in checker_bg(page):
            im.save(os.path.join(args.preview_dir, "page_%s.png" % name))
        for name, im in checker_bg(head):
            im.save(os.path.join(args.preview_dir, "nav_%s.png" % name))
        small = head.resize((44, 44), Image.LANCZOS).resize((176, 176), Image.NEAREST)
        small.save(os.path.join(args.preview_dir, "nav_at44px_zoom.png"))
        print("预览 -> %s" % args.preview_dir)


if __name__ == "__main__":
    main()
