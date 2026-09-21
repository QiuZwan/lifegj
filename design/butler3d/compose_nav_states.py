# -*- coding: utf-8 -*-
"""把 grab_navbar.py 抓到的四张导航栏特写拼成一张「验收图」。

四张 = 亮/深 x 未选中/选中。拼图规则:
  - 先竖直自动裁掉导航栏上方的空白(那部分是页面内容,不是我们要看的东西),
    判据是「这一行的像素亮度明显偏离整幅的中位亮度」的行才算内容行;
  - 每一行左边放整条导航栏(看机器人跟另外四个线性图标搭不搭),
    右边放「智能管家」那一格的放大特写(看 24dp 下糊没糊)。

用法:
  python design/butler3d/compose_nav_states.py --out design/butler3d/nav_states.png
"""
import argparse
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))

STATES = [
    ("light", "gray", "亮色 · 未选中(灰)"),
    ("light", "color", "亮色 · 选中(全彩)"),
    ("dark", "gray", "深色 · 未选中(灰)"),
    ("dark", "color", "深色 · 选中(全彩)"),
]

BG = (255, 255, 255)
LABEL_COLOR = (40, 40, 40)


def font(size):
    for name in ("msyh.ttc", "msyhbd.ttc", "simhei.ttf", "arial.ttf"):
        p = os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts", name)
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except OSError:
                pass
    return ImageFont.load_default()


def trim_rows(im, pad=2):
    """竖直裁到「有内容」的行(亮度偏离中位的行),去掉导航栏上方的页面留白。"""
    g = im.convert("L")
    w, h = g.size
    d = g.tobytes()
    rows = []
    for y in range(h):
        row = d[y * w:(y + 1) * w]
        rows.append(sum(row) / w)
    srt = sorted(rows)
    med = srt[len(srt) // 2]
    hit = [y for y, v in enumerate(rows) if abs(v - med) > 10]
    if not hit:
        return im
    y0 = max(0, hit[0] - pad)
    y1 = min(h, hit[-1] + 1 + pad)
    return im.crop((0, y0, w, y1))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(HERE, "nav_states.png"))
    ap.add_argument("--width", type=int, default=980, help="整条导航栏在成品里的宽度")
    ap.add_argument("--cell-zoom", type=float, default=3.2, help="智能管家那格的额外放大倍数")
    args = ap.parse_args()

    bands = []
    for mode, state, label in STATES:
        p = os.path.join(HERE, "nav_%s_%s_navband3x.png" % (mode, state))
        if not os.path.exists(p):
            raise SystemExit("缺少 %s(先跑 grab_navbar.py)" % p)
        bands.append((label, trim_rows(Image.open(p).convert("RGB"))))

    W = args.width
    scaled = []
    for label, b in bands:
        # 3x 特写 -> 目标宽度,求出统一高度
        k = W / b.size[0]
        scaled.append((label, b, b.resize((W, max(1, int(b.size[1] * k))), Image.LANCZOS)))

    cell_w = int(W / 5.0 * args.cell_zoom)          # 单格宽度(五个 tab 等分)
    row_h = max(s[2].size[1] for s in scaled)
    gap, pad, label_w, header = 22, 18, 210, 46
    out_w = pad * 2 + label_w + W + gap + cell_w
    out_h = header + pad * 2 + len(scaled) * (row_h + gap) - gap

    canvas = Image.new("RGB", (out_w, out_h), BG)
    dr = ImageDraw.Draw(canvas)
    f_h = font(26)
    f_l = font(22)
    dr.text((pad, pad - 4), "底部导航栏 · 3D 小机器人图标实测(模拟器实拍)",
            font=f_h, fill=LABEL_COLOR)

    y = header + pad
    for label, full, img in scaled:
        dr.text((pad, y + row_h // 2 - 14), label, font=f_l, fill=LABEL_COLOR)
        x = pad + label_w
        canvas.paste(img, (x, y))
        # 智能管家 = 第 3 格:横向 x in [W*0.4, W*0.6]
        cell = full.crop((int(full.size[0] * 0.4), 0, int(full.size[0] * 0.6), full.size[1]))
        cell = cell.resize((cell_w, row_h), Image.LANCZOS)
        canvas.paste(cell, (x + W + gap, y))
        y += row_h + gap

    canvas.save(args.out)
    print("[拼图] %s  %dx%d" % (args.out, out_w, out_h))


if __name__ == "__main__":
    main()
