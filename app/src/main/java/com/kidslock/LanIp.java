package com.kidslock;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** 取电视在局域网里的 IPv4（手机要访问的地址）。 */
public final class LanIp {
    private LanIp() {}

    public static String get() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni == null || !ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName() == null ? "" : ni.getName().toLowerCase();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        // 电视同时插网线时，优先给 WiFi 地址（手机连的是 WiFi）
                        if (name.startsWith("wlan") || name.startsWith("ap")) return a.getHostAddress();
                        if (fallback == null) fallback = a.getHostAddress();
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return fallback;
    }
}
