package com.kidslock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启（防重启绕过锁屏）。
 *
 * 顺序很重要（v1.4 修正）：
 *   0. 先做"离开接续判定"（{@link Away#resolve}）—— 决定这次开机到底该不该锁。
 *      旧版把这一步留在服务启动时才做，而这里已经按"重启前的锁定标记"把锁屏页拉起来了，
 *      于是长关机（本该清零解锁）也会先闪一个锁屏页，甚至因为锁屏页 onCreate 里再置一次
 *      locked=true 而彻底锁回去。
 *   1. 再按开机策略处理（家长关了"开机自动锁定"才放开）。
 *   2. 启动计时服务；只有判定为"仍应锁定"时才拉起锁屏页并补拉。
 *   3. 挂常驻看门狗。
 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Prefs prefs = new Prefs(context);
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction());

        if (!prefs.isEnabled()) return;      // 家长停用了监控，不做任何事
        if (!isBoot) return;

        // 0) 离开接续判定：短关机/重启 → 计时续上、锁屏状态保留；长关机/跨夜 → 清零解锁
        int away = Away.resolve(prefs);
        Log.i("txhlock", "BootReceiver action=" + intent.getAction() + " away=" + away
                + " locked=" + prefs.isLocked() + " lockOnBoot=" + prefs.lockOnBoot()
                // v1.9：顺手记录无障碍的真实状态。
                // 覆盖安装后小米电视会把服务解绑（settings 里名字还在、却不绑定），
                // 这两项能立刻区分"真在跑"和"名字还在但人已经不在"。
                + " accBound=" + KeyFilter.isBound(context)
                + " keyFilter=" + KeyFilter.hasKeyFilter(context));

        // 1) 开机策略（家长主动关掉"开机自动锁定"时放开）
        Keeper.lockOnBoot(context, prefs);

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
