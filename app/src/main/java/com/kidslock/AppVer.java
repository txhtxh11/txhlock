package com.kidslock;

/**
 * 对外展示的版本号（家长设置页右上角）。
 *
 * 规则：v0.1 起步，每改一次 +0.1，涨到 v1.0 即为正式版。
 * 以后改版本只改下面这一个常量即可，界面上会自动跟着变。
 */
public final class AppVer {

    /** 当前展示版本，例如 "v0.1"。 */
    public static final String VERSION = "v1.9.1";

    /** 完整标签，例如 "TXH txhlock v0.1"。 */
    public static final String LABEL = "TXH txhlock " + VERSION;

    private AppVer() {
    }
}
