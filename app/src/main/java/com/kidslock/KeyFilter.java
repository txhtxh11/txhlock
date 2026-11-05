package com.kidslock;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * 无障碍服务的「真实运行状态」检测。
 *
 * 为什么不能用 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES 判断：
 * 小米电视（MIUI TV）在覆盖安装后会保留这个字符串，却**不再绑定**服务。
 * 此时 settings 里查得到名字、应用却整天收不到 onKeyEvent，
 * 拿字符串判断会把"名字还在、人已经不在"误判为已开启 —— v1.8 之前就是这样漏掉的。
 *
 * AccessibilityManager.getEnabledAccessibilityServiceList() 读的是 system_server
 * 里真正绑定成功的服务列表，更可信。
 */
public final class KeyFilter {

    private KeyFilter() {}

    /** 本应用的无障碍服务当前是否真的被系统绑定（不看 settings 字符串）。 */
    public static boolean isBound(Context c) {
        return find(c) != null;
    }

    /**
     * 是否拿到了「按键过滤」能力。
     * 只有它为 true，遥控器主页键才会被吞掉（桌面完全不出现）；
     * 为 false 时只能靠轮询抢回，按主页会先看到 1~3 秒桌面。
     */
    public static boolean hasKeyFilter(Context c) {
        AccessibilityServiceInfo info = find(c);
        if (info == null) return false;
        return (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
    }

    /**
     * 主页键拦截是否「真的生效」：既声明了过滤能力，又确实收到并吞掉过一次 HOME。
     *
     * 小米电视上常见「flag 有、事件不来」：只信 flag 会把轮询间隔放宽到 3 秒，
     * 孩子按主页键就能在桌面上停留好几秒。这里要求真的拦截过一次，才敢放宽。
     */
    public static boolean isEffective(Context c) {
        if (!hasKeyFilter(c)) return false;
        try {
            return new Prefs(c).keyFilterSeen();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 找到本应用那条服务记录；没绑定返回 null。 */
    private static AccessibilityServiceInfo find(Context c) {
        if (c == null) return null;
        try {
            AccessibilityManager am = (AccessibilityManager)
                    c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) return null;
            List<AccessibilityServiceInfo> list =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (list == null) return null;
            String pkg = c.getPackageName();
            for (AccessibilityServiceInfo info : list) {
                if (info == null || info.getResolveInfo() == null
                        || info.getResolveInfo().serviceInfo == null) continue;
                if (pkg.equals(info.getResolveInfo().serviceInfo.packageName)) return info;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
