package com.kidslock;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * v3.0 设计系统工具类「TXH 控制台」。
 *
 * 视觉规则（与 UI 稿一致，所有页面只从这里取值）：
 *  - 零圆角、1.5dp 细线、无阴影无渐变；
 *  - 只有一个强调色：琥珀 #F0A437；聚焦/选中 = 整块填琥珀 + 深色字；
 *  - 标签和数字用等宽字体，像仪表盘；
 *  - 文字颜色用 color selector + duplicateParentState，方块聚焦时子文字自动变深色。
 *
 * 电视适配：按屏幕宽度把 density 归一到 960dp 设计稿，4K 电视整界面等比放大。
 */
public final class Ui {

    private static final float DESIGN_WIDTH_DP = 960f;

    private Ui() {
    }

    /** 按屏幕宽度把 density 归一到"960dp 设计宽度"（4K 电视自动放大，手机不缩小）。 */
    public static Context wrap(Context base) {
        try {
            Resources res = base.getResources();
            DisplayMetrics dm = res.getDisplayMetrics();
            int px = Math.max(dm.widthPixels, dm.heightPixels);
            if (px <= 0 || dm.density <= 0) return base;
            float target = px / DESIGN_WIDTH_DP;
            if (target <= dm.density * 1.02f) return base;
            Configuration cfg = new Configuration(res.getConfiguration());
            cfg.densityDpi = Math.round(target * 160f);
            return base.createConfigurationContext(cfg);
        } catch (Throwable t) {
            return base;
        }
    }

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int color(Context c, int id) {
        return c.getResources().getColor(id);
    }

    /** 等宽字体：数字、版本号、状态标签统一用它，是这套 UI 的识别点。 */
    public static Typeface mono() {
        return Typeface.MONOSPACE;
    }

    // ---------------- 文本 ----------------

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    /** 等宽文本（数字/标签）。 */
    public static TextView monoText(Context c, CharSequence s, float sp, int color) {
        TextView t = text(c, s, sp, color, false);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    /** 方块上的文字：跟随父容器 focused 状态自动变深色（需要父容器 focusable）。 */
    public static TextView blockText(Context c, CharSequence s, float sp, int colorRes, boolean bold) {
        TextView t = text(c, s, sp, color(c, colorRes), bold);
        t.setTextColor(c.getResources().getColorStateList(colorRes));
        t.setDuplicateParentStateEnabled(true);
        return t;
    }

    /** 等宽的方块文字。 */
    public static TextView blockMono(Context c, CharSequence s, float sp, int colorRes, boolean bold) {
        TextView t = blockText(c, s, sp, colorRes, bold);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    public static TextView sectionLabel(Context c, CharSequence s) {
        TextView t = monoText(c, s, 10.5f, color(c, R.color.text_dim));
        t.setPadding(0, dp(c, 10), 0, dp(c, 4));
        return t;
    }

    public static TextView hint(Context c, CharSequence s) {
        TextView t = text(c, s, 12.5f, color(c, R.color.text_dim), false);
        t.setLineSpacing(dp(c, 4), 1f);
        t.setPadding(0, dp(c, 8), 0, dp(c, 8));
        return t;
    }

    // ---------------- 容器 ----------------

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View spacer(Context c, float dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dp)));
        return v;
    }

    /** 1.5dp 细分隔线，控制台风格的基础元素。 */
    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(color(c, R.color.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1.5f)));
        lp.topMargin = dp(c, 8);
        lp.bottomMargin = dp(c, 2);
        v.setLayoutParams(lp);
        return v;
    }

    // ---------------- 交互 ----------------

    /**
     * 聚焦反馈统一交给"整块填琥珀"的 selector，这里保留方法签名，不再做放大，
     * 避免零圆角方块被缩放后边缘发虚。
     */
    public static void focusScale(final View v) {
        v.setFocusable(true);
    }

    /** 标准按钮：primary=true 琥珀实心 + 深色字。 */
    public static TextView button(Context c, CharSequence label, boolean primary, float heightDp) {
        TextView t;
        if (primary) {
            t = text(c, label, 16, color(c, R.color.on_accent), true);
        } else {
            t = blockText(c, label, 16, R.color.block_sub, false);
        }
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(primary ? R.drawable.bg_primary : R.drawable.bg_ghost);
        t.setClickable(true);
        t.setFocusable(true);
        int h = dp(c, heightDp);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, h, 1f));
        return t;
    }

    /** 小号方块按钮（弹层里的 +/-）。 */
    public static TextView smallButton(Context c, CharSequence label) {
        TextView t = blockMono(c, label, 22, R.color.block_text, true);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_ghost);
        t.setClickable(true);
        t.setFocusable(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 60), dp(c, 52)));
        return t;
    }

    /** 题型/选项小方块：on=true 时琥珀描边，聚焦时整块填琥珀。 */
    public static TextView chip(Context c, CharSequence label, boolean on) {
        TextView t = blockText(c, label, 15, on ? R.color.block_text : R.color.block_sub, on);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        t.setClickable(true);
        t.setFocusable(true);
        t.setMinHeight(dp(c, 50));
        return t;
    }

    /** 方块里的进度条：按比例设置填充权重。 */
    public static void setProgress(View fill, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        View parent = (View) fill.getParent();
        if (parent == null) return;
        int total = 1000;
        int w = Math.round(total * r);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) fill.getLayoutParams();
        lp.width = 0;
        lp.weight = w <= 0 ? 0f : w;
        fill.setLayoutParams(lp);
        if (parent instanceof LinearLayout) {
            ((LinearLayout) parent).setWeightSum(total);
        }
    }

    // ---------------- 对话框 ----------------

    public static Dialog dialog(Activity a, View content, int widthDp) {
        return dialog(a, content, widthDp, 0);
    }

    /** 统一弹窗：无系统标题栏、直角面板、居中。 */
    public static Dialog dialog(Activity a, View content, int widthDp, int maxHeightDp) {
        Dialog d = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(dp(a, widthDp),
                    maxHeightDp > 0 ? dp(a, maxHeightDp) : ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.78f);
        }
        d.setCanceledOnTouchOutside(true);
        return d;
    }

    /** 弹窗内容外框：面板背景 + 内边距。 */
    public static LinearLayout dialogPanel(Context c) {
        LinearLayout l = column(c);
        l.setBackgroundResource(R.drawable.bg_sheet);
        int p = dp(c, 30);
        l.setPadding(p, dp(c, 24), p, dp(c, 24));
        return l;
    }
}
