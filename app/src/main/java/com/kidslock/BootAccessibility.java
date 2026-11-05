package com.kidslock;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍保活 / 开机自启钩子。
 *
 * 为什么需要它：小米电视（MIUI TV）默认不把 BOOT_COMPLETED 发给第三方应用，
 * 开机广播这条路在这类 ROM 上不可靠。但"已启用的无障碍服务"是由 system_server
 * 在开机时主动绑定启动的，不经过第三方自启动白名单，因此可以当作可靠的开机入口。
 *
 * 额外收益：别的应用（小米桌面、视频 App）抢到前台时，立即把锁屏拉回来，
 * 比"3 秒轮询"更即时，主页键逃逸基本来不及看到桌面。
 *
 * 需要家长在系统"设置 → 无障碍"里手动开启一次（只是一次开关，不需要 ADB）。
 */
public class BootAccessibility extends AccessibilityService {

    private static final String TAG = "txhlock";

    /** 缓存 Prefs，onKeyEvent 会被频繁调用，不能每次都新建。 */
    private Prefs prefs;

    private Prefs p() {
        if (prefs == null) prefs = new Prefs(this);
        return prefs;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility connected");
        // 双保险：XML 里已声明 flagRequestFilterKeyEvents，这里再用代码设一次，
        // 以防个别 ROM 不读 XML 的 accessibilityFlags。
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo
                        .FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
                Log.i(TAG, "serviceInfo flags=0x" + Integer.toHexString(info.flags)
                        + " filterKey=" + ((info.flags & android.accessibilityservice
                        .AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0));
            } else {
                Log.i(TAG, "serviceInfo null");
            }
        } catch (Exception e) {
            Log.i(TAG, "setServiceInfo failed: " + e);
        }
        Prefs prefs = p();
        if (!prefs.isEnabled()) return;
        // 先做离开接续判定（小米电视上这条路径常常比 BOOT_COMPLETED 更早到）：
        // 短关机/重启 → 计时续上、锁屏状态保留；长关机/跨夜 → 清零解锁、从零计时。
        int away = Away.resolve(prefs);
        Log.i(TAG, "away resolve=" + away + " locked=" + prefs.isLocked());
        // 开机策略：小米电视常常不派发 BOOT_COMPLETED，无障碍服务反而是最早被系统绑定的，
        // 所以这里也做一次。用"开机 3 分钟内"来判断是开机绑定还是家长手动打开，
        // 家长手动开无障碍时不会突然弹锁屏。
        boolean bootish = android.os.SystemClock.elapsedRealtime() < 3L * 60L * 1000L;
        Keeper.lockOnBoot(this, prefs, bootish);
        Keeper.startService(this);
        Keeper.scheduleWatchdog(this);
        if (prefs.isLocked()) {
            // 开机后系统绑定本服务的那一刻，正好把锁屏顶上去
            new android.os.Handler(getMainLooper()).postDelayed(
                    () -> Keeper.launchLockNow(this), 1500);
        }
    }

    /**
     * 按键拦截：锁定状态下把遥控器【主页键】直接吞掉。
     *
     * 效果：按主页键 → 小米桌面根本不会被启动 → 孩子看不到任何桌面画面，
     * 也就没有"回到桌面再被 1~2 秒拉回来"的空档。
     * 未锁定时返回 false，主页键照常工作，不影响家长正常看电视。
     */
    @Override public boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        Prefs pr = p();
        if (!pr.isEnabled() || !pr.isLocked()) return false;   // 没在锁屏 → 放行

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            Log.i(TAG, "HOME key swallowed");
            // 记下"真的拦住过一次"，QuizService 才敢把抢回间隔从 0.8 秒放宽到 3 秒
            pr.setKeyFilterSeen(true);
            // 万一此刻锁屏不在前台（例如刚到桌面），顺手拉回来
            Keeper.launchLockNow(this);
        }
        return true;   // 吞掉 DOWN/UP，系统不会把 HOME 派发给桌面
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        Prefs pr = p();
        if (!pr.isEnabled() || !pr.isLocked()) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        // 别的应用抢到前台 → 立刻把锁屏顶回来
        if (!getPackageName().contentEquals(pkg)) {
            Log.i(TAG, "relock, foreground=" + pkg);
            Keeper.launchLockNow(this);
        }
    }

    @Override public void onInterrupt() {
    }

    @Override public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }
}
