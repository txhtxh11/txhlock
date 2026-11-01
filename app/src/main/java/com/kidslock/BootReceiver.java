package com.kidslock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启（防重启绕过锁屏）。
 *
 * 关键点（旧版只有"启动服务 + 若处于锁定就拉起一次"，所以重启照样能绕过）：
 *   1. 开机即置为锁定状态 —— 重启不再等于"解锁"
 *   2. 立即拉起一次，再在 4/10/20/40/80 秒补拉 —— 开机器桌面会盖住第一枪
 *   3. 挂上常驻看门狗 —— 进程被 ROM 清掉也能把锁屏救回来
 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Prefs prefs = new Prefs(context);
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction());
        Log.i("txhlock", "BootReceiver action=" + intent.getAction()
                + " isBoot=" + isBoot + " enabled=" + prefs.isEnabled()
                + " lockOnBoot=" + prefs.lockOnBoot());

        if (!prefs.isEnabled()) return;      // 家长停用了监控，不做任何事

        if (!isBoot) return;

        // 1) 重启跟随重启前锁定状态（未锁定→不锁、计时续上；已锁定→仍锁）
        Keeper.lockOnBoot(context, prefs);

        // 2) 恢复计时服务
        Keeper.startService(context);

        // 3) 仅当处于锁定状态时才补拉锁屏（应对系统桌面抢占焦点）
        if (prefs.isLocked()) {
            Keeper.launchLock(context);
            Keeper.scheduleRetries(context);
        }

        // 4) 常驻看门狗（内部会判断锁定状态）
        Keeper.scheduleWatchdog(context);
    }
}
