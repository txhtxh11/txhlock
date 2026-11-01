package com.kidslock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * AlarmManager 派发的补拉 / 看门狗广播。
 * - ACTION_RETRY   ：开机后定时补拉锁屏页（对付"被系统桌面盖住"）
 * - ACTION_WATCHDOG：常驻巡检（对付"进程/服务被 ROM 杀掉"）
 */
public class KeeperReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Prefs prefs = new Prefs(context);

        // 监控已停用：什么都不做（家长关掉后不该再弹锁屏）
        if (!prefs.isEnabled()) return;

        // 保活计时服务
        Keeper.startService(context);

        // 只有处于锁定状态才拉起锁屏，避免打断正常观看
        if (prefs.isLocked()) {
            Keeper.launchLock(context);
        }

        // 看门狗自续期（部分 ROM 的 setRepeating 只执行一次）
        if (Keeper.ACTION_WATCHDOG.equals(intent.getAction())) {
            Keeper.scheduleWatchdog(context);
        }
    }
}
