#!/usr/bin/env python3
"""没有模拟器时的版式预览（设计系统 v2）。

按工程自己的 res/values/colors.xml、values-night/colors.xml、values/kit.xml 和
res/drawable/lc_*.xml 渲染"默认首页"的版式稿，用的就是 App 里同一套色值、同一份图标 pathData。

用法（在工程根目录）：
    python3 tools/preview.py              # 出 视觉稿/<kit>.png 与 <kit>-night.png
    python3 tools/preview.py --name demo  # 自定义文件名

它**不是模拟器截图**，是用工程色值画的版式示意；字体按系统 Noto Sans/Serif CJK 渲染。
改这里的 build() 就能预览你自己的界面结构；不要指望它长成你的 App。
"""
import argparse
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
W = 390.0          # 逻辑宽度（dp）
H = 844.0          # 逻辑高度（dp）


# ---------- 读工程资源 ----------

def _res_dir():
    for p in (ROOT / "app/src/main/res", ROOT / "src/main/res"):
        if p.is_dir():
            return p
    raise SystemExit("找不到 res/ 目录（在工程根目录跑这个脚本）")


RES = _res_dir()


def colors(night):
    f = RES / ("values-night/colors.xml" if night else "values/colors.xml")
    if not f.is_file():
        f = RES / "values/colors.xml"
    txt = f.read_text(encoding="utf-8")
    return dict(re.findall(r'<color name="([^"]+)">(#\w+)</color>', txt))


def knobs():
    """kit.xml 里的结构旋钮。"""
    out = {}
    f = RES / "values/kit.xml"
    if f.is_file():
        out.update(dict(re.findall(r'<string name="([^"]+)">([^<]*)</string>',
                                   f.read_text(encoding="utf-8"))))
    return out


def dimen(name, default):
    f = RES / "values/kit.xml"
    if f.is_file():
        m = re.search(r'<dimen name="%s">([\d.]+)dp</dimen>' % re.escape(name),
                      f.read_text(encoding="utf-8"))
        if m:
            return float(m.group(1))
    return default


def gradient(night):
    f = RES / ("drawable-night/bg_gradient.xml" if night else "drawable/bg_gradient.xml")
    if not f.is_file():
        return None
    txt = f.read_text(encoding="utf-8")
    start = re.search(r'startColor="(#\w+)"', txt)
    end = re.search(r'endColor="(#\w+)"', txt)
    if not start or not end:
        return None
    return (start.group(1), end.group(1))


_ICONS = {}


def icon_paths(name):
    if name in _ICONS:
        return _ICONS[name]
    f = RES / "drawable" / (name + ".xml")
    ds = []
    if f.is_file():
        ds = re.findall(r'android:pathData="([^"]+)"', f.read_text(encoding="utf-8"))
    _ICONS[name] = ds
    return ds


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


