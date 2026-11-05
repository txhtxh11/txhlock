package com.kidslock;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * 切换桌面入口：普通「答题锁屏」还是伪装「每日壁纸」。
 * 两个 activity-alias 同时只有一个启用，桌面只会看到一个图标。
 */
public final class LauncherAlias {

    private static final String NORMAL = "com.kidslock.LauncherNormal";
    private static final String WALL = "com.kidslock.LauncherWallpaper";

    public static void apply(Context c, boolean disguise) {
        if (c == null) return;
        try {
            PackageManager pm = c.getPackageManager();
            ComponentName normal = new ComponentName(c.getPackageName(), NORMAL);
            ComponentName wall = new ComponentName(c.getPackageName(), WALL);
            int on = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            int off = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            pm.setComponentEnabledSetting(wall, disguise ? on : off,
                    PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(normal, disguise ? off : on,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable ignored) {
        }
    }
}
