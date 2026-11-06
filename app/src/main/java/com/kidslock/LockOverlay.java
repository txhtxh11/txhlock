package com.kidslock;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * 系统悬浮层锁屏（v3.0）。
 *
 * 为什么要有它：锁屏如果只是一个 Activity，孩子按遥控器主页键时，系统会先把桌面
 * 切到前台，Activity 再花 0.5~1 秒抢回来 —— 孩子能看到桌面闪一下。
 * 悬浮层（SYSTEM_ALERT_WINDOW / TYPE_APPLICATION_OVERLAY）盖在所有应用之上，
 * 桌面启动了也还在它下面，所以视觉上"主页键完全没反应"。
 *
 * 这是参考同类产品（如 StudyOnTV）的公开思路后独立实现的方案，没有使用其代码。
 * 没有悬浮窗权限时自动退回 Activity 锁屏，功能不受影响。
 */
public final class LockOverlay {

    private static final String TAG = "txhlock";

    private static LockOverlay sInstance;

    private final Context app;
    private final WindowManager wm;
    private final View view;
    private final QuizPanel panel;

    private LockOverlay(Context context) {
        this.app = context.getApplicationContext();
        this.wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        panel = new QuizPanel(new QuizPanel.Host() {
            @Override public void onUnlocked(boolean viaParent) {
                hide();
            }

            @Override public void onClosed() {
                hide();
            }

            @Override public Context context() {
                return app;
            }
        });
        view = panel.createView();
        if (view instanceof KeyRoot) {
            ((KeyRoot) view).setKeyHandler(new KeyRoot.KeyHandler() {
                @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
                    return panel.onKeyDown(keyCode, event);
                }
                @Override public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
                    return panel.onKeyUp(keyCode, event);
                }
            });
        }
        panel.start();
    }

    /** 有没有悬浮窗权限（4.3~5.1 安装即授予；6.0+ 需要家长授权一次）。 */
    public static boolean canShow(Context c) {
        if (c == null) return false;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                return Settings.canDrawOverlays(c);
            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }

    public static boolean isShowing() {
        return sInstance != null;
    }

    /** 显示悬浮锁屏；返回 false 表示没有权限或失败，调用方应退回 Activity。 */
    public static boolean show(Context c) {
        if (c == null || sInstance != null) return sInstance != null;
        if (!new Prefs(c).isLocked()) return false;
        if (!canShow(c)) return false;
        try {
            LockOverlay overlay = new LockOverlay(c);
            overlay.addToWindow();
            sInstance = overlay;
            Log.i(TAG, "lock overlay shown");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "lock overlay failed", t);
            return false;
        }
    }

    public static void hide() {
        LockOverlay o = sInstance;
        sInstance = null;
        if (o != null) o.removeFromWindow();
    }

    /** 锁屏状态还在、但悬浮层被系统收掉了：重新盖回来。 */
    public static void ensureShowing(Context c) {
        if (c == null) return;
        if (sInstance != null) return;
        if (!new Prefs(c).isLocked()) return;
        show(c);
    }

    private void addToWindow() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        // 不加 FLAG_NOT_FOCUSABLE：悬浮层要自己接收遥控器按键
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
        wm.addView(view, lp);
        view.setFocusableInTouchMode(true);
        view.requestFocus();
    }

    private void removeFromWindow() {
        panel.stop();
        try {
            wm.removeViewImmediate(view);
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "lock overlay hidden");
    }
}
