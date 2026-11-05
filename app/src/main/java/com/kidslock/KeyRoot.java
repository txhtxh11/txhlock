package com.kidslock;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * 锁屏根布局：在事件分发给子 View 之前先交给 QuizPanel。
 *
 * 这样无论锁屏是跑在 Activity 里还是系统悬浮层里，方向键、返回键、菜单键
 * 的处理都完全一致；悬浮层没有 Activity.onKeyDown 可用，只能靠这一层。
 */
public class KeyRoot extends FrameLayout {

    public interface KeyHandler {
        boolean onKeyDown(int keyCode, KeyEvent event);
    }

    private KeyHandler handler;
    private View focusTarget;

    public KeyRoot(Context context) {
        super(context);
    }

    public KeyRoot(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public KeyRoot(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setKeyHandler(KeyHandler h) {
        this.handler = h;
    }

    /** 窗口真正拿到焦点后，把焦点交给第一个答案，避免"先按一下方向键才出现高亮"。 */
    public void setFocusTarget(View v) {
        focusTarget = v;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && handler != null) {
            if (handler.onKeyDown(event.getKeyCode(), event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFocusableInTouchMode(true);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && focusTarget != null) {
            focusTarget.post(new Runnable() {
                @Override public void run() {
                    focusTarget.requestFocus();
                }
            });
        }
    }
}
