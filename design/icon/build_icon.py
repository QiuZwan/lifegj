#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从「墨环 + 玉绿四角星」logo 图提取几何，生成 Android adaptive icon 的三层 vector drawable。

- 环：最小二乘拟合正圆（外/内缘）。顶部竖缝做成**两侧平行**的直缝：外弧与内弧用不同角度，
  使缝在任意半径上的 x 宽度都等于实测的 12px（原图就是这样一道等宽竖缝）。
- 星：用**逐象限归一化折叠**量出 0~90° 的半径谱（这样四个尖不等长、中心微偏都不会污染形状），
  对称化后细密采样，再用 Douglas-Peucker 抽稀成多边形。不做形状假设，忠实保留原图的尖。

用法: python build_icon.py <logo.png> [RO_DP]
"""
import sys, math, os, statistics
from collections import Counter
from PIL import Image, ImageDraw

ROOT = "C:/Users/26518/.openclaw-autoclaw/workspace/lifebutler-android"
OUT = ROOT + "/app/src/main/res/drawable"
PREV = ROOT + "/.workbuddy/tools"


def fit_circle(pts):
    """Kasa 代数圆拟合 -> (cx, cy, r)"""
    n = len(pts)
    sx = sum(p[0] for p in pts); sy = sum(p[1] for p in pts)
    sxx = sum(p[0] ** 2 for p in pts); syy = sum(p[1] ** 2 for p in pts)
    sxy = sum(p[0] * p[1] for p in pts)
    sxz = sum(p[0] * (p[0] ** 2 + p[1] ** 2) for p in pts)
    syz = sum(p[1] * (p[0] ** 2 + p[1] ** 2) for p in pts)
    sz = sum(p[0] ** 2 + p[1] ** 2 for p in pts)
    M = [[sxx, sxy, sx, sxz], [sxy, syy, sy, syz], [sx, sy, n, sz]]
    for c in range(3):
        p = max(range(c, 3), key=lambda r: abs(M[r][c]))
        M[c], M[p] = M[p], M[c]
        for r in range(3):
            if r != c and M[c][c]:
                f = M[r][c] / M[c][c]
                for k in range(c, 4):
                    M[r][k] -= f * M[c][k]
    A, B, Cc = [M[i][3] / M[i][i] for i in range(3)]
    cx, cy = A / 2, B / 2
    return cx, cy, math.sqrt(Cc + cx * cx + cy * cy)


def douglas_peucker(pts, eps):
    if len(pts) < 3:
        return pts
    x1, y1 = pts[0]; x2, y2 = pts[-1]
    dx, dy = x2 - x1, y2 - y1
    L = math.hypot(dx, dy) or 1e-9
    dmax, idx = -1.0, 0
    for i in range(1, len(pts) - 1):
        d = abs(dy * (pts[i][0] - x1) - dx * (pts[i][1] - y1)) / L
        if d > dmax:
            dmax, idx = d, i
    if dmax > eps:
        return douglas_peucker(pts[:idx + 1], eps)[:-1] + douglas_peucker(pts[idx:], eps)
    return [pts[0], pts[-1]]


def main(path, ro_dp=32.5):
    src = Image.open(path).convert("RGB")
    W, H = src.size
    px = src.load()
    ink, green = set(), set()
    for y in range(H):
        for x in range(W):
            r, g, b = px[x, y]
            mx, mn = max(r, g, b), min(r, g, b)
            if mx < 100 and (mx - mn) < 40:
                ink.add((x, y))
            elif g > 55 and g > r + 25 and g > b + 25:
                green.add((x, y))

    # ── 1. 环：最小二乘拟合 ──
    xs = [p[0] for p in ink]; ys = [p[1] for p in ink]
    cx = (min(xs) + max(xs)) / 2.0; cy = (min(ys) + max(ys)) / 2.0
    ro = ((max(xs) - min(xs)) + (max(ys) - min(ys))) / 4.0
    for _ in range(3):
        outer, inner = [], []
        for i in range(720):
            deg = i / 2.0
            if abs((deg - 270 + 180) % 360 - 180) < 8:      # 跳过顶部竖缝
                continue
            th = math.radians(deg); dx, dy = math.cos(th), math.sin(th)
            rs = [q / 4.0 for q in range(int(ro * 0.45 * 4), int(ro * 1.25 * 4))
                  if (int(round(cx + dx * q / 4)), int(round(cy + dy * q / 4))) in ink]
            if not rs:
                continue
            outer.append((cx + dx * max(rs), cy + dy * max(rs)))
            inner.append((cx + dx * min(rs), cy + dy * min(rs)))
        c1 = fit_circle(outer); c2 = fit_circle(inner)
        cx, cy, ro, ri = (c1[0] + c2[0]) / 2, (c1[1] + c2[1]) / 2, c1[2], c2[2]
    print(f"环: 圆心=({cx:.2f},{cy:.2f}) 外半径={ro:.2f} 内半径={ri:.2f} 壁厚={ro-ri:.2f}"
          f"  壁厚/外半径={(ro-ri)/ro:.4f}")

    # ── 2. 顶部竖缝 ──
    yprobe = int(round(cy - (ro + ri) / 2))
    row = sorted(x for x in range(W) if (x, yprobe) in ink)
    runs, s = [], row[0]
    for i in range(1, len(row)):
        if row[i] != row[i - 1] + 1:
            runs.append((s, row[i - 1])); s = row[i]
    runs.append((s, row[-1]))
    gap = [(runs[i][1], runs[i + 1][0]) for i in range(len(runs) - 1)
           if runs[i][1] < cx < runs[i + 1][0]][0]
    slit = gap[1] - gap[0] - 1
    print(f"顶部竖缝: 宽={slit}px 中心x={(gap[0]+gap[1])/2:.1f}（环心x={cx:.1f}，偏差 {(gap[0]+gap[1])/2-cx:+.1f}px）")

    # ── 3. 星心（四个尖的中点，自洽） ──
    tips = []
    for k in range(4):
        best = None
        for p in green:
            a = (math.degrees(math.atan2(p[1] - cy, p[0] - cx)) + 360) % 360
            if k * 90 - 45 <= a < k * 90 + 45:
                d = math.hypot(p[0] - cx, p[1] - cy)
                if best is None or d > best[0]:
                    best = (d, p, a)
        tips.append(best)
    scx = (tips[1][1][0] + tips[3][1][0]) / 2
    scy = (tips[0][1][1] + tips[2][1][1]) / 2

    def star_r(deg):
        th = math.radians(deg); dx, dy = math.cos(th), math.sin(th)
        best = 0.0; r = 1.0
        while r < ro * 0.85:
            x = int(round(scx + dx * r)); y = int(round(scy + dy * r))
            if 0 <= x < W and 0 <= y < H and (x, y) in green:
                best = r
            r += 0.25
        return best

    Rk = [star_r(k * 90) for k in range(4)]
    a_px = sum(Rk) / 4
    print(f"星: 中心=({scx:.1f},{scy:.1f}) 相对环心偏({scx-cx:+.1f},{scy-cy:+.1f})")
    print(f"    四尖半径={['%.1f' % r for r in Rk]}  均值 a={a_px:.2f}  a/外半径={a_px/ro:.4f}")
    print("    （原图四尖不等长属生成抖动，下面按等长正星处理）")

    # ── 4. 逐象限归一化折叠：0~90° 半径谱 ──
    STEP = 0.25
    N = int(90 / STEP)                      # 0..360
    raw = []
    for j in range(N + 1):
        i = j * STEP
        sam = []
        for k in range(4):
            for sgn in (+1, -1):
                v = star_r(k * 90 + sgn * i)
                if v > 3:
                    sam.append(v / Rk[k])
        raw.append(sum(sam) / len(sam) if sam else 0.0)
    raw[0] = 1.0
    sym = [(raw[j] + raw[N - j]) / 2 for j in range(N // 2 + 1)]     # 关于 45° 对称化
    prof = sym + sym[-2::-1]                                        # 0..90°
    prof = [min(p, 1.0) for p in prof]                               # 尖上量到的微小越界(噪声)钳回
    prof = [prof[0]] + [(prof[i - 1] + 2 * prof[i] + prof[i + 1]) / 4
                        for i in range(1, len(prof) - 1)] + [prof[-1]]
    prof[0] = 1.0; prof[-1] = 1.0
    print(f"    折叠谱: 0°={prof[0]:.4f} 10°={prof[int(10/STEP)]:.4f} "
          f"20°={prof[int(20/STEP)]:.4f} 30°={prof[int(30/STEP)]:.4f} "
          f"45°={prof[int(45/STEP)]:.4f}  星腰/a={prof[int(45/STEP)]:.4f}")

    # ── 5. 换算到 108dp viewport ──
    K = ro_dp / ro
    C = 54.0
    RO, RI, SW, A = ro_dp, ri * K, slit * K, a_px * K
    print(f"\n108dp: 外半径={RO:.3f} 内半径={RI:.3f} 壁厚={RO-RI:.3f} 缝宽={SW:.3f} "
          f"星尖={A:.3f} 星腰={A*prof[int(45/STEP)]:.3f}  星尖/环孔={A/RI:.3f}")

    def pt(r, deg):
        th = math.radians(deg); return C + r * math.cos(th), C + r * math.sin(th)

    go = math.degrees(math.asin((SW / 2) / RO))
    gi = math.degrees(math.asin((SW / 2) / RI))
    aL, bR = pt(RO, 270 - go), pt(RO, 270 + go)
    aLi, bRi = pt(RI, 270 - gi), pt(RI, 270 + gi)
    n = lambda v: f"{v:.2f}".rstrip('0').rstrip('.')
    ring = (f"M{n(aL[0])},{n(aL[1])} A{n(RO)},{n(RO)} 0 1 0 {n(bR[0])},{n(bR[1])} "
            f"L{n(bRi[0])},{n(bRi[1])} A{n(RI)},{n(RI)} 0 1 1 {n(aLi[0])},{n(aLi[1])} Z")

    # 星：每个象限取「尖->尖」一段折线，DP 抽稀（容差 0.4% 星尖半径）
    quad, total = [], []
    for j in range(N + 1):
        quad.append(pt(A * prof[j], j * STEP))
    simp = douglas_peucker(quad, A * 0.004)
    print(f"    星轮廓: 密集采样 {len(quad)} 点 -> 抽稀 {len(simp)} 点/象限")
    for q in range(4):
        for (x, y) in simp[:-1]:
            total.append((C + (x - C) * math.cos(math.radians(q * 90)) - (y - C) * math.sin(math.radians(q * 90)),
                          C + (x - C) * math.sin(math.radians(q * 90)) + (y - C) * math.cos(math.radians(q * 90))))
    star = "M" + " L".join(f"{n(x)},{n(y)}" for x, y in total) + " Z"

    hexs = lambda c: "#%02X%02X%02X" % c
    cink = Counter(px[x, y] for (x, y) in ink).most_common(1)[0][0]
    cgrn = Counter(px[x, y] for (x, y) in green).most_common(1)[0][0]
    print(f"颜色: 底色 #F4F3EF（设计令牌 LbBg）| 墨 {hexs(cink)} | 玉绿 {hexs(cgrn)}")

    HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n')
    files = {
        "ic_launcher_background.xml":
            HEAD + '    <path\n        android:fillColor="#F4F3EF"\n'
                   '        android:pathData="M0,0h108v108h-108z" />\n</vector>\n',
        "ic_launcher_foreground.xml":
            HEAD + '    <!-- 「墨环 · 玉绿四角星」：墨环围住一颗玉绿四角芒，环在正上方留一道竖缝 -->\n'
                   f'    <path\n        android:fillColor="{hexs(cink)}"\n        android:pathData="{ring}" />\n'
                   f'    <path\n        android:fillColor="{hexs(cgrn)}"\n        android:pathData="{star}" />\n</vector>\n',
        "ic_launcher_mono.xml":
            HEAD + '    <!-- Android 13+ 主题图标单色层：只给剪影，颜色交给系统主题 -->\n'
                   f'    <path\n        android:fillColor="#FFFFFF"\n        android:pathData="{ring}" />\n'
                   f'    <path\n        android:fillColor="#FFFFFF"\n        android:pathData="{star}" />\n</vector>\n',
    }
    for name, body in files.items():
        with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(body)
        print(f"写出 {name}  {len(body)}B")

    # ── 6. 校验：模型 vs 原图 IoU ──
    def in_star(x, y):
        dx, dy = x - scx, y - scy
        r = math.hypot(dx, dy)
        deg = (math.degrees(math.atan2(dy, dx)) + 360) % 360
        j = min(int(round((deg % 90.0) / STEP)), N)
        return r <= a_px * prof[j]

    inter = union = 0
    for y in range(int(scy - a_px * 1.15), int(scy + a_px * 1.15)):
        for x in range(int(scx - a_px * 1.15), int(scx + a_px * 1.15)):
            if not (0 <= x < W and 0 <= y < H):
                continue
            m, g = in_star(x, y), (x, y) in green
            inter += m and g
            union += m or g
    print(f"\n星形 IoU（折叠模型 vs 原图绿色区域）= {inter/union:.4f}")

    # ── 7. 预览 + 叠回原图校验 ──
    def render(size, dp_span=108.0):
        """把 108dp 画布上的图形按 dp_span（默认整张画布）渲成 size×size，圆心居中。"""
        S = 4
        img = Image.new("RGB", (size * S, size * S), (0xF4, 0xF3, 0xEF))
        d = ImageDraw.Draw(img)
        u = size * S / dp_span
        off = size * S / 2.0 - C * u
        T = lambda x, y: (x * u + off, y * u + off)
        poly = [(C + RO * math.cos(math.radians(270 - go - i * 0.05)),
                 C + RO * math.sin(math.radians(270 - go - i * 0.05))) for i in range(0, 7200)]
        poly += [(C + RI * math.cos(math.radians(270 - gi + i * 0.05)),
                  C + RI * math.sin(math.radians(270 - gi + i * 0.05))) for i in range(0, 7200)]
        d.polygon([T(x, y) for x, y in poly], fill=cink)
        d.polygon([T(x, y) for x, y in total], fill=cgrn)
        return img.resize((size, size), Image.LANCZOS)

    # 原图裁剪是 cx±ro*1.06，故环外直径占面板 1/1.06。
    # 生成版把「2*ro_dp*1.06 这么宽的 dp 范围」铺满面板，两者比例就一致了，可以直接比。
    span_dp = 2 * ro_dp * 1.06
    band = Image.new("RGB", (256 * 6 + 40, 256 * 2 + 90), (255, 255, 255))
    crop = src.crop((int(cx - ro * 1.06), int(cy - ro * 1.06),
                     int(cx + ro * 1.06), int(cy + ro * 1.06))).resize((256, 256), Image.LANCZOS)
    band.paste(crop, (0, 0))
    band.paste(render(256, span_dp), (266, 0))
    f = 256 / (ro * 2.12)
    g = f / K
    tov = lambda X, Y: ((X - C) * g + 128, (Y - C) * g + 128)
    ov = crop.copy()
    d = ImageDraw.Draw(ov)
    d.line([tov(C + RO * math.cos(math.radians(270 - go - i * 0.05)),
                C + RO * math.sin(math.radians(270 - go - i * 0.05))) for i in range(0, 7200)],
           fill=(255, 0, 255), width=2)
    d.line([tov(C + RI * math.cos(math.radians(270 - gi + i * 0.05)),
                C + RI * math.sin(math.radians(270 - gi + i * 0.05))) for i in range(0, 7200)],
           fill=(255, 0, 255), width=2)
    d.line([tov(x, y) for x, y in total] + [tov(*total[0])], fill=(255, 0, 255), width=2)
    band.paste(ov, (532, 0))
    for i, s in enumerate((144, 96, 72, 48, 36)):
        band.paste(render(s, 72.0), (266 + i * 150, 272))   # 72dp = 启动器实际可见区
    band.save(PREV + "/icon_preview.png")
    print("写出 icon_preview.png（左=原图 中=生成 右=洋红描边叠回原图 下排=144/96/72/48/36px）")


if __name__ == "__main__":
    main(sys.argv[1], float(sys.argv[2]) if len(sys.argv) > 2 else 32.5)