class Svg:
    def __init__(self, night):
        self.night = night
        self.c = colors(night)
        self.k = knobs()
        self.scale = float(self.k.get("kit_type_scale", "1.0"))
        self.card_mode = self.k.get("kit_card", "none")
        self.density = self.k.get("kit_density", "comfy")
        self.font = self.k.get("kit_font", "sans")
        self.button = self.k.get("kit_button", "line")
        self.radius = dimen("kit_radius_card", 8.0)
        self.page = 20.0 if self.density == "compact" else 24.0
        self.bold = "700"
        self.med = "500"
        self.family = ("Noto Serif CJK SC, serif" if self.font == "serif"
                       else "Noto Sans CJK SC, sans-serif")
        self.parts = []

    # ----- 原子 -----
    def fill(self, name):
        return self.c[name]

    def add(self, s):
        self.parts.append(s)

    def rect(self, x, y, w, h, fill, r=0.0, stroke=None, sw=1.0):
        s = ' fill="%s"' % fill if fill else ' fill="none"'
        if stroke:
            s += ' stroke="%s" stroke-width="%s"' % (stroke, sw)
        if r:
            self.add('<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" rx="%.2f"%s/>'
                     % (x, y, w, h, min(r, w / 2.0, h / 2.0), s))
        else:
            self.add('<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f"%s/>'
                     % (x, y, w, h, s))

    def line(self, x1, y1, x2, y2, color, w=0.7):
        self.add('<line x1="%.2f" y1="%.2f" x2="%.2f" y2="%.2f" stroke="%s" stroke-width="%.2f"/>'
                 % (x1, y1, x2, y2, color, w))

    def t(self, x, base_y, s, size, color, weight=None, anchor="start", tracking=None):
        attrs = ' font-family="%s" font-size="%.2f" fill="%s" text-anchor="%s"' % (
            self.family, size, color, anchor)
        if weight:
            attrs += ' font-weight="%s"' % weight
        if tracking:
            attrs += ' letter-spacing="%.2f"' % tracking
        self.add('<text x="%.2f" y="%.2f"%s>%s</text>' % (x, base_y, attrs, esc(s)))

    def ts(self, size):
        return size * self.scale

    def icon(self, name, x, y, size, color, sw=2.0):
        k = size / 24.0
        body = "".join('<path d="%s"/>' % d for d in icon_paths(name))
        if not body:
            return
        self.add('<g transform="translate(%.2f,%.2f) scale(%.4f)" fill="none" stroke="%s" '
                 'stroke-width="%.2f" stroke-linecap="round" stroke-linejoin="round">%s</g>'
                 % (x, y, k, color, sw, body))

    def bar(self, x, y, w, pct, color):
        h = 3.0
        self.rect(x, y, w, h, self.fill("track"), r=h / 2)
        self.rect(x, y, max(w * pct / 100.0, h), h, color, r=h / 2)

    def card_begin(self, x, y, w, h):
        if self.card_mode == "fill":
            self.rect(x, y, w, h, self.fill("card"), r=self.radius)
        elif self.card_mode == "outline":
            self.rect(x, y, w, h, self.fill("card"), r=self.radius,
                      stroke=self.fill("cardStroke"), sw=1.0)

    # ----- 版式 -----
    def build(self):
        c = self.fill
        P = self.page
        no_card = self.card_mode == "none"
        inner = 0.0 if no_card else 20.0
        compact = self.density == "compact"
        row_h = 46.0 if compact else 52.0

        g = gradient(self.night)
        if g:
            self.add('<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">'
                     '<stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/>'
                     '</linearGradient></defs>' % g)
            self.add('<rect width="%.0f" height="%.0f" fill="url(#bg)"/>' % (W, H))
        else:
            self.rect(0, 0, W, H, c("bg"))

        y = 14.0
        # 页头
        self.t(P, y + 9, "NOV 02 · 周日", 11, c("text3"), self.med, tracking=0.9)
        self.icon("lc_refresh", W - P - 44 - 21 + 11, y + 6, 21, c("text2"))
        self.icon("lc_settings", W - P - 21 + 1, y + 6, 21, c("text2"))
        y += 9 + 8
        title_size = self.ts(22)
        self.t(P, y + title_size, "今日概览", title_size, c("text"), self.bold, tracking=-0.3)
        y += title_size + 26

        # 主角卡
        stat_block = 16 + 1 + 14 + 40
        hero_h = (inner * 2 if not no_card else 0) + 12 + 10 + self.ts(34) + 10 + 16 + stat_block
        self.card_begin(P, y, W - 2 * P, hero_h)
        cy = y + inner + 4
        self.t(P + inner, cy + 9, "可用余额", 11, c("text3"), self.med, tracking=0.8)
        cy += 12 + 10
        m_size = self.ts(34)
        self.t(P + inner, cy + m_size * 0.78, "128.40", m_size, c("text"), self.bold, tracking=-0.7)
        mw = len("128.40") * m_size * 0.56
        self.t(P + inner + mw + 4, cy + m_size * 0.78, "元", self.ts(13), c("text2"))
        cy += m_size + 10
        self.t(P + inner, cy + 12, "本周 +32.00 · 更新于 09:41", self.ts(12), c("text2"))
        cy += 16
        self.line(P + inner, cy, W - P - inner, cy, c("sep"), 0.7)
        cy += 14
        half = (W - 2 * P - 2 * inner) / 2.0
        for i, (label, value) in enumerate((("赠送余额", "8.40"), ("充值余额", "120.00"))):
            x = P + inner + i * half
            self.t(x, cy + 12, label, self.ts(12), c("text2"))
            self.t(x, cy + 12 + 5 + self.ts(14), value, self.ts(14), c("text"))
        y += hero_h + 26

        # 额度
        self.t(P, y + 9, "额度", 11, c("text3"), self.med, tracking=0.8)
        y += 18
        quota_h = 3 * (12 + 18 + 8 + 3 + 4) + (inner * 2 if not no_card else 0)
        self.card_begin(P, y, W - 2 * P, quota_h)
        qy = y + inner
        meter_color = c("text") if no_card else c("accent")
        for label, pct, color in (("五小时", 42, meter_color),
                                  ("本周", 72, meter_color),
                                  ("本月", 96, c("danger"))):
            self.t(P + inner, qy + 18, label, self.ts(14), c("text"))
            self.t(W - P - inner, qy + 18, "%d%%" % pct, self.ts(13), color, self.med, anchor="end")
            self.bar(P + inner, qy + 26, W - 2 * P - 2 * inner, pct, color)
            qy += 12 + 18 + 8 + 3 + 4
        y = qy + 20

        # 本周（迷你柱状图）
        self.t(P, y + 9, "本周", 11, c("text3"), self.med, tracking=0.8)
        y += 18
        week_h = 56 + 10 + 18 + (inner * 2 if not no_card else 14)
        self.card_begin(P, y, W - 2 * P, week_h)
        wy = y + (inner if not no_card else 14)
        vals = [38, 64, 46, 82, 58, 96, 30]
        active = 5
        left = P + inner
        ww = W - 2 * P - 2 * inner
        gap = 8.0
        col_w = (ww - gap * 6) / 7.0
        for i, v in enumerate(vals):
            bh = 56 * v / 100.0
            self.rect(left + i * (col_w + gap), wy + 56 - bh, col_w, bh,
                      c("accent") if i == active else c("track"), r=2)
        self.t(left, wy + 56 + 10 + 12, "周一至周日 · 今天 11-02", self.ts(12), c("text2"))
        y += week_h + 18
        self.t(W / 2, y + 10, "只保存在本机 · 只请求官方接口", self.ts(12), c("text3"),
               anchor="middle")

        # 底栏
        bar_top = H - 12 - 48 - 12
        if not no_card:
            self.line(0, bar_top, W, bar_top, c("sep"), 0.7)
        btn_r = {"pill": 24.0, "rect": 12.0}.get(self.button, 8.0)
        self.rect(P, bar_top + 12, W - 2 * P, 48, c("accentFill"), r=btn_r)
        self.t(W / 2, bar_top + 12 + 48 / 2 + self.ts(15) * 0.36, "记录一笔",
               self.ts(15), c("onAccent"), self.med, anchor="middle")
        return self.parts


def render(night, name):
    s = Svg(night)
    body = "".join(s.build())
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
           'viewBox="0 0 %d %d">%s</svg>') % (W * 3, H * 3, W, H, body)
    d = ROOT / "视觉稿"
    d.mkdir(exist_ok=True)
    (d / (name + ".svg")).write_text(svg, encoding="utf-8")
    out = d / (name + ".png")
    subprocess.run(["rsvg-convert", "-w", "780", "-o", str(out), str(d / (name + ".svg"))],
                   check=True)
    print("ok 视觉稿/" + name + ".png")


def main():
    ap = argparse.ArgumentParser(description="没有模拟器时的版式预览（设计系统 v2）")
    ap.add_argument("--name", default=None, help="输出文件名；默认取 UI-KIT.md 里的风格名")
    args = ap.parse_args()
    name = args.name
    if not name:
        name = "kit"
        brief = ROOT / "UI-KIT.md"
        if brief.is_file():
            for line in brief.read_text(encoding="utf-8").splitlines():
                line = line.strip()
                if line.startswith("# "):
                    name = line[2:].strip().split()[0]
                    break
    render(False, name)
    render(True, name + "-night")


if __name__ == "__main__":
    main()
