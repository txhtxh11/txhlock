package com.kidslock;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 壁纸伪装入口（v5.0）。
 *
 * 桌面图标显示「每日壁纸」，打开后是一张必应每日一图（没网时用内置壁纸）。
 * 短按【菜单键】= 壁纸设置（换一张 / 每日更新 / 关闭）；
 * 长按【菜单键】5 秒 = 家长设置（会先要求家长密码）。
 *
 * 目的：孩子看到的是一个壁纸应用，不会想到去卸载"答题锁屏"。
 * 注意：这是"伪装"而不是系统级防卸载；懂行的成人仍可在系统设置里卸载。
 */
public class WallpaperActivity extends Activity {

    private static final String TAG = "txhlock";
    private static final String BING_URL =
            "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN";

    private static final String[] LOCAL = {
            "photo1.jpg", "photo2.jpg", "photo3.jpg", "photo4.jpg", "photo5.jpg", "photo6.jpg",
            "dawn.jpg", "day.jpg", "dusk.jpg", "night.jpg"
    };
    private static final String[] LOCAL_CREDIT = {
            "图片来自 Unsplash", "图片来自 Unsplash", "图片来自 Unsplash",
            "图片来自 Unsplash", "图片来自 Unsplash", "图片来自 Unsplash",
            "内置壁纸", "内置壁纸", "内置壁纸", "内置壁纸"
    };

    private Prefs prefs;
    private ImageView img;
    private TextView tvDate, tvCredit, tvHold;
    private View holdBox;
    private ProgressBar loading;
    private final Handler h = new Handler(Looper.getMainLooper());

    private boolean holding = false;
    private boolean longFired = false;
    private long holdStart = 0;

