package com.kidslock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启（防重启绕过锁屏）。
 *
 * 顺序很重要：
 *   0. 先做"离开接续判定"（{@link Away#resolve}）—— 决定这次开机到底算不算"离开很久"。
 *   1. 再按开机策略处理：开了「开机先答题」就在真正开机时强制锁一次；
 *      关了就沿用重启前的状态。
 *   2. 启动计时服务；只有判定为"应锁定"时才拉起锁屏页并补拉。
 *   3. 挂常驻看门狗。
 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Prefs prefs = new Prefs(context);
        String action = intent == null ? "" : intent.getAction();
        boolean isRealBoot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
        boolean isPackageReplaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);

        if (!prefs.isEnabled()) return;      // 家长停用了监控，不做任何事
        if (!isRealBoot && !isPackageReplaced) return;

        // 0) 离开接续判定：短关机/重启 → 计时续上、锁屏状态保留；长关机/跨夜 → 清零解锁
        int away = Away.resolve(prefs);
        Log.i("txhlock", "BootReceiver action=" + action + " away=" + away
                + " locked=" + prefs.isLocked() + " lockOnBoot=" + prefs.lockOnBoot()
                + " accBound=" + KeyFilter.isBound(context)
                + " keyFilter=" + KeyFilter.hasKeyFilter(context));

        // 1) 开机策略：真正开机 + 家长开了「开机先答题」→ 强制锁一次
        Keeper.lockOnBoot(context, prefs, isRealBoot);

        // 2) 恢复计时服务
        Keeper.startService(context);

        // 3) 仅当判定仍处于锁定状态时才拉起锁屏并补拉（应对系统桌面抢占焦点）
        if (prefs.isLocked()) {
            Keeper.launchLock(context);
            Keeper.scheduleRetries(context);
        }

        // 4) 常驻看门狗（内部会判断锁定状态）
        Keeper.scheduleWatchdog(context);
    }
}
