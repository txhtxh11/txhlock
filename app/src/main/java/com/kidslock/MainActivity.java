package com.kidslock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * 旧版桌面快捷方式还指向 com.kidslock.MainActivity。
 * 这里只做一件事：不管谁点进来（旧图标/外部启动），都立刻跳回「每日壁纸」。
 * 真正的家长设置在 SettingsActivity，只有壁纸界面长按菜单键才会进入。
 */
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = new Intent(this, WallpaperActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}