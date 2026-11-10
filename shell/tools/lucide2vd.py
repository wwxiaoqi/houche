#!/usr/bin/env python3
"""Lucide SVG（24 网格 / stroke 2 / round）-> Android VectorDrawable（描边版）。

只做机械转换：路径数据原样搬，填充置空，用 stroke 画。运行时 setColorFilter 上色。
支持 path / circle / line / rect / polyline。
"""
import re, sys, pathlib, math

def circle_path(cx, cy, r):
    return (f"M{cx-r},{cy}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0")

def rect_path(x, y, w, h, rx):
    if rx <= 0:
        return f"M{x},{y}h{w}v{h}h{-w}z"
    return (f"M{x+rx},{y}h{w-2*rx}a{rx},{rx} 0 0,1 {rx},{rx}v{h-2*rx}"
            f"a{rx},{rx} 0 0 1 {-rx},{rx}h{-(w-2*rx)}a{rx},{rx} 0 0 1 {-rx},{-rx}"
            f"v{-(h-2*rx)}a{rx},{rx} 0 0 1 {rx},{-rx}z")

def num(m, key, d=0.0):
    g = re.search(rf'\b{key}="([-\d.]+)"', m)
    return float(g.group(1)) if g else d

def convert(svg, size_dp=24):
    vb = re.search(r'viewBox="([\d.\-]+) ([\d.\-]+) ([\d.\-]+) ([\d.\-]+)"', svg)
    vw, vh = vb.group(3), vb.group(4)
    sw = re.search(r'stroke-width="([\d.]+)"', svg)
    sw = sw.group(1) if sw else "2"
    paths = []
    for m in re.finditer(r'<(path|circle|line|polyline|rect|polygon)\b([^>]*)/?>', svg):
        tag, attrs = m.group(1), m.group(2)
        d = None
        if tag == 'path':
            g = re.search(r'\bd="([^"]+)"', attrs)
            d = g.group(1) if g else None
        elif tag == 'circle':
            d = circle_path(num(attrs,'cx'), num(attrs,'cy'), num(attrs,'r'))
        elif tag == 'line':
            d = f"M{num(attrs,'x1')},{num(attrs,'y1')}L{num(attrs,'x2')},{num(attrs,'y2')}"
        elif tag in ('polyline','polygon'):
            g = re.search(r'\bpoints="([^"]+)"', attrs)
            if g:
                pts = [p for p in re.split(r'[\s,]+', g.group(1).strip()) if p]
                pairs = list(zip(pts[0::2], pts[1::2]))
                d = "M" + "L".join(f"{x},{y}" for x, y in pairs) + ("z" if tag=="polygon" else "")
        elif tag == 'rect':
            d = rect_path(num(attrs,'x'), num(attrs,'y'), num(attrs,'width'), num(attrs,'height'), num(attrs,'rx'))
        if not d:
            continue
        paths.append(
            '    <path\n'
            '        android:fillColor="#00000000"\n'
            '        android:strokeColor="#FF000000"\n'
            f'        android:strokeWidth="{sw}"\n'
            '        android:strokeLineCap="round"\n'
            '        android:strokeLineJoin="round"\n'
            f'        android:pathData="{d}" />'
        )
    assert paths, "no drawable elements"
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size_dp}dp" android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="{vw}" android:viewportHeight="{vh}">\n'
            + "\n".join(paths) + "\n</vector>\n")

if __name__ == "__main__":
    src, out = sys.argv[1], sys.argv[2]
    pathlib.Path(out).write_text(convert(pathlib.Path(src).read_text(encoding="utf-8")), encoding="utf-8")
    print("ok", out)
