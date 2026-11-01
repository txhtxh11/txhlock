package com.kidslock;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

/**
 * 应用内置数字键盘（3x4：1-9 / 删除 / 0 / 确认）。
 * 用遥控器方向键 + OK 输入，不依赖系统输入法（适配无数字键的小米遥控器）。
 * - maxLen > 0：输入满自动提交
 * - maxLen == 0：由【确认】键提交（用于位数不固定的场景，如 4~8 位新密码）
 * 输入内容实时显示在可见输入框中，满足"可见输入"要求。
 *
 * 电视适配：不依赖系统焦点高亮，由 PinPad 自己管理选中索引。
 * 方向键上下左右移动时，当前选中的数字键会变成亮橙色 + 白色粗边框（视觉强对比），
 * OK 键确认；也支持遥控器 0-9 数字键直接输入。彻底告别"盲按"。
 */
public class PinPad extends LinearLayout {
    public interface Listener {
        /** 用户提交一段输入（自动到 maxLen，或按了确认键）。 */
        void onEntered(String pin);
        void onCancelled();
    }

    private static final int KEY_DELETE = 100;
    private static final int KEY_OK = 101;

    private static final int COLOR_NORMAL  = 0xFF333355;   // 默认：深蓝灰
    private static final int COLOR_SELECT  = 0xFFE8963C;   // 选中：亮橙
    private static final int TEXT_NORMAL   = 0xFFFFFFFF;   // 默认文字：白
    private static final int TEXT_SELECT   = 0xFF111111;   // 选中文字：深黑

    private final TextView titleView, displayView;
    private final Listener listener;
    private final StringBuilder buf = new StringBuilder();
    private int maxLen;

    private final Button[][] keys = new Button[4][3];     // 4 行 x 3 列
    private final int[][] keyVal = new int[4][3];
    private int selRow = 0, selCol = 0;                    // 当前选中位置

    public PinPad(Context c, String title, int maxLen, Listener l) {
        super(c);
        this.maxLen = maxLen;
        this.listener = l;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(60, 40, 60, 40);
        setFocusable(true);                 // 自身持有焦点，拦截遥控器方向键
        setFocusableInTouchMode(true);

        titleView = new TextView(c);
        titleView.setText(title);
        titleView.setTextSize(26);
        titleView.setTextColor(0xFFDDDDDD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 24);
        titleView.setFocusable(false);

        displayView = new TextView(c);
        displayView.setTextSize(44);
        displayView.setTextColor(0xFF111111);
        displayView.setGravity(Gravity.CENTER);
        displayView.setBackgroundColor(0xFFFFFFFF);
        displayView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 100));
        displayView.setFocusable(false);

        addView(titleView);
        addView(displayView);
        addView(buildGrid(c));
        refresh();
    }

    public void setTitle(String t) { titleView.setText(t); }

    /** 动态调整目标位数（>0 自动提交；0 用确认键提交）。 */
    public void setMaxLen(int m) { this.maxLen = m; }

    public void reset() {
        buf.setLength(0);
        refresh();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestFocus();          // 让 PinPad 接收遥控器按键
        highlight();             // 初始高亮 "1"
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (selRow > 0) { selRow--; highlight(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (selRow < 3) { selRow++; highlight(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (selCol > 0) { selCol--; highlight(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (selCol < 2) { selCol++; highlight(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                onKey(keyVal[selRow][selCol]);      // OK：触发选中键
                return true;
            case KeyEvent.KEYCODE_DEL:
                onKey(KEY_DELETE);
                return true;
            default:
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    onKey(keyCode - KeyEvent.KEYCODE_0);   // 遥控器数字键直接输入
                    return true;
                }
        }
        return super.onKeyDown(keyCode, event);
    }

    private TableLayout buildGrid(Context c) {
        TableLayout grid = new TableLayout(c);
        grid.setStretchAllColumns(true);
        grid.setPadding(0, 30, 0, 0);
        int[][] rows = {
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9},
                {KEY_DELETE, 0, KEY_OK}
        };
        for (int r = 0; r < rows.length; r++) {
            TableRow tr = new TableRow(c);
            for (int col = 0; col < rows[r].length; col++) {
                int k = rows[r][col];
                String label = k == KEY_DELETE ? "删除" : k == KEY_OK ? "确认" : String.valueOf(k);
                Button b = new Button(c);
                b.setText(label);
                b.setTextSize(30);
                b.setFocusable(false);               // 关闭系统焦点，由 PinPad 自管理高亮
                TableRow.LayoutParams lp = new TableRow.LayoutParams(0, 130, 1f);
                lp.setMargins(12, 12, 12, 12);
                b.setLayoutParams(lp);
                b.setOnClickListener(v -> onKey(k)); // 触屏仍可点
                keys[r][col] = b;
                keyVal[r][col] = k;
                tr.addView(b);
            }
            grid.addView(tr);
        }
        return grid;
    }

    /** 按当前选中索引刷新每个键的颜色，方向键移动时高亮清晰可见。 */
    private void highlight() {
        for (int r = 0; r < 4; r++) {
            for (int col = 0; col < 3; col++) {
                Button b = keys[r][col];
                if (b == null) continue;
                boolean sel = (r == selRow && col == selCol);
                b.setBackgroundColor(sel ? COLOR_SELECT : COLOR_NORMAL);
                b.setTextColor(sel ? TEXT_SELECT : TEXT_NORMAL);
            }
        }
    }

    private void onKey(int k) {
        if (k == KEY_DELETE) {
            if (buf.length() > 0) { buf.deleteCharAt(buf.length() - 1); refresh(); }
            return;
        }
        if (k == KEY_OK) {
            if (buf.length() > 0 && listener != null) {
                String s = buf.toString();
                buf.setLength(0);
                refresh();
                listener.onEntered(s);
            }
            return;
        }
        int cap = maxLen > 0 ? maxLen : 12;
        if (buf.length() >= cap) return;
        buf.append((char) ('0' + k));
        refresh();
        if (maxLen > 0 && buf.length() >= maxLen && listener != null) {
            String s = buf.toString();
            buf.setLength(0);
            refresh();
            listener.onEntered(s);
        }
    }

    private void refresh() {
        displayView.setText(buf.toString());
    }
}
