package com.kidslock;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 局域网手机设置页：电视上打开「家长设置」验证页时会启动，
 * 手机扫二维码 → 同一个 WiFi → 输入家长密码 → 改设置 / 解锁。
 * 只用 ServerSocket，不依赖任何 Web 框架；空闲 10 分钟自动关闭。
 */
public final class MiniWeb {
    private static final String TAG = "txhlock-web";
    private static final int[] PORTS = {8765, 8766, 8767, 8768, 8769, 8770};
    private static final long IDLE_MS = 10 * 60 * 1000L;
    private static MiniWeb sInstance;

    private final Context app;
    private final Prefs prefs;
    private ServerSocket server;
    private int port;
    private volatile boolean running = true;
    private long lastTouch = System.currentTimeMillis();
    private final Map<String, Long> sessions = new HashMap<String, Long>();
    private int badCount = 0;
    private long badSince = 0L;

    private MiniWeb(Context c) {
        app = c.getApplicationContext();
        prefs = new Prefs(app);
    }

    public static synchronized boolean start(Context c) {
        if (sInstance != null) {
            sInstance.lastTouch = System.currentTimeMillis();
            return true;
        }
        if (c == null || LanIp.get() == null) return false;
        MiniWeb m = new MiniWeb(c);
        if (!m.bind()) return false;
        sInstance = m;
        m.runServer();
        return true;
    }

    public static synchronized String url() {
        if (sInstance == null) return null;
        String ip = LanIp.get();
        return ip == null ? null : "http://" + ip + ":" + sInstance.port + "/";
    }

    public static synchronized void stop() {
        MiniWeb m = sInstance;
        sInstance = null;
        if (m != null) m.close();
    }

    private boolean bind() {
        for (int p : PORTS) {
            try {
                ServerSocket s = new ServerSocket(p);
                s.setReuseAddress(true);
                s.setSoTimeout(30000);
                server = s;
                port = p;
                return true;
            } catch (Throwable t) {
                server = null;
            }
        }
        return false;
    }

