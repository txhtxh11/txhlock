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
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 全应用统一的视觉工具类（v2.0 卡片风格）。
 *
 * 设计约束：
 *  - 只用两层卡片（背景 + 卡片），一个强调色；
 *  - 所有间距、圆角、字号从这里取，避免每个页面各写一套；
 *  - 遥控器焦点：卡片聚焦时放大 3% + 蓝色描边，远距离一眼能看清。
 *
 * 电视适配：Android 电视的 dp 宽度差别很大（1080p 常见 960dp，4K 电视有时仍是
 * 1920dp），如果按固定 dp 写死，在 4K 电视上界面会缩成一小块。{@link #wrap(Context)}
 * 把"设计宽度"统一成 960dp：屏幕更宽就等比放大 density，所有卡片、字号同步变大。
 */
public final class Ui {

    /** 设计稿宽度（dp）。所有布局按这个宽度来排。 */
    private static final float DESIGN_WIDTH_DP = 960f;

    private Ui() {
    }

    /**
     * 按屏幕宽度把 density 归一到"960dp 设计宽度"。
     * 4K 电视（3840px / 320dpi）会被放大到 density=4，界面不会缩成一小块；
     * 手机/竖屏（宽度小于 960dp）保持原样，不受影响。
     */
    public static Context wrap(Context base) {
        try {
            Resources res = base.getResources();
            DisplayMetrics dm = res.getDisplayMetrics();
            int px = Math.max(dm.widthPixels, dm.heightPixels);
            if (px <= 0 || dm.density <= 0) return base;
            float target = px / DESIGN_WIDTH_DP;
            // 只在"屏幕比设计稿更宽"时放大；不缩小，避免小屏手机界面被压扁
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

    /** 卡片标题：21sp 粗体。 */
    public static TextView cardTitle(Context c, CharSequence s) {
        return text(c, s, 21, color(c, R.color.text_main), true);
    }

    /** 卡片说明：14sp 次要色。 */
    public static TextView cardSub(Context c, CharSequence s) {
        return text(c, s, 14, color(c, R.color.text_dim), false);
    }

    /** 弹层里的分组小标题。 */
    public static TextView sectionLabel(Context c, CharSequence s) {
        TextView t = text(c, s, 13, color(c, R.color.text_dim), false);
        t.setPadding(0, dp(c, 14), 0, dp(c, 6));
        return t;
    }

    public static TextView hint(Context c, CharSequence s) {
        TextView t = text(c, s, 13, color(c, R.color.text_dim), false);
        t.setLineSpacing(dp(c, 4), 1f);
        t.setPadding(0, dp(c, 6), 0, dp(c, 6));
        return t;
    }

    /** 数字/数值：大号粗体。 */
    public static TextView value(Context c, CharSequence s) {
        return text(c, s, 24, color(c, R.color.text_main), true);
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

    /** 一条 1dp 分隔线。 */
    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(color(c, R.color.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 4);
        v.setLayoutParams(lp);
        return v;
    }

    // ---------------- 交互 ----------------

    /**
     * 给可聚焦控件统一加"聚焦放大"动画。
     * 焦点描边由各自的 selector 背景负责，这里只管缩放，避免每个页面写一遍。
     */
    public static void focusScale(final View v) {
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean hasFocus) {
                float to = hasFocus ? 1.035f : 1f;
                view.animate()
                        .scaleX(to).scaleY(to)
                        .setDuration(hasFocus ? 150 : 110)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        });
    }

    /** 标准按钮：primary=true 实心蓝底，false 描边。 */
    public static TextView button(Context c, CharSequence label, boolean primary, float heightDp) {
        TextView t = text(c, label, 18, primary ? 0xFFFFFFFF : color(c, R.color.text_main), primary);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(primary ? R.drawable.bg_primary : R.drawable.bg_ghost);
        t.setClickable(true);
        t.setFocusable(true);
        focusScale(t);
        int h = dp(c, heightDp);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, h, 1f));
        return t;
    }

    /** 小号描边按钮（用于弹层里的 +/-）。 */
    public static TextView smallButton(Context c, CharSequence label) {
        TextView t = text(c, label, 24, color(c, R.color.text_main), true);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_ghost);
        t.setClickable(true);
        t.setFocusable(true);
        focusScale(t);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 64), dp(c, 56)));
        return t;
    }

    /** 题型/选项小片：on=true 时蓝底、文字变亮。 */
    public static TextView chip(Context c, CharSequence label, boolean on) {
        TextView t = text(c, label, 16, on ? color(c, R.color.text_main) : color(c, R.color.text_dim), on);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        t.setClickable(true);
        t.setFocusable(true);
        focusScale(t);
        t.setMinHeight(dp(c, 52));
        return t;
    }

    /** 把卡片里的进度条按比例展开（fill 的父容器里有一个 weight=1 的填充 View）。 */
    public static void setProgress(View fill, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        View parent = (View) fill.getParent();
        if (parent == null) return;
        int total = 1000;
        int w = Math.round(total * r);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) fill.getLayoutParams();
        if (w <= 0) {
            lp.width = 0;
            lp.weight = 0f;
        } else {
            lp.width = 0;
            lp.weight = w;
            // 剩下的空白用父容器的 weightSum 反推
        }
        fill.setLayoutParams(lp);
        if (parent instanceof LinearLayout) {
            ((LinearLayout) parent).setWeightSum(total);
        }
    }

    // ---------------- 对话框 ----------------

    /**
     * 统一风格的弹窗：无系统标题栏、卡片背景、居中。
     * 4.3 电视上系统默认对话框是灰紫 Holo 配色，和卡片界面完全不搭，所以全部自绘。
     */
    public static Dialog dialog(Activity a, View content, int widthDp) {
        return dialog(a, content, widthDp, 0);
    }

    /**
     * 同 {@link #dialog(Activity, View, int)}，但可以限制最大高度（内容多时由调用方
     * 自己套 ScrollView，这里只负责别让弹窗超出屏幕）。
     */
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
            w.setDimAmount(0.7f);
        }
        d.setCanceledOnTouchOutside(true);
        return d;
    }

    /** 弹窗内容外框：卡片背景 + 内边距。 */
    public static LinearLayout dialogPanel(Context c) {
        LinearLayout l = column(c);
        l.setBackgroundResource(R.drawable.bg_sheet);
        int p = dp(c, 30);
        l.setPadding(p, dp(c, 24), p, dp(c, 24));
        return l;
    }
}
