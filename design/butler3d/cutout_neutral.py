# -*- coding: utf-8 -*-
"""把"浅色背景上的白色机器人"抠出来 —— 用于本机渲染图(不是洋红底的 ImageGen 输出)。

难点:机器人是奶白(lum 230+),背景是浅灰蓝渐变(lum 184~224)。
  按颜色阈值一刀切会把机身一起切掉;按"与上一个像素的局部色差"漫水填充也不行 ——
  轮廓上有抗锯齿过渡带(185→190→200→215→235 每步只差 10 上下),填充分级"爬"进机身里
  (第一版实测:头壳、手臂、腹壳全被啃成透明)。

正解:**按行估计背景色**。背景只有纵向渐变(左右几乎没有变化),所以取每行最左/最右各 8 个像素
的中位数当作"这一行的背景色",再要求像素与它足够接近才算背景。轮廓上的过渡像素虽然跟邻居近,
但离本行背景色已经差了 20+,于是被挡住。连通性从四边出发,只删"从边界连通过来的"那片。

另外:地面有一片软接触阴影(比背景暗、无色偏),比背景暗但不属于机器人 ——
用"最下面一排仍有近黑像素(鞋底)的行"当分界,把它整条切掉。

输出:
  <out>_cut.png    RGBA 抠图(透明背景)
  <out>_white.png  贴到纯白方形画布(图生 3D 的输入)
  <out>_check_light.png / _check_dark.png  浅底/深底预览,用来肉眼验收边缘

用法:
  python design/butler3d/cutout_neutral.py --src views/view2.png --out robot2 --size 1024
"""
import argparse
import os
from collections import deque

from PIL import Image, ImageFilter


def row_bg(im, edge=8):
    """每行背景色 = 左右两端各 edge 个像素的中位数(RGB)。"""
    w, h = im.size
    px = im.load()
    refs = []
    for y in range(h):
        rs, gs, bs = [], [], []
        for x in list(range(min(edge, w))) + list(range(max(0, w - edge), w)):
            p = px[x, y]
            rs.append(p[0]); gs.append(p[1]); bs.append(p[2])
        rs.sort(); gs.sort(); bs.sort()
        m = len(rs) // 2
        refs.append((rs[m], gs[m], bs[m]))
    return refs


def cutout(im, tol=14):
    w, h = im.size
    px = im.load()
    refs = row_bg(im)
    bg = bytearray(w * h)
    q = deque()

    def is_bg(x, y):
        p = px[x, y]
        r = refs[y]
        return (abs(p[0] - r[0]) <= tol and abs(p[1] - r[1]) <= tol and abs(p[2] - r[2]) <= tol)

    for x in range(w):
        for y in (0, h - 1):
            if not bg[y * w + x] and is_bg(x, y):
                bg[y * w + x] = 1
                q.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if not bg[y * w + x] and is_bg(x, y):
                bg[y * w + x] = 1
                q.append((x, y))

    while q:
        x, y = q.popleft()
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < w and 0 <= ny < h and not bg[ny * w + nx] and is_bg(nx, ny):
                bg[ny * w + nx] = 1
                q.append((nx, ny))

    out = Image.new("RGBA", (w, h))
    op = out.load()
    kept = 0
    for y in range(h):
        for x in range(w):
            if bg[y * w + x]:
                op[x, y] = (0, 0, 0, 0)
            else:
                op[x, y] = px[x, y] + (255,)
                kept += 1
    return out, kept / (w * h)


def drop_ground_shadow(im, dark=95, band=12):
    """切掉脚底接触阴影:找"最下面一排含近黑像素(鞋底)的行",把它以下的内容全清掉;
    再把紧邻其上的 band 行里"低色彩 + 中高亮度"的灰影像素一并清掉 —— 那是向外摊开的软阴影,
    留在图里会让图生 3D 在底座上糊出一块灰片。"""
    w, h = im.size
    px = im.load()
    a = im.split()[3].load()
    last = -1
    for y in range(h):
        for x in range(w):
            if a[x, y] > 128:
                p = px[x, y]
                if 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2] < dark:
                    last = y
                    break
    if last < 0 or last >= h - 2:
        return im, 0
    out = im.copy()
    op = out.load()
    cut = 0
    for y in range(last + 1, h):
        for x in range(w):
            if op[x, y][3] > 0:
                op[x, y] = (0, 0, 0, 0)
                cut += 1
    for y in range(max(0, last - band), last + 1):
        for x in range(w):
            p = op[x, y]
            if p[3] == 0:
                continue
            lum = 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]
            chroma = max(p[0], p[1], p[2]) - min(p[0], p[1], p[2])
            if chroma < 18 and 165 < lum < 236:
                op[x, y] = (0, 0, 0, 0)
                cut += 1
    return out, cut


def despeckle(im, floor=32):
    """清掉低 alpha 残点:颜色相似判定会留下半透明的一圈"淡影",
    肉眼看不见,但会让 getbbox() 把边界算大、导致居中偏移。"""
    a = im.split()[3].point(lambda v: 0 if v < floor else v)
    out = im.copy()
    out.putalpha(a)
    return out


def on_square(im, size, pad_ratio=0.06, canvas=(255, 255, 255)):
    """贴到方形画布:先按内容裁紧,再按比例留白缩放。"""
    bbox = im.split()[3].point(lambda v: 255 if v > 32 else 0).getbbox()
    tight = im.crop(bbox)
    w, h = tight.size
    inner = int(size * (1 - 2 * pad_ratio))
    k = min(inner / w, inner / h)
    tight = tight.resize((max(1, round(w * k)), max(1, round(h * k))), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), canvas + (255,))
    out.alpha_composite(tight, ((size - tight.width) // 2, (size - tight.height) // 2))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True, help="输出前缀(不含扩展名)")
    ap.add_argument("--size", type=int, default=1024)
    ap.add_argument("--tol", type=int, default=14)
    ap.add_argument("--feather", type=int, default=1, help="alpha 羽化半径")
    ap.add_argument("--keep-shadow", action="store_true", help="保留脚底接触阴影")
    args = ap.parse_args()

    im = Image.open(args.src).convert("RGB")
    cut, ratio = cutout(im, args.tol)
    if not args.keep_shadow:
        cut, cut_px = drop_ground_shadow(cut)
        print("切掉脚底阴影 %d 像素" % cut_px)
    if args.feather > 0:
        a = cut.split()[3].filter(ImageFilter.GaussianBlur(args.feather))
        cut.putalpha(a)
    cut = despeckle(cut)
    d = os.path.dirname(args.out)
    if d:
        os.makedirs(d, exist_ok=True)
    cut.save(args.out + "_cut.png")
    white = on_square(cut, args.size)
    white.save(args.out + "_white.png")
    for name, bgc in (("light", (246, 245, 241)), ("dark", (18, 22, 18))):
        prev = Image.new("RGBA", (args.size, args.size), bgc + (255,))
        prev.alpha_composite(white)
        prev.convert("RGB").save("%s_check_%s.png" % (args.out, name))
    print("抠图保留占比 %.1f%%  尺寸 %s" % (ratio * 100, im.size))
    print("  %s_cut.png / _white.png(%dpx) / _check_light|dark.png" % (args.out, args.size))


if __name__ == "__main__":
    main()
