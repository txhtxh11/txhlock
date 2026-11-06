package com.kidslock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 应用内更新：检查 GitHub 上的 update.json，下载 APK、校验 MD5，再拉起系统安装器。 */
public final class UpdateManager {
    private static final String[] META_URLS = {
            "https://raw.githubusercontent.com/txhtxh11/txhlock/master/update.json",
            "https://ghproxy.net/https://raw.githubusercontent.com/txhtxh11/txhlock/master/update.json",
            "https://ghfast.top/https://raw.githubusercontent.com/txhtxh11/txhlock/master/update.json",
            "https://cdn.jsdelivr.net/gh/txhtxh11/txhlock@master/update.json",
            "https://fastly.jsdelivr.net/gh/txhtxh11/txhlock@master/update.json"
    };

    public interface Listener {
        void onResult(UpdateInfo info, String error);
        void onStatus(String msg);
        void onProgress(int percent);
    }

    public static class UpdateInfo {
        public int versionCode;
        public String versionName = "";
        public String notes = "";
        public String md5 = "";
        public String[] urls = new String[0];
    }

    private UpdateManager() {}

    public static int currentVersionCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void check(final Activity a, final Listener l) {
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                UpdateInfo info = null;
                try {
                    String body = null;
                    for (int i = 0; i < META_URLS.length; i++) {
                        try {
                            body = http(META_URLS[i]);
                            if (body != null && body.length() > 0) break;
                        } catch (Throwable ignored) {
                            Log.w("UpdateManager", "meta failed " + META_URLS[i] + " : " + ignored);
                        }
                    }
                    if (body == null) throw new RuntimeException("update.json 下载失败");
                    JSONObject o = new JSONObject(body);
                    UpdateInfo ui = new UpdateInfo();
                    ui.versionCode = o.optInt("versionCode", 0);
                    ui.versionName = o.optString("versionName", "");
                    ui.notes = o.optString("notes", "");
                    ui.md5 = o.optString("md5", "").toLowerCase();
                    JSONArray arr = o.optJSONArray("urls");
                    if (arr != null && arr.length() > 0) {
                        ui.urls = new String[arr.length()];
                        for (int i = 0; i < arr.length(); i++) ui.urls[i] = arr.optString(i, "");
                    } else {
                        String u = o.optString("url", "");
                        if (u.length() > 0) ui.urls = new String[]{u};
                    }
                    if (ui.versionCode > currentVersionCode(a) && ui.urls.length > 0) info = ui;
                } catch (Throwable t) {
                    err = t.getMessage() == null ? t.toString() : t.getMessage();
                }
                final UpdateInfo fi = info;
                final String fe = err;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (l != null) l.onResult(fi, fe);
                    }
                });
            }
        }).start();
    }

    public static void downloadAndInstall(final Activity a, final UpdateInfo info, final Listener l) {
        if (info == null || info.urls == null || info.urls.length == 0) {
            status(a, l, "没有下载地址");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(i);
            } catch (Throwable ignored) { }
            status(a, l, "请先允许「安装未知应用」，再回来点检查更新");
            return;
        }
        status(a, l, "开始下载更新…");
        new Thread(new Runnable() {
            @Override public void run() {
                File out = new File(a.getCacheDir(), UpdateProvider.NAME);
                String err = null;
                try {
                    boolean ok = false;
                    for (int i = 0; i < info.urls.length && !ok; i++) {
                        String u = info.urls[i];
                        if (u == null || u.length() == 0) continue;
                        try {
                            download(u, out, a, l);
                            if (out.exists() && out.length() > 1024) {
                                if (info.md5 == null || info.md5.length() == 0
                                        || info.md5.equalsIgnoreCase(md5(out))) {
                                    ok = true;
                                } else {
                                    err = "MD5 校验失败";
                                    out.delete();
                                }
                            }
                        } catch (Throwable t) {
                            err = t.getMessage();
                            // 保留已下载的部分，换镜像时继续续传
                        }
                    }
                    if (!ok) throw new RuntimeException(err == null ? "下载失败" : err);
                    final File file = out;
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            try {
                                install(a, file);
                                if (l != null) l.onStatus("已打开安装器，请点「安装」");
                            } catch (Throwable t) {
                                Toast.makeText(a, "打开安装器失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (final Throwable t) {
                    final String msg = t.getMessage() == null ? t.toString() : t.getMessage();
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (l != null) l.onStatus("更新失败：" + msg);
                        }
                    });
                }
            }
        }).start();
    }

    private static void install(Context c, File file) {
        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = UpdateProvider.uriFor(c, file);
        } else {
            uri = Uri.fromFile(file);
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(i);
    }

    private static void status(final Activity a, final Listener l, final String msg) {
        a.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (l != null) l.onStatus(msg);
            }
        });
    }

    private static void download(String url, File out, Activity a, final Listener l) throws Exception {
        long offset = out.exists() ? out.length() : 0L;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        tls12(c);
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) txhlock-updater");
        c.setRequestProperty("Accept-Encoding", "identity");
        if (offset > 0) c.setRequestProperty("Range", "bytes=" + offset + "-");
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code != 200 && code != 206) throw new RuntimeException("HTTP " + code);
        boolean append = offset > 0 && code == 206;
        if (offset > 0 && code == 200) offset = 0;   // 镜像不支持续传，从头来
        InputStream is = c.getInputStream();
        int total = c.getContentLength();
        FileOutputStream fos = new FileOutputStream(out, append);
        byte[] buf = new byte[16384];
        int n, done = 0, lastPct = -1;
        while ((n = is.read(buf)) > 0) {
            fos.write(buf, 0, n);
            done += n;
            if (total > 0) {
                long whole = offset + total;
                final int pct = (int) Math.min(100, (offset + done) * 100 / whole);
                if (pct != lastPct && (pct % 5 == 0 || pct == 100)) {
                    lastPct = pct;
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (l != null) l.onProgress(pct);
                        }
                    });
                }
            }
        }
        fos.close();
        is.close();
        c.disconnect();
    }

    private static String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        tls12(c);
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) txhlock-updater");
        c.setInstanceFollowRedirects(true);
        InputStream is = c.getInputStream();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        c.disconnect();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String md5(File f) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        in.close();
        StringBuilder sb = new StringBuilder();
        byte[] d = md.digest();
        for (int i = 0; i < d.length; i++) sb.append(String.format("%02x", d[i]));
        return sb.toString();
    }

    private static void tls12(HttpURLConnection c) {
        if (!(c instanceof javax.net.ssl.HttpsURLConnection)) return;
        try {
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
            sc.init(null, null, null);
            ((javax.net.ssl.HttpsURLConnection) c).setSSLSocketFactory(sc.getSocketFactory());
        } catch (Throwable ignored) { }
    }
}
