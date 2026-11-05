package com.kidslock;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

/**
 * 应用内置数字键盘（v2.0 卡片风格）。
 *
 * 为什么不用系统输入法：电视遥控器没有数字键，系统输入法在 TV 上又难用；
 * 这里用方向键 + OK 选择，遥控器 0-9 数字键也能直接输入。
 *
 * 布局：
 *   1  2  3
 *   4  5  6
 *   7  8  9
 *  删除 0 确认
 *   取消（可隐藏）
 *
 * 焦点由 PinPad 自己管理（按键本身不聚焦），保证方向键始终在网格内移动，
 * 不会跳到格子外面去；选中键高亮成实心蓝，远距离也看得清。
 */
public class PinPad extends LinearLayout {

    public interface Listener {
        /** 用户提交一段输入（自动到 maxLen，或按了确认键）。 */
        void onEntered(String pin);

        /** 用户按了取消（取消行或返回键）。 */
        void onCancelled();
    }

    private static final int KEY_DELETE = 100;
    private static final int KEY_OK = 101;
    private static final int KEY_CANCEL = 102;

    private static final int ROWS = 5;

    private final Listener listener;
    private final TextView dotsView;
    private final TextView statusView;
    private final TableLayout grid;
    private final TextView[][] keys = new TextView[ROWS][3];
    private final int[][] keyVal = new int[ROWS][3];

    private final StringBuilder buf = new StringBuilder();
    private int maxLen;
    private int selRow = 0, selCol = 0;
    private boolean cancelVisible = true;

    public PinPad(Context c, String title, int maxLen, Listener l) {
        super(c);
        this.maxLen = maxLen;
        this.listener = l;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);
        setFocusableInTouchMode(true);

        dotsView = new TextView(c);
        dotsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        dotsView.setTextColor(0xFF111111);
        dotsView.setGravity(Gravity.CENTER);
        dotsView.setIncludeFontPadding(false);
        dotsView.setBackgroundResource(R.drawable.bg_display);

        statusView = new TextView(c);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusView.setTextColor(0xFFDDDDDD);
        statusView.setGravity(Gravity.CENTER);
        statusView.setIncludeFontPadding(false);
        statusView.setMinHeight(Ui.dp(c, 20));
        statusView.setText(title == null ? "" : title);

        grid = buildGrid(c);

