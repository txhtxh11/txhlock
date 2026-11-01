package com.kidslock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 开机自启：恢复计时服务，若处于锁定状态则直接拉起锁屏页（防重启绕过）。 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Prefs prefs = new Prefs(context);
        if (!prefs.isEnabled()) return;

        // 启动计时服务
        Intent svc = new Intent(context, QuizService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception ignored) {
        }

        // 若处于锁定，立即弹出锁屏页
        if (prefs.isLocked()) {
            try {
                Intent li = new Intent(context, LockScreenActivity.class);
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(li);
            } catch (Exception ignored) {
            }
        }
    }
}