    private void close() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Throwable t) {
            // ignore
        }
        server = null;
    }

    private void runServer() {
        Thread th = new Thread(new Runnable() {
            @Override public void run() {
                while (running) {
                    try {
                        Socket s = server.accept();
                        lastTouch = System.currentTimeMillis();
                        handle(s);
                        try { s.close(); } catch (Throwable t) { }
                    } catch (SocketTimeoutException e) {
                        if (System.currentTimeMillis() - lastTouch > IDLE_MS) close();
                    } catch (Throwable t) {
                        if (!running) break;
                    }
                }
            }
        }, "miniweb");
        th.setDaemon(true);
        th.start();
    }

    // ---------------- HTTP ----------------

    private void handle(Socket s) throws Exception {
        // 只服务局域网：手机用流量/外网访问一律拒绝
        if (s.getInetAddress() == null || !s.getInetAddress().isSiteLocalAddress()) {
            try { s.close(); } catch (Throwable t) { }
            return;
        }
        s.setSoTimeout(15000);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        String first = in.readLine();
        if (first == null) return;
        String[] parts = first.split(" ");
        if (parts.length < 2) return;
        String method = parts[0];
        String path = parts[1];

        Map<String, String> headers = new HashMap<String, String>();
        String line;
        while ((line = in.readLine()) != null && line.length() > 0) {
            int i = line.indexOf(':');
            if (i > 0) {
                headers.put(line.substring(0, i).trim().toLowerCase(Locale.US),
                        line.substring(i + 1).trim());
            }
        }
        int len = 0;
        try { len = Integer.parseInt(headers.get("content-length")); } catch (Throwable t) { }
        String body = "";
        if (len > 0) {
            char[] buf = new char[len];
            int off = 0;
            while (off < len) {
                int r = in.read(buf, off, len - off);
                if (r < 0) break;
                off += r;
            }
            body = new String(buf, 0, off);
        }

        String query = "";
        int qi = path.indexOf('?');
        if (qi >= 0) {
            query = path.substring(qi + 1);
            path = path.substring(0, qi);
        }
        Map<String, String> form = new HashMap<String, String>();
        parse(query, form);
        parse(body, form);

        String sid = cookie(headers.get("cookie"), "sid");
        boolean authed = valid(sid);

        if ("/favicon.ico".equals(path)) return;

        if ("/login".equals(path) && "POST".equals(method)) {
            if (blocked()) {
                html(s, 429, loginPage("尝试次数过多，请 5 分钟后再试"));
                return;
            }
            String pin = form.get("pin");
            if (pin != null && pin.equals(prefs.pin())) {
                badCount = 0;
                String token = UUID.randomUUID().toString().replace("-", "");
                sessions.put(token, System.currentTimeMillis() + 2 * 3600 * 1000L);
                redirectWithCookie(s, "/", "sid=" + token + "; Path=/; Max-Age=7200");
            } else {
                badCount++;
                if (badCount == 1) badSince = System.currentTimeMillis();
                html(s, 200, loginPage("密码错误"));
            }
            return;
        }

        if ("/save".equals(path) && "POST".equals(method)) {
            if (!authed) { redirect(s, "/"); return; }
            html(s, 200, settingsPage(apply(form)));
            return;
        }

        if ("/lock".equals(path) && "POST".equals(method)) {
            if (!authed) { redirect(s, "/"); return; }
            lockNow();
            html(s, 200, settingsPage("已发送「立刻锁定」"));
            return;
        }

        if ("/unlock".equals(path) && "POST".equals(method)) {
            if (!authed) { redirect(s, "/"); return; }
            unlock();
            html(s, 200, settingsPage("已解锁电视"));
            return;
        }

        if ("/logout".equals(path)) {
            if (sid != null) sessions.remove(sid);
            redirect(s, "/");
            return;
        }

        if (!authed) { html(s, 200, loginPage(null)); return; }
        html(s, 200, settingsPage(null));
    }

    private void parse(String q, Map<String, String> out) {
        if (q == null || q.length() == 0) return;
        String[] pairs = q.split("&");
        for (String pair : pairs) {
            int i = pair.indexOf('=');
            try {
                if (i < 0) {
                    out.put(URLDecoder.decode(pair, "UTF-8"), "");
                } else {
                    out.put(URLDecoder.decode(pair.substring(0, i), "UTF-8"),
                            URLDecoder.decode(pair.substring(i + 1), "UTF-8"));
                }
            } catch (Throwable t) {
                // ignore bad field
            }
        }
    }

    private static String cookie(String header, String name) {
        if (header == null) return null;
        String[] parts = header.split(";");
        for (String p : parts) {
            String s = p.trim();
            if (s.startsWith(name + "=")) return s.substring(name.length() + 1);
        }
        return null;
    }

    private boolean valid(String sid) {
        if (sid == null) return false;
        Long exp = sessions.get(sid);
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) {
            sessions.remove(sid);
            return false;
        }
        return true;
    }

    private boolean blocked() {
        if (badCount < 5) return false;
        if (System.currentTimeMillis() - badSince > 5 * 60 * 1000L) {
            badCount = 0;
            return false;
        }
        return true;
    }

    // ---------------- 业务 ----------------

    private String apply(Map<String, String> f) {
        try {
            prefs.setEnabled(f.containsKey("enabled"));
            prefs.setSingleMinutes(clamp(intval(f, "session", 20), 5, 300));
            prefs.setDailyMinutes(clamp(intval(f, "daily", 0), 0, 720));
            prefs.setAwayResetMinutes(clamp(intval(f, "away", 30), 0, 1440));
            prefs.setRequiredCorrect(clamp(intval(f, "correct", 3), 1, 10));
            prefs.setHoldSeconds(clamp(intval(f, "hold", 10), 3, 20));
            prefs.setLockOnBoot(f.containsKey("boot"));
            String pin = f.get("pin");
            if (pin != null && pin.matches("\\d{4,8}")) prefs.setPin(pin);
            if (prefs.isEnabled()) Keeper.startService(app);
            else app.stopService(new Intent(app, QuizService.class));
            return "已保存";
        } catch (Throwable t) {
            Log.w(TAG, "save failed", t);
            return "保存失败，请重试";
        }
    }

    private void lockNow() {
        try {
            prefs.setLocked(true);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override public void run() {
                    try {
                        Keeper.startService(app);
                        Keeper.launchLockNow(app);
                    } catch (Throwable t) {
                        Log.w(TAG, "lock now failed", t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "lock now failed", t);
        }
    }

    private void unlock() {
        try {
            if (prefs.isDailyLocked()) prefs.setDailyExempt(true);
            prefs.setDailyLocked(false);
            prefs.setLocked(false);
            long now = System.currentTimeMillis();
            prefs.setSessionStartMs(now);
            prefs.setLastAliveMs(now);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override public void run() {
                    try { LockOverlay.hide(); } catch (Throwable t) { }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "unlock failed", t);
        }
    }

    private static int intval(Map<String, String> f, String k, int def) {
        try { return Integer.parseInt(f.get(k)); } catch (Throwable t) { return def; }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---------------- 页面 ----------------

    private String loginPage(String err) {
        StringBuilder b = new StringBuilder();
        b.append(head("家长验证"));
        b.append("<div class=logo>每日壁纸</div><div class=sub>家长设置</div>");
        b.append("<form method=post action=/login>");
        b.append("<input name=pin type=password inputmode=numeric autocomplete=off ")
                .append("maxlength=8 placeholder=\"家长密码\" autofocus>");
        b.append("<button class=primary>进入设置</button></form>");
        if (err != null) b.append("<p class=err>").append(esc(err)).append("</p>");
        b.append("<p class=tip>手机必须和电视连同一个家庭 WiFi（局域网内有效，用流量打不开）；密码就是电视上的家长密码。</p>");
        b.append("</div></body></html>");
        return b.toString();
    }

    private String settingsPage(String msg) {
        long today = prefs.todayWatchedMs() / 60000L;
        long start = prefs.sessionStartMs();
        long sess = start > 0 ? (System.currentTimeMillis() - start) / 60000L : 0L;
        if (sess < 0) sess = 0;
        String state = prefs.isLocked() ? "已锁定" : "未锁定";
        StringBuilder b = new StringBuilder();
        b.append(head("家长设置"));
        b.append("<div class=logo>每日壁纸</div><div class=sub>家长设置</div>");
        b.append("<p class=status>今日已看 ").append(today).append(" 分钟 · 本次 ")
                .append(sess).append(" 分钟 · ").append(state).append("</p>");
        if (msg != null) b.append("<p class=ok>").append(esc(msg)).append("</p>");
        b.append("<form method=post action=/save>");
        b.append("<label class=row><span>监控开关</span><input type=checkbox name=enabled ")
                .append(prefs.isEnabled() ? "checked" : "").append("></label>");
        b.append(num("单次连续观看（分钟）", "session", prefs.singleMinutes(), 5, 300));
        b.append(num("每日累计上限（分钟，0=不限）", "daily", prefs.dailyMinutes(), 0, 720));
        b.append(num("长关机重置（分钟，0=不重置）", "away", prefs.awayResetMinutes(), 0, 1440));
        b.append(num("连续答对题数", "correct", prefs.requiredCorrect(), 1, 10));
        b.append(num("长按菜单键（秒）", "hold", prefs.holdSeconds(), 3, 20));
        b.append("<label class=row><span>开机先答题</span><input type=checkbox name=boot ")
                .append(prefs.lockOnBoot() ? "checked" : "").append("></label>");
        b.append("<label class=field><span>新家长密码（4~8 位数字，留空不改）</span>")
                .append("<input name=pin inputmode=numeric maxlength=8 placeholder=\"留空不改\"></label>");
        b.append("<button class=primary>保存设置</button></form>");
        b.append("<form method=post action=/lock><button class=warn>立刻锁定电视</button></form>");
        b.append("<form method=post action=/unlock><button class=ghost>立刻解锁电视</button></form>");
        b.append("<p class=tip>改完立刻生效；电视上的设置页重新进入后会刷新。</p>");
        b.append("<p class=tip><a href=/logout>退出</a></p>");
        b.append("</div></body></html>");
        return b.toString();
    }

    private static String num(String label, String name, int value, int min, int max) {
        return "<label class=field><span>" + esc(label) + "</span><input type=number name=" + name
                + " value=" + value + " min=" + min + " max=" + max + "></label>";
    }

    private static String head(String title) {
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<title>" + esc(title) + "</title><style>"
                + "body{margin:0;background:#0f141b;color:#e8eef7;font-family:-apple-system,"
                + "BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif}"
                + ".wrap{max-width:520px;margin:0 auto;padding:22px 18px 40px}"
                + ".logo{font-size:26px;font-weight:700;margin-top:10px}"
                + ".sub{color:#8fa3b8;font-size:14px;margin:4px 0 18px}"
                + ".status{background:#1a222d;border-radius:12px;padding:12px 14px;color:#9fd0ff;font-size:14px}"
                + ".ok{color:#39d98a;font-size:14px}.err{color:#ff6b6b;font-size:14px}"
                + ".tip{color:#8fa3b8;font-size:12px;line-height:1.6;margin-top:16px}"
                + ".row{display:flex;align-items:center;justify-content:space-between;background:#1a222d;"
                + "border-radius:12px;padding:14px;margin:10px 0}"
                + ".row input{width:24px;height:24px}"
                + ".field{display:block;background:#1a222d;border-radius:12px;padding:12px 14px;margin:10px 0}"
                + ".field span{display:block;color:#c7d4e5;font-size:13px;margin-bottom:8px}"
                + "input[type=password],input[type=number]{width:100%;box-sizing:border-box;"
                + "background:#0f141b;color:#fff;border:1px solid #2a3646;border-radius:10px;"
                + "padding:12px;font-size:17px}"
                + "button{width:100%;border:0;border-radius:12px;padding:15px;font-size:17px;"
                + "font-weight:600;margin-top:12px}"
                + ".primary{background:#1e5af0;color:#fff}.ghost{background:#223041;color:#cfe0f5}"
                + ".warn{background:#c94a3d;color:#fff}"
                + "a{color:#7fb0ff}</style></head><body><div class=wrap>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void html(Socket s, int code, String body) throws Exception {
        byte[] data = body.getBytes("UTF-8");
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Cache-Control: no-store\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = s.getOutputStream();
        out.write(head.getBytes("UTF-8"));
        out.write(data);
        out.flush();
    }

    private void redirect(Socket s, String to) throws Exception {
        String head = "HTTP/1.1 302 Found\r\nLocation: " + to + "\r\n"
                + "Content-Length: 0\r\nConnection: close\r\n\r\n";
        s.getOutputStream().write(head.getBytes("UTF-8"));
        s.getOutputStream().flush();
    }

    private void redirectWithCookie(Socket s, String to, String cookie) throws Exception {
        String head = "HTTP/1.1 302 Found\r\nLocation: " + to + "\r\n"
                + "Set-Cookie: " + cookie + "\r\n"
                + "Content-Length: 0\r\nConnection: close\r\n\r\n";
        s.getOutputStream().write(head.getBytes("UTF-8"));
        s.getOutputStream().flush();
    }
}
