package com.kidslock;

/**
 * 对外展示的版本号（家长设置页右上角）。
 *
 * 规则：每发一版只改下面这两个常量，界面会自动跟着变。
 * v3.0：卡片式 UI 重构 + Android 4.3 全系电视兼容修复 + 一键安装。
 */
public final class AppVer {

    public static final String VERSION = "v1.9.33";

    /** 完整标签，用于日志/通知，例如 "TXH txhlock v3.0"。 */
    public static final String LABEL = "TXH txhlock " + VERSION;

    private AppVer() {
    }
}
