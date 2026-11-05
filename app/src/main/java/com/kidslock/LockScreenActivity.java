package com.kidslock;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/**
 * 全屏答题锁屏（v3.0 薄宿主）。
 *
 * 真正的出题/判题/解锁逻辑在 {@link QuizPanel} 里，和系统悬浮层锁屏共用。
 * 这个 Activity 只作为「没有悬浮窗权限」或「悬浮层被系统拒绝」时的兜底。
 */
public class LockScreenActivity extends Activity implements QuizPanel.Host {

    /** 锁屏页是否在前台。QuizService / Keeper 用它避免重复拉起打断答题。 */
    public static volatile boolean sForeground = false;

    private Prefs prefs;
    private QuizPanel panel;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrap(base));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        // 悬浮层已经在盖着了，不要再叠一个 Activity
        if (LockOverlay.isShowing()) {
            finish();
            return;
        }
        // 显示之前先按"离开规则"判定一次（待机唤醒/开机补拉可能更早到）
        Away.resolve(prefs);
        if (!prefs.isLocked()) {
            finish();
            return;
        }
        prefs.setLocked(true);

        setupFullscreen();
        panel = new QuizPanel(this);
        setContentView(panel.createView());
        panel.start();
    }

    @Override public Context context() {
        return this;
    }

    @Override public void onUnlocked(boolean viaParent) {
        sForeground = false;
        finish();
        // 锁屏页是独立任务栈，去掉退出动画，避免老电视上"卡一下"才回到原界面
        overridePendingTransition(0, 0);
    }

    @Override public void onClosed() {
        sForeground = false;
        finish();
    }

    private void setupFullscreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        Away.resolve(prefs);
        if (!prefs.isLocked()) {
            finish();
            return;
        }
        sForeground = true;
    }

    @Override protected void onPause() {
        sForeground = false;
        super.onPause();
    }

    @Override protected void onStop() {
        super.onStop();
        // 被 HOME 切走且当前没有悬浮层时，尽快把锁屏拉回来
        if (panel != null && !panel.isUnlocking() && prefs.isLocked() && !sForeground) {
            Keeper.launchLock(this);
        }
    }

    @Override protected void onDestroy() {
        if (panel != null) panel.stop();
        super.onDestroy();
    }

    /** 返回键在 KeyRoot 里已被吞掉，这里再兜一层。 */
    @Override public void onBackPressed() {
        // 锁定状态不允许退出
    }
}