        addView(dotsView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 64)));
        addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = Ui.dp(c, 8);
        addView(grid, glp);

        setCancelVisible(true);
        refreshDots();
        highlight();
    }

    // ---------------- 对外接口 ----------------

    public void setTitle(String t) {
        statusView.setText(t == null ? "" : t);
        statusView.setTextColor(Ui.color(getContext(), R.color.text_dim));
    }

    /** 显示错误：红字 + 左右抖动，比只改一行小字更醒目。 */
    public void showError(String msg) {
        statusView.setText(msg);
        statusView.setTextColor(Ui.color(getContext(), R.color.danger));
        TranslateAnimation shake = new TranslateAnimation(0, Ui.dp(getContext(), 10), 0, 0);
        shake.setDuration(60);
        shake.setRepeatCount(3);
        shake.setRepeatMode(Animation.REVERSE);
        startAnimation(shake);
    }

    public void setMaxLen(int m) {
        this.maxLen = m;
        refreshDots();
    }

    public void setCancelVisible(boolean v) {
        cancelVisible = v;
        TableRow cancelRow = (TableRow) grid.getChildAt(ROWS - 1);
        if (cancelRow != null) cancelRow.setVisibility(v ? VISIBLE : GONE);
        if (!v && selRow > 3) {
            selRow = 3;
            highlight();
        }
    }

    public void reset() {
        buf.setLength(0);
        refreshDots();
        if (statusView.getCurrentTextColor() == Ui.color(getContext(), R.color.danger)) {
            setTitle("");
        }
    }

    /** 当前输入（用于一次性校验）。 */
    public String value() {
        return buf.toString();
    }

    // ---------------- 内部 ----------------

    private TableLayout buildGrid(Context c) {
        TableLayout t = new TableLayout(c);
        t.setStretchAllColumns(true);
        t.setShrinkAllColumns(true);

        int[][] rows = {
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9},
                {KEY_DELETE, 0, KEY_OK},
                {KEY_CANCEL, KEY_CANCEL, KEY_CANCEL}
        };
        for (int r = 0; r < rows.length; r++) {
            TableRow tr = new TableRow(c);
            for (int col = 0; col < 3; col++) {
                final int k = rows[r][col];
                TextView b = new TextView(c);
                b.setText(labelOf(k));
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, k == KEY_DELETE || k == KEY_OK ? 18 : 24);
                b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                b.setGravity(Gravity.CENTER);
                b.setIncludeFontPadding(false);
                TableRow.LayoutParams lp = new TableRow.LayoutParams(0, Ui.dp(c, 56), 1f);
                lp.setMargins(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
                b.setLayoutParams(lp);
                b.setClickable(true);
                b.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        onKey(k);
                    }
                });
                keys[r][col] = b;
                keyVal[r][col] = k;
                tr.addView(b);
            }
            if (r == rows.length - 1) {
                // 取消行：整行一个键，横跨三列
                TableRow.LayoutParams lp = new TableRow.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 46));
                lp.setMargins(Ui.dp(c, 5), Ui.dp(c, 3), Ui.dp(c, 5), Ui.dp(c, 5));
                TextView only = (TextView) tr.getChildAt(0);
                only.setLayoutParams(lp);
                tr.removeViewAt(2);
                tr.removeViewAt(1);
            }
            t.addView(tr);
        }
        return t;
    }

    private static String labelOf(int k) {
        if (k == KEY_DELETE) return "删除";
        if (k == KEY_OK) return "确认";
        if (k == KEY_CANCEL) return "取消";
        return String.valueOf(k);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(new Runnable() {
            @Override public void run() {
                requestFocus();
                highlight();
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int maxRow = cancelVisible ? 4 : 3;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (selRow > 0) { selRow--; highlight(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (selRow < maxRow) { selRow++; highlight(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (selRow == 4) return true;
                if (selCol > 0) { selCol--; highlight(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (selRow == 4) return true;
                if (selCol < 2) { selCol++; highlight(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                onKey(keyVal[selRow][selCol]);
                return true;
            case KeyEvent.KEYCODE_DEL:
                onKey(KEY_DELETE);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (listener != null) listener.onCancelled();
                return true;
            default:
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    onKey(keyCode - KeyEvent.KEYCODE_0);
                    return true;
                }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void highlight() {
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < 3; col++) {
                TextView b = keys[r][col];
                if (b == null) continue;
                boolean sel = (r == selRow && col == selCol);
                b.setBackgroundResource(sel ? R.drawable.bg_key_selected : R.drawable.bg_key_normal);
                b.setTextColor(sel ? 0xFF0B1220 : Ui.color(getContext(), R.color.text_main));
            }
        }
    }

    private void onKey(int k) {
        if (k == KEY_CANCEL) {
            if (listener != null) listener.onCancelled();
            return;
        }
        if (k == KEY_DELETE) {
            if (buf.length() > 0) {
                buf.deleteCharAt(buf.length() - 1);
                refreshDots();
            }
            return;
        }
        if (k == KEY_OK) {
            submit();
            return;
        }
        int cap = maxLen > 0 ? maxLen : 12;
        if (buf.length() >= cap) return;
        buf.append((char) ('0' + k));
        refreshDots();
        if (maxLen > 0 && buf.length() >= maxLen) {
            submit();
        }
    }

    private void submit() {
        if (buf.length() == 0 || listener == null) return;
        String s = buf.toString();
        buf.setLength(0);
        refreshDots();
        listener.onEntered(s);
    }

    private void refreshDots() {
        int shown = maxLen > 0 ? maxLen : Math.max(buf.length(), 4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            sb.append(i < buf.length() ? '●' : '○');
            if (i != shown - 1) sb.append(' ');
        }
        dotsView.setText(sb.toString());
        if (maxLen == 0) {
            setTitle("输入完成后按【确认】键");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && !hasFocus()) requestFocus();
    }
}
