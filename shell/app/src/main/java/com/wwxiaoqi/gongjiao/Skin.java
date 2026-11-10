package com.wwxiaoqi.gongjiao;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 设计系统 v2。色值全部走 R.color（values / values-night 两套），深色模式不改一行逻辑。
 *
 * 五条硬规矩（每一条都有参考样本的实测依据，见 references/design-system.md）：
 * 1. 一个强调色，面积不超过整屏 2.5%：只做动作 / 状态 / 一条细线，不做面积。
 * 2. 层次靠一档近白灰（#F5F5F7~#F8F9FA 量级）+ 字号断层，不靠边框堆叠。
 * 3. 圆角只有四档：胶囊(999) / 大卡(20-24) / 卡(12-16) / 小件(8)。
 * 4. 标题 700 字重 + 负字距；正文 15sp / 行高 1.5+，正文永不加字距。
 * 5. 零 elevation。阴影要么全有要么全无，本系统选"全无"，用底色分层。
 */
public final class Skin {

    public static final float R_CARD = 18f;
    public static final float R_PILL = 999f;
    public static final float R_SMALL = 8f;

    // 字号阶梯（五档封顶）
    public static final float T_METRIC = 34f;
    public static final float T_TITLE = 22f;
    public static final float T_CARD = 17f;
    public static final float T_BODY = 15f;
    public static final float T_NOTE = 12f;
    // 兼容旧名（对外仍可用）
    public static final float T_LABEL = 15f;
    public static final float T_CAPTION = 12f;
    public static final float T_CHIP = 11f;

    private Skin() {
    }

    // ---------- 颜色 ----------
    public static int c(Context ctx, int res) {
        return ctx.getResources().getColor(res, ctx.getTheme());
    }

    public static int bg(Context c) { return c(c, R.color.bg); }

    /** 页面背景：kit 提供了 bg_gradient 就用渐变（warm），否则纯色 */
    public static Drawable background(Context c) {
        int id = c.getResources().getIdentifier("bg_gradient", "drawable", c.getPackageName());
        if (id != 0) {
            try {
                return c.getResources().getDrawable(id, c.getTheme());
            } catch (Exception ignored) {
                // 退回纯色
            }
        }
        return new android.graphics.drawable.ColorDrawable(bg(c));
    }

    public static int cardColor(Context c) { return c(c, R.color.card); }
    public static int cardStroke(Context c) { return c(c, R.color.cardStroke); }
    public static int accentFill(Context c) { return c(c, R.color.accentFill); }
    public static int onAccent(Context c) { return c(c, R.color.onAccent); }
    public static int okText(Context c) { return c(c, R.color.okText); }
    public static int warnText(Context c) { return c(c, R.color.warnText); }
    public static int dangerText(Context c) { return c(c, R.color.dangerText); }
    public static int sep(Context c) { return c(c, R.color.sep); }
    public static int track(Context c) { return c(c, R.color.track); }
    public static int text(Context c) { return c(c, R.color.text); }
    public static int text2(Context c) { return c(c, R.color.text2); }
    public static int text3(Context c) { return c(c, R.color.text3); }
    public static int accent(Context c) { return c(c, R.color.accent); }
    public static int ok(Context c) { return c(c, R.color.ok); }
    public static int warn(Context c) { return c(c, R.color.warn); }
    public static int danger(Context c) { return c(c, R.color.danger); }

    /**
     * 进度条 / 数据条的默认色：
     * 文档型 kit（无卡）用墨色，产品型 kit 用签名色；超过 90% 的紧张态由调用方换 danger。
     */
    public static int meterColor(Context c) {
        return "none".equals(kitCard(c)) ? text(c) : accent(c);
    }