    private final Runnable showHold = new Runnable() {
        @Override public void run() {
            if (!holding) return;
            holdBox.setVisibility(View.VISIBLE);
            updateHold();
            h.postDelayed(tick, 400);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!holding) return;
            updateHold();
            h.postDelayed(this, 400);
        }
    };

    private final Runnable fire = new Runnable() {
        @Override public void run() {
            if (!holding) return;
            longFired = true;
            holding = false;
            holdBox.setVisibility(View.GONE);
            openSettings();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_wallpaper);
        img = (ImageView) findViewById(R.id.img);
        tvDate = (TextView) findViewById(R.id.tvDate);
        tvCredit = (TextView) findViewById(R.id.tvCredit);
        holdBox = findViewById(R.id.holdBox);
        tvHold = (TextView) findViewById(R.id.tvHold);
        loading = (ProgressBar) findViewById(R.id.loading);

        SimpleDateFormat f = new SimpleDateFormat("yyyy年M月d日 · EEEE", Locale.CHINA);
        tvDate.setText(f.format(new Date()));

        showLocal();
        if (prefs.wallpaperBing()) loadBing();
    }

    private void openSettings() {
        try {
            startActivity(new Intent(this, MainActivity.class));
        } catch (Throwable t) {
            Log.w(TAG, "open settings failed", t);
        }
    }

    // ---------------- 按键：短按壁纸设置，长按 5 秒家长设置 ----------------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                holding = true;
                longFired = false;
                holdStart = System.currentTimeMillis();
                h.removeCallbacks(showHold);
                h.removeCallbacks(tick);
                h.removeCallbacks(fire);
                long total = holdTotalMs();
                h.postDelayed(showHold, (long) (total * 0.8));
                h.postDelayed(fire, total);
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (holding) {
                    holding = false;
                    h.removeCallbacks(showHold);
                    h.removeCallbacks(tick);
                    h.removeCallbacks(fire);
                    holdBox.setVisibility(View.GONE);
                    if (!longFired) showWallpaperSettings();
                }
                return true;
            }
            return true;
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private long holdTotalMs() {
        return Math.max(3, Math.min(20, prefs.holdSeconds())) * 1000L;
    }

    private void updateHold() {
        long leftMs = holdTotalMs() - (System.currentTimeMillis() - holdStart);
        int left = (int) Math.ceil(leftMs / 1000.0);
        if (left < 1) left = 1;
        tvHold.setText("继续按住 " + left + " 秒…");
    }

    @Override protected void onPause() {
        super.onPause();
        holding = false;
        h.removeCallbacks(showHold);
        h.removeCallbacks(tick);
        h.removeCallbacks(fire);
        if (holdBox != null) holdBox.setVisibility(View.GONE);
    }

    // ---------------- 壁纸设置（短按菜单键看到的） ----------------

    private void showWallpaperSettings() {
        LinearLayout panel = Ui.dialogPanel(this);
        panel.addView(Ui.text(this, "壁纸设置", 24, Ui.color(this, R.color.text_main), true));
        TextView sub = Ui.text(this, "图片来自 Bing 每日一图；没有网络时使用内置壁纸。", 16,
                Ui.color(this, R.color.text_dim), false);
        sub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12));
        panel.addView(sub);

        final android.app.Dialog dlg = Ui.dialog(this, panel, 600);

        final TextView b1 = Ui.button(this, bingLabel(), false, 72);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = !prefs.wallpaperBing();
                prefs.setWallpaperBing(on);
                b1.setText(bingLabel());
                if (on) loadBing();
                toast(on ? "已开启每日更新" : "已关闭每日更新，使用内置壁纸");
            }
        });
        addButton(panel, b1);

        TextView b2 = Ui.button(this, "换一张壁纸", false, 72);
        b2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setWallpaperIndex(prefs.wallpaperIndex() + 1);
                showLocal();
                toast("已换一张");
            }
        });
        addButton(panel, b2);

        TextView b3 = Ui.button(this, "关闭", true, 72);
        b3.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); }
        });
        addButton(panel, b3);
        dlg.show();
        b3.requestFocus();
    }

    private void addButton(LinearLayout panel, TextView b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 68));
        lp.topMargin = Ui.dp(this, 10);
        panel.addView(b, lp);
    }

    private String bingLabel() {
        return "每日自动更新（必应）：" + (prefs.wallpaperBing() ? "开" : "关");
    }

    // ---------------- 内置壁纸 ----------------

    private void showLocal() {
        try {
            Calendar cal = Calendar.getInstance();
            int day = cal.get(Calendar.DAY_OF_YEAR);
            int idx = (day + prefs.wallpaperIndex()) % LOCAL.length;
            if (idx < 0) idx += LOCAL.length;
            InputStream is = getAssets().open("wallpapers/" + LOCAL[idx]);
            Bitmap b = BitmapFactory.decodeStream(is);
            is.close();
            if (b != null) show(b, LOCAL_CREDIT[idx] + " · 每天自动更换");
        } catch (Throwable t) {
            Log.w(TAG, "local wallpaper failed", t);
        }
    }

    // ---------------- 必应每日一图 ----------------

    private void loadBing() {
        long at = prefs.wallpaperFetchedAt();
        File f = prefs.wallpaperFile() == null ? null : new File(prefs.wallpaperFile());
        if (at > 0 && sameDay(at, System.currentTimeMillis()) && f != null && f.exists()) {
            Bitmap b = decode(f);
            if (b != null) {
                show(b, prefs.wallpaperCredit());
                return;
            }
        }
        loading.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String body = http(BING_URL);
                    JSONObject o = new JSONObject(body);
                    JSONArray arr = o.getJSONArray("images");
                    JSONObject im = arr.getJSONObject(0);
                    String path = im.getString("url");
                    final String credit = im.optString("copyright", "");
                    String full = path.startsWith("http") ? path : "https://cn.bing.com" + path;

                    File dir = new File(getFilesDir(), "wall");
                    if (!dir.exists()) dir.mkdirs();
                    File out = new File(dir, "today.jpg");
                    download(full, out);

                    final Bitmap b = decode(out);
                    if (b == null) throw new RuntimeException("decode failed");
                    prefs.setWallpaperFetchedAt(System.currentTimeMillis());
                    prefs.setWallpaperFile(out.getAbsolutePath());
                    prefs.setWallpaperCredit(credit);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!isFinishing()) show(b, credit);
                        }
                    });
                } catch (Throwable t) {
                    Log.w(TAG, "bing wallpaper failed: " + t);
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { loading.setVisibility(View.GONE); }
                    });
                }
            }
        }).start();
    }

    private void show(Bitmap b, String credit) {
        img.setImageBitmap(b);
        if (credit != null && credit.length() > 0) {
            tvCredit.setText(credit);
        } else if (prefs.wallpaperBing()) {
            tvCredit.setText("Bing 每日一图");
        }
    }

    private static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    private static String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        tls12(c);
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 4.3; TV) AppleWebKit/537.36 Chrome/40.0 Safari/537.36");
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

    private static void download(String url, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        tls12(c);
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 4.3; TV) AppleWebKit/537.36 Chrome/40.0 Safari/537.36");
        c.setInstanceFollowRedirects(true);
        InputStream is = c.getInputStream();
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[16384];
        int n;
        while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
        fos.close();
        is.close();
        c.disconnect();
    }

    /** Android 4.3 默认可能只谈 TLS 1.0，这里强制试一次 TLS 1.2。 */
    private static void tls12(HttpURLConnection c) {
        if (!(c instanceof javax.net.ssl.HttpsURLConnection)) return;
        try {
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
            sc.init(null, null, null);
            ((javax.net.ssl.HttpsURLConnection) c).setSSLSocketFactory(sc.getSocketFactory());
        } catch (Throwable ignored) {
        }
    }

    private static Bitmap decode(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 1920) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
