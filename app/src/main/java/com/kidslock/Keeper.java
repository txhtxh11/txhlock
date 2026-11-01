package com.kidslock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

/**
 * 统一"拉起锁屏"入口。
 *
 * 解决重启绕过问题时踩到的三个坑：
 *   1. 开机广播里 startActivity 会被随后启动的系统桌面覆盖 → 必须多次补拉（scheduleRetries）
 *   2. 应用进程/服务被 ROM 杀掉后再也不会锁 → 用 AlarmManager 常驻看门狗（scheduleWatchdog）
 *   3. 重启后如果状态是"未锁定"就完全不管 → 开机强制置为锁定（lockOnBoot）
 */
public final class Keeper {

    public static final String ACTION_RETRY = "com.kidslock.action.RETRY_LOCK";
    public static final String ACTION_WATCHDOG = "com.kidslock.action.WATCHDOG";

    private static final int REQ_RETRY = 1001;
    private static final int REQ_WATCHDOG = 1002;

    private Keeper() {}

    /** 启动计时服务（兼容 API 18 与 API 26+）。 */
    public static void startService(Context c) {
        try {
            Intent svc = new Intent(c, QuizService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(svc);
            } else {
                c.startService(svc);
            }
        } catch (Exception ignored) {
        }
    }

    /** 立即拉起锁屏页（1.5 秒内不重复拉起，避免多个看门狗叠加导致闪屏）。 */
    private static long sLastLaunch = 0L;
    private static long sLastQuick = 0L;

    /** 无障碍事件触发的即时拉起：几乎不节流，用于跟别的应用抢前台。 */
    public static void launchLockNow(Context c) {
        long now = SystemClock.elapsedRealtime();
        if (now - sLastQuick < 400L) return;
        sLastQuick = now;
        sLastLaunch = now;
        startLockActivity(c);
    }

    public static void launchLock(Context c) {
        long now = SystemClock.elapsedRealtime();
        if (now - sLastLaunch < 1500L) return;
        sLastLaunch = now;
        startLockActivity(c);
    }

    private static void startLockActivity(Context c) {
        try {
            Intent i = new Intent(c, LockScreenActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            c.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    /**
     * 开机后补拉若干次。
     * 原因：BOOT_COMPLETED 广播发出时系统桌面往往还没起来，
     * 我们拉起的锁屏页会被桌面盖住；隔几秒再拉一次才稳。
     */
    public static void scheduleRetries(Context c) {
        long[] delays = {4000L, 10000L, 20000L, 40000L, 80000L};
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < delays.length; i++) {
            PendingIntent pi = PendingIntent.getBroadcast(c, REQ_RETRY + i,
                    new Intent(c, KeeperReceiver.class).setAction(ACTION_RETRY),
                    PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + delays[i], pi);
            } catch (Exception ignored) {
            }
        }
    }

    /** 常驻看门狗：每 60 秒检查一次，服务被杀也能把锁屏救回来。 */
    public static void scheduleWatchdog(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, REQ_WATCHDOG,
                new Intent(c, KeeperReceiver.class).setAction(ACTION_WATCHDOG),
                PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 60000, 60000, pi);
        } catch (Exception ignored) {
        }
    }

    /**
     * 开机即锁：把"重启电视"变成"重新开始一局答题"。
     * 注意：不清 dailyLocked —— 否则孩子重启就能把"今日额度已用完"刷掉。
     */
    public static void lockOnBoot(Context c, Prefs prefs) {
        // 重启跟随重启前锁定状态：
        //  - "开机自动锁定"开（默认）：已锁定→重启仍锁；未锁定→重启不锁、计时续上（不重置起点）
        //  - "开机自动锁定"关：重启不锁屏（家长主动关闭重启锁屏）
        if (!prefs.lockOnBoot()) {
            prefs.setLocked(false);
            prefs.setSessionStartMs(0);
        }
    }
}