    /** 语义色按 alpha 做底（chip / 选中的常见做法） */
    public static int tint(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(alpha * 255) << 24);
    }

    // ---------- 结构旋钮（每个 kit 覆盖 res/values/kit.xml） ----------

    public static float typeScale(Context c) {
        try {
            return Float.parseFloat(c.getString(R.string.kit_type_scale));
        } catch (Exception e) {
            return 1f;
        }
    }

    /** 卡片语言：none（无卡，靠发丝线分区）/ outline（1px 描边卡）/ fill（实色面板） */
    public static String kitCard(Context c) {
        try { return c.getString(R.string.kit_card); } catch (Exception e) { return "outline"; }
    }

    /**
     * 四套 kit 的结构身份。注意：这是"结构差异"，不是换色。
     * - none  + solid = 素纸（Writer 派：无卡、全宽发丝线、文字主导）
     * - outline + solid = 纸间（小眠/书摘派：白卡描边、胶囊按钮）
     * - fill  + solid = 格纸（木函/衣橱派：实色面板、方形按钮、紧凑密度）
     * - fill  + solid + 渐变底 = 暖光（钱迹派：整页渐变、白卡、深色按钮）
     */
    public static String kitDivider(Context c) {
        try { return c.getString(R.string.kit_divider); } catch (Exception e) { return "solid"; }
    }

    public static String kitButton(Context c) {
        try { return c.getString(R.string.kit_button); } catch (Exception e) { return "filled"; }
    }

    /** 密度：comfy（默认，行高 54）/ compact（行高 46，边距 20） */
    public static String kitDensity(Context c) {
        try { return c.getString(R.string.kit_density); } catch (Exception e) { return "comfy"; }
    }

    /** 展示字体：sans（默认）/ serif（阅读、笔记类） */
    public static String kitFont(Context c) {
        try { return c.getString(R.string.kit_font); } catch (Exception e) { return "sans"; }
    }

    public static float cardRadius(Context c) {
        return c.getResources().getDimension(R.dimen.kit_radius_card)
                / c.getResources().getDisplayMetrics().density;
    }

    /** 页面左右边距：comfy 24 / compact 20 */
    public static int pagePadding(Context c) {
        return dp(c, "compact".equals(kitDensity(c)) ? 20f : 24f);
    }

    // ---------- 尺寸 ----------
    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable stroke(Context c, int fill, int strokeColor, float radiusDp, float w) {
        GradientDrawable d = round(c, fill, radiusDp);
        d.setStroke(Math.max(1, dp(c, w)), strokeColor);
        return d;
    }

    // ---------- 文本 ----------
    public static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    /** 展示级字重：标题 / 大数字。只在 20sp 以上用，正文永远不用 bold。 */
    public static Typeface bold(Context c) {
        if ("serif".equals(kitFont(c))) {
            return Typeface.create("serif", Typeface.BOLD);
        }
        return Typeface.create("sans-serif", Typeface.BOLD);
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp * typeScale(c));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(c, 3), 1f);
        return t;
    }

    public static TextView medium(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(medium());
        return t;
    }

    /** 标题：700 字重 + 负字距（中文标题收紧一点才"现代"） */
    public static TextView title(Context c, String s) {
        TextView t = text(c, s, T_TITLE, text(c));
        t.setTypeface(bold(c));
        t.setLetterSpacing(-0.015f);
        return t;
    }

    /** 大数字：等宽数字 + 负字距，刷新时不左右跳 */
    public static TextView metric(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(bold(c));
        t.setFontFeatureSettings("'tnum'");
        t.setLetterSpacing(-0.02f);
        return t;
    }

    public static TextView note(Context c, String s) {
        return text(c, s, T_NOTE, text2(c));
    }

    public static TextView caption(Context c, String s) {
        return text(c, s, T_CAPTION, text3(c));
    }

    /** 小标签：11sp、大写、字距放开（"NOV 02" / "额度" 这类 kicker） */
    public static TextView kicker(Context c, String s) {
        TextView t = text(c, s, 11f, text3(c));
        t.setLetterSpacing(0.08f);
        t.setSingleLine(true);
        return t;
    }

    // ---------- 容器 ----------
    /** 一张卡：按 kit_card 决定无卡 / 描边 / 实色面板；无阴影。 */
    public static LinearLayout card(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        String mode = kitCard(c);
        if ("none".equals(mode)) {
            return v;
        }
        if ("fill".equals(mode)) {
            v.setBackground(round(c, cardColor(c), cardRadius(c)));
        } else {
            v.setBackground(stroke(c, cardColor(c), cardStroke(c), cardRadius(c), 1f));
        }
        int p = dp(c, "compact".equals(kitDensity(c)) ? 16f : 20f);
        v.setPadding(p, p, p, p);
        return v;
    }

    /** 行列表容器：卡语言同上，但内边距为 0（行自己带边距） */
    public static LinearLayout listCard(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        String mode = kitCard(c);
        if ("fill".equals(mode)) {
            v.setBackground(round(c, cardColor(c), cardRadius(c)));
        } else if ("outline".equals(mode)) {
            v.setBackground(stroke(c, cardColor(c), cardStroke(c), cardRadius(c), 1f));
        }
        v.setPadding(0, 0, 0, 0);
        return v;
    }

    /** 行间分隔线：按 kit 决定实线 / 点线 / 不画 */
    public static View divider(Context c) {
        String mode = kitDivider(c);
        if ("none".equals(mode)) {
            return new View(c);
        }
        if ("dotted".equals(mode)) {
            View v = new View(c);
            final int color = sep(c);
            v.setBackground(new Drawable() {
                @Override
                public void draw(android.graphics.Canvas canvas) {
                    android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    p.setColor(color);
                    p.setStyle(android.graphics.Paint.Style.STROKE);
                    p.setStrokeWidth(dp(c, 1));
                    p.setPathEffect(new android.graphics.DashPathEffect(
                            new float[]{dp(c, 2), dp(c, 3)}, 0));
                    canvas.drawLine(0, getBounds().height() / 2f,
                            getBounds().width(), getBounds().height() / 2f, p);
                }

                @Override public void setAlpha(int a) { }
                @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
                @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
            });
            v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
            return v;
        }
        return hairline(c);
    }

    /** 0.7dp 发丝线（不是 1px：3x 屏上 0.7dp≈2px，看起来才是一条线） */
    public static View hairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(sep(c));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 0.7f)));
        return v;
    }

    public static void gap(Context c, LinearLayout parent, float dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dp)));
        parent.addView(v);
    }

    public static View spacer(Context c, float weight) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, weight));
        return v;
    }

    /** 图标：22dp 描边矢量，颜色跟文字二级走 */
    public static ImageView icon(Context c, int res, int color, float sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** 图标按钮：44dp 触摸区，中间 21dp 图标 */
    public static LinearLayout iconButton(Context c, int res, int color, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER);
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        box.addView(icon(c, res, color, 21f));
        if (l != null) {
            ripple(box);
            box.setOnClickListener(l);
        }
        return box;
    }

    /** 水波纹：无界（图标按钮用），颜色按当前强调色 12% */
    public static void ripple(View v) {
        if (android.os.Build.VERSION.SDK_INT < 21) return;
        Context c = v.getContext();
        v.setBackground(new RippleDrawable(
                ColorStateList.valueOf(tint(accentFill(c), 0.10f)), v.getBackground(), null));
    }

    public static boolean isNight(Context c) {
        int mode = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** 进度条：双层 weight 实现，setBar 可原地更新不闪 */
    public static LinearLayout bar(Context c, double percent, int color) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(round(c, track(c), 1.5f));
        float p = (float) Math.max(0, Math.min(100, percent));
        View fill = new View(c);
        fill.setBackground(round(c, color, 1.5f));
        bar.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, p));
        View rest = new View(c);
        bar.addView(rest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - p));
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 3)));
        return bar;
    }

    /** 行列表容器（别名，语义更清楚） */
    public static LinearLayout list(Context c) {
        return listCard(c);
    }

    /** 一行：label 左 / 值右 / 可选 chevron。返回的 LinearLayout 里 tag="value" 是右侧 TextView */
    public static LinearLayout row(Context c, String label, String value, boolean chevron, View.OnClickListener l) {
        boolean compact = "compact".equals(kitDensity(c));
        boolean noCard = "none".equals(kitCard(c));
        int h = noCard ? 0 : dp(c, pagePadding(c) / 2f) + dp(c, 4f);
        int v = compact ? dp(c, 12) : dp(c, 16);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(h, v, h, v);
        row.addView(text(c, label, T_BODY, text(c)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = text(c, value == null ? "" : value, T_BODY, text2(c));
        val.setTag("value");
        row.addView(val);
        if (chevron) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 18), dp(c, 18));
            lp.leftMargin = dp(c, 6);
            row.addView(icon(c, R.drawable.lc_chevron, text3(c), 18f), lp);
        }
        if (l != null) {
            ripple(row);
            row.setOnClickListener(l);
        }
        return row;
    }

    /** 行与行之间的发丝线：从文字起点开始缩进；无卡 kit 全宽 */
    public static View rowHairline(Context c) {
        if ("none".equals(kitCard(c))) {
            return divider(c);
        }
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(c, pagePadding(c) / 2f) + dp(c, 4f), 0, 0, 0);
        wrap.addView(divider(c));
        return wrap;
    }

    /** 两列分项：赠送余额 / 充值余额 这种 */
    public static LinearLayout statRow(Context c, String l1, String v1, String l2, String v2) {
        LinearLayout cols = new LinearLayout(c);
        cols.addView(stat(c, l1, v1), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cols.addView(stat(c, l2, v2), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return cols;
    }

    private static LinearLayout stat(Context c, String label, String value) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(c, label, T_NOTE, text2(c)));
        gap(c, box, 4);
        box.addView(text(c, value, T_BODY, text(c)));
        return box;
    }

    /** 区块标题：11sp 大写字距标签 + 上下留白（不抢戏，只做分区） */
    public static TextView section(Context c, String s) {
        TextView t = kicker(c, s);
        int h = "none".equals(kitCard(c)) ? 0 : dp(c, pagePadding(c) / 2f) + dp(c, 4f);
        t.setPadding(h, dp(c, 24), h, dp(c, 10));
        return t;
    }

    /** 大数字 + 小单位（单位降一号、灰一档，且不破坏基线） */
    public static TextView metric(Context c, String value, String unit) {
        TextView t = metric(c, value, T_METRIC, text(c));
        if (unit != null && !unit.isEmpty()) {
            SpannableStringBuilder sb = new SpannableStringBuilder(value + " " + unit);
            int start = value.length() + 1;
            sb.setSpan(new RelativeSizeSpan(0.4f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(text2(c)), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.setText(sb);
        }
        return t;
    }

    /** 主按钮：按 kit_button 决定胶囊 / 方角 / 圆角；填充色永远是 accentFill（墨色，不是品牌色）。 */
    public static TextView pill(Context c, String text, boolean filled) {
        boolean compact = "compact".equals(kitDensity(c));
        String shape = kitButton(c);
        float r = "pill".equals(shape) ? R_PILL : ("rect".equals(shape) ? 12f : R_SMALL);
        int padV = compact ? dp(c, 11) : dp(c, 13);
        if (!filled) {
            // 次按钮：墨色描边 + 墨色字（不抢主按钮）
            TextView t = medium(c, text, T_BODY, text(c));
            t.setGravity(Gravity.CENTER);
            t.setBackground(stroke(c, 0x00000000, text(c), r, 1.2f));
            t.setPadding(dp(c, 22), padV, dp(c, 22), padV);
            ripple(t);
            return t;
        }
        TextView t = medium(c, text, T_BODY, onAccent(c));
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(c, accentFill(c), r));
        t.setPadding(dp(c, 22), padV, dp(c, 22), padV);
        ripple(t);
        return t;
    }

    /** 状态胶囊：12% 透明底 + 同色字 */
    public static TextView chip(Context c, String text, int color) {
        TextView t = medium(c, text, T_CHIP, color);
        t.setBackground(round(c, tint(color, 0.12f), R_SMALL));
        t.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        return t;
    }

    /** 一排胶囊（筛选器 / 快捷操作）：返回容器，调用方自己给每个设监听 */
    public static LinearLayout pillRow(Context c, String... labels) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(c, 8);
            row.addView(pill(c, labels[i], false), lp);
        }
        return row;
    }

    /** 横向间距 */
    public static void hgap(Context c, LinearLayout parent, float dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dp), 1));
        parent.addView(v);
    }

    /**
     * 迷你柱状图：7 根细柱，柱宽随容器均分、圆角 2dp。
     * 非活跃柱用 track，活跃柱（今天）用 accent —— 这是"强调色只做标记"的最小用法。
     * values 传 0-100 的相对高度即可（不必是真值，调用方自己归一化）。
     */
    public static LinearLayout sparkline(Context c, int[] values, int activeIndex) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 56)));
        int n = Math.max(1, values.length);
        for (int i = 0; i < n; i++) {
            int v = Math.max(0, Math.min(100, values[i]));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) clp.leftMargin = dp(c, 8);
            row.addView(col, clp);
            View space = new View(c);
            col.addView(space, new LinearLayout.LayoutParams(1, 0, (100 - v) / 100f));
            View bar = new View(c);
            bar.setBackground(round(c, i == activeIndex ? accent(c) : track(c), 2f));
            col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, v / 100f));
        }
        return row;
    }

    /** 已经 addView 过的进度条，原地更新（不要每次重建，否则会闪） */
    public static void setBar(LinearLayout bar, double percent, int color) {
        if (bar.getChildCount() < 2) return;
        float p = (float) Math.max(0, Math.min(100, percent));
        View fill = bar.getChildAt(0);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) fill.getLayoutParams();
        lp.weight = p;
        fill.setLayoutParams(lp);
        View rest = bar.getChildAt(1);
        LinearLayout.LayoutParams lp2 = (LinearLayout.LayoutParams) rest.getLayoutParams();
        lp2.weight = 100 - p;
        rest.setLayoutParams(lp2);
        Context c = bar.getContext();
        fill.setBackground(round(c, color, 1.5f));
    }

    /** 输入框：15sp、圆角 12、1px 描边、12dp 内边距。多行传 true */
    public static EditText input(Context c, String hint, boolean multiline) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(T_BODY);
        e.setTextColor(text(c));
        e.setHintTextColor(text3(c));
        e.setBackground(stroke(c, c(c, R.color.card), cardStroke(c), 12f, 1f));
        int p = dp(c, 14);
        e.setPadding(p, p, p, p);
        e.setSingleLine(!multiline);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        e.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    /** 搜索行：listCard 的第一行，放大镜 + 无边框输入 */
    public static LinearLayout searchRow(Context c, String hint) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = dp(c, pagePadding(c) / 2f) + dp(c, 4f), v = dp(c, 12);
        row.setPadding(h, v, h, v);
        row.addView(icon(c, R.drawable.lc_search, text3(c), 18f));
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(T_BODY);
        e.setTextColor(text(c));
        e.setHintTextColor(text3(c));
        e.setBackground(null);
        e.setSingleLine(true);
        e.setTag("input");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(c, 10);
        row.addView(e, lp);
        return row;
    }

    /** 开关行：label 左 / Switch 右。Switch 用 accent 上色 */
    public static LinearLayout switchRow(Context c, String label, boolean on,
                                         android.widget.CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int h = dp(c, pagePadding(c) / 2f) + dp(c, 4f), v = dp(c, 10);
        row.setPadding(h, v, h, v);
        row.addView(text(c, label, T_BODY, text(c)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(c);
        sw.setChecked(on);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{0xFFFFFFFF, 0xFFFFFFFF}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{accentFill(c), sep(c)}));
        if (l != null) sw.setOnCheckedChangeListener(l);
        sw.setTag("switch");
        row.addView(sw);
        return row;
    }

    // ---------- 沉浸式（edge-to-edge）----------

    /**
     * 在 onCreate() 里、setContentView() **之前**调。
     * **只做窗口级**；绝不在这里碰 WindowInsetsController（DecorView 还没建，直接 FATAL NPE）。
     */
    public static void setupWindow(Activity a) {
        int sdk = android.os.Build.VERSION.SDK_INT;
        Window w = a.getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        if (sdk < 35) {
            w.setStatusBarColor(0x00000000);
            w.setNavigationBarColor(sdk >= 29 ? 0x00000000 : 0x40000000);
        }
        if (sdk >= 29) w.setNavigationBarContrastEnforced(false);

        if (sdk >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            w.setAttributes(lp);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            if (sdk >= 28) {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(lp);
            }
        }
    }

    /** 系统栏图标明暗：setContentView() **之后**调（decor 已建），幂等、可每次切页调。 */
    @SuppressWarnings("deprecation")
    public static void setDarkIcons(Activity a, boolean darkIcons) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = a.getWindow().getInsetsController();
            int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if (c != null) c.setSystemBarsAppearance(darkIcons ? mask : 0, mask);
        } else {
            View d = a.getWindow().getDecorView();
            int vis = d.getSystemUiVisibility();
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | (android.os.Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
            d.setSystemUiVisibility(darkIcons ? (vis | flags) : (vis & ~flags));
        }
    }

    /** 按页面底色自动定图标明暗（浅底→深图标）。切页、切主题时调。 */
    public static void systemBars(Activity a, int pageBg) {
        setDarkIcons(a, isLightColor(pageBg));
    }

    /** 相对亮度（WCAG），用于决定状态栏图标用深色还是浅色 */
    public static boolean isLightColor(int c) {
        double l = 0.2126 * lin((c >> 16) & 0xFF) + 0.7152 * lin((c >> 8) & 0xFF)
                + 0.0722 * lin(c & 0xFF);
        return l > 0.35;
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    @SuppressWarnings("deprecation")
    public static int insetTop(android.view.WindowInsets in) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            return in.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout()).top;
        }
        return in.getSystemWindowInsetTop();
    }

    /** 底部 inset：只兜"真 0"；先问 tappableElement（三键导航），再退回系统资源 */
    @SuppressWarnings("deprecation")
    public static int insetBottom(View v, android.view.WindowInsets in) {
        int b;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            b = in.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout()).bottom;
            if (b <= 0) b = in.getInsets(android.view.WindowInsets.Type.tappableElement()).bottom;
        } else {
            b = in.getSystemWindowInsetBottom();
        }
        if (b <= 0) {
            int id = v.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (id > 0) b = v.getResources().getDimensionPixelSize(id);
        }
        return b;
    }

    /**
     * 简单场景（整页滚动）用这个：edges 1=上 2=下 4=左右，加在"画底色的那层"上。
     * 底部有固定栏时别用它——栏的高度要 += inset（见 design-system §13）。
     */
    public static void padToBars(final View v, final int edges) {
        final int l0 = v.getPaddingLeft(), t0 = v.getPaddingTop();
        final int r0 = v.getPaddingRight(), b0 = v.getPaddingBottom();
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets in) {
                int l, t, r, b;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets i = in.getInsets(
                            android.view.WindowInsets.Type.systemBars()
                                    | android.view.WindowInsets.Type.displayCutout());
                    l = i.left; t = i.top; r = i.right; b = i.bottom;
                } else {
                    l = in.getSystemWindowInsetLeft();
                    t = in.getSystemWindowInsetTop();
                    r = in.getSystemWindowInsetRight();
                    b = in.getSystemWindowInsetBottom();
                }
                view.setPadding(l0 + ((edges & 4) != 0 ? l : 0),
                        t0 + ((edges & 1) != 0 ? t : 0),
                        r0 + ((edges & 4) != 0 ? r : 0),
                        b0 + ((edges & 2) != 0 ? b : 0));
                return in;
            }
        });
        v.requestApplyInsets();
    }
}
