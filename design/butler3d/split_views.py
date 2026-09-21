# -*- coding: utf-8 -*-
"""四视图拆分:按"暗像素"的列分布找出四台机器人的水平范围,各裁一张单视图。

为什么用暗像素:背景是浅灰蓝渐变,机器人本体是奶白 —— 直接按"与背景不同"分不开。
但机器人身上有面罩、关节、脚掌这些深色件(L<150),背景里一个都没有,所以拿它当"存在信号"
最稳。列上出现暗像素 = 这一列有机器人。

用法:python design/butler3d/split_views.py --src <四视图> --out-dir <目录>
"""
import argparse
import os

from PIL import Image


def dark_profile(im, thresh=150):
    g = im.convert("L")
    w, h = g.size
    px = g.load()
    cols = []
    for x in range(w):
        n = 0
        for y in range(0, h, 2):
            if px[x, y] < thresh:
                n += 1
        cols.append(n)
    return cols


def groups(cols, min_gap=18, min_width=40):
    """把列信号切成若干连续段(gap 大于 min_gap 视为断开)。"""
    spans = []
    start = None
    gap = 0
    for x, v in enumerate(cols):
        if v > 0:
            if start is None:
                start = x
            gap = 0
        else:
            if start is not None:
                gap += 1
                if gap >= min_gap:
                    if x - gap - start + 1 >= min_width:
                        spans.append((start, x - gap))
                    start = None
                    gap = 0
    if start is not None:
        spans.append((start, len(cols) - 1 - gap))
    return spans


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--pad", type=int, default=26, help="水平留白")
    args = ap.parse_args()

    im = Image.open(args.src).convert("RGB")
    w, h = im.size
    spans = groups(dark_profile(im))
    print("检出 %d 个视图:" % len(spans))
    os.makedirs(args.out_dir, exist_ok=True)
    names = ["view1_front", "view2_threequarter", "view3_side", "view4_back"]
    for i, (a, b) in enumerate(spans):
        x0 = max(0, a - args.pad)
        x1 = min(w, b + args.pad + 1)
        crop = im.crop((x0, 0, x1, h))
        name = names[i] if i < len(names) else "view%d" % (i + 1)
        p = os.path.join(args.out_dir, name + ".png")
        crop.save(p)
        print("  %-20s x=%4d..%4d  宽 %4d  -> %s" % (name, x0, x1, x1 - x0, p))


if __name__ == "__main__":
    main()
