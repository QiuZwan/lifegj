#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 Android vector drawable 里的 pathData 按 SVG 规范（含 A 圆弧）解成多边形并用 PIL 渲染，
用来验证生成的 ic_launcher_*.xml 真的画得对——预览图是自己算的多边形，验不了 A 指令。
用法: python svg_check.py [输出png]
"""
import re, sys, math, os
from PIL import Image, ImageDraw

RES = "C:/Users/26518/.openclaw-autoclaw/workspace/lifebutler-android/app/src/main/res/drawable"
OUT = sys.argv[1] if len(sys.argv) > 1 else \
    "C:/Users/26518/.openclaw-autoclaw/workspace/lifebutler-android/.workbuddy/tools/icon_xml_check.png"

TOK = re.compile(r"[MmLlAaZzHhVv]|-?\d*\.?\d+(?:[eE][-+]?\d+)?")


def arc_to_points(x0, y0, rx, ry, phi, laf, sf, x1, y1, n=None):
    """SVG 椭圆弧端点参数 -> 圆心参数（规范 F.6.5），返回折线点。"""
    phi = math.radians(phi)
    if x0 == x1 and y0 == y1:
        return []
    rx, ry = abs(rx), abs(ry)
    xp = math.cos(phi) * (x0 - x1) / 2 + math.sin(phi) * (y0 - y1) / 2
    yp = -math.sin(phi) * (x0 - x1) / 2 + math.cos(phi) * (y0 - y1) / 2
    lam = xp * xp / (rx * rx) + yp * yp / (ry * ry)
    if lam > 1:
        s = math.sqrt(lam); rx *= s; ry *= s
    num = rx * rx * ry * ry - rx * rx * yp * yp - ry * ry * xp * xp
    den = rx * rx * yp * yp + ry * ry * xp * xp
    co = math.sqrt(max(0.0, num / den)) * (-1 if laf == sf else 1)
    cxp = co * rx * yp / ry
    cyp = -co * ry * xp / rx
    cx = math.cos(phi) * cxp - math.sin(phi) * cyp + (x0 + x1) / 2
    cy = math.sin(phi) * cxp + math.cos(phi) * cyp + (y0 + y1) / 2
    a1 = math.atan2((yp - cyp) / ry, (xp - cxp) / rx)
    a2 = math.atan2((-yp - cyp) / ry, (-xp - cxp) / rx)
    da = a2 - a1
    if not sf and da > 0:
        da -= 2 * math.pi
    elif sf and da < 0:
        da += 2 * math.pi
    if n is None:
        n = max(8, int(abs(math.degrees(da)) / 0.35))
    pts = []
    for i in range(1, n + 1):
        t = a1 + da * i / n
        ex = cx + rx * math.cos(t) * math.cos(phi) - ry * math.sin(t) * math.sin(phi)
        ey = cy + rx * math.cos(t) * math.sin(phi) + ry * math.sin(t) * math.cos(phi)
        pts.append((ex, ey))
    return pts


def parse(d):
    """只支持本文件用到的绝对命令 M / L / A / Z"""
    t = TOK.findall(d)
    subs, cur = [], []
    i, cx, cy, sx, sy = 0, 0.0, 0.0, 0.0, 0.0
    while i < len(t):
        c = t[i]
        if c == "M":
            if cur:
                subs.append(cur)
            cx, cy = float(t[i + 1]), float(t[i + 2]); sx, sy = cx, cy
            cur = [(cx, cy)]; i += 3
        elif c == "L":
            cx, cy = float(t[i + 1]), float(t[i + 2]); cur.append((cx, cy)); i += 3
        elif c in "Hh":
            v = float(t[i + 1]); cx = v if c == "H" else cx + v
            cur.append((cx, cy)); i += 2
        elif c in "Vv":
            v = float(t[i + 1]); cy = v if c == "V" else cy + v
            cur.append((cx, cy)); i += 2
        elif c == "A":
            rx, ry, rot, laf, sf = (float(t[i + 1]), float(t[i + 2]), float(t[i + 3]),
                                    int(float(t[i + 4])), int(float(t[i + 5])))
            x1, y1 = float(t[i + 6]), float(t[i + 7])
            cur.extend(arc_to_points(cx, cy, rx, ry, rot, laf, sf, x1, y1))
            cx, cy = x1, y1; i += 8
        elif c in "Zz":
            cur.append((sx, sy)); i += 1
        else:
            raise SystemExit(f"未支持的命令 {c}")
    if cur:
        subs.append(cur)
    return subs


def paths(fname):
    src = open(os.path.join(RES, fname), encoding="utf-8").read()
    return [(m.group(1), m.group(2)) for m in
            re.finditer(r'fillColor="(#[0-9A-Fa-f]{6,8})"\s+android:pathData="([^"]+)"', src)]


def render(fname, size=512, bg=(0xF4, 0xF3, 0xEF)):
    img = Image.new("RGB", (size, size), bg)
    d = ImageDraw.Draw(img)
    u = size / 108.0
    for col, data in paths(fname):
        c = tuple(int(col[1 + i * 2:3 + i * 2], 16) for i in range(3))
        for sub in parse(data):
            if len(sub) > 2:
                d.polygon([(x * u, y * u) for x, y in sub], fill=c)
    return img


if __name__ == "__main__":
    band = Image.new("RGB", (512 * 3 + 60, 512 + 90), (255, 255, 255))
    for i, f in enumerate(("ic_launcher_foreground.xml", "ic_launcher_mono.xml",
                           "ic_launcher_background.xml")):
        band.paste(render(f), (i * 532, 0))
    for i, s in enumerate((144, 96, 72, 48, 36)):
        band.paste(render("ic_launcher_foreground.xml", s * 4).resize((s, s), Image.LANCZOS),
                   (10 + i * 150, 522))
    band.save(OUT)
    print("写出", OUT)
    for f in ("ic_launcher_foreground.xml", "ic_launcher_mono.xml", "ic_launcher_background.xml"):
        ps = paths(f)
        print(f"{f}: {len(ps)} 条 path，" +
              " / ".join(f"{c} {len(parse(p))} 子路径 {sum(len(s) for s in parse(p))} 点" for c, p in ps))
