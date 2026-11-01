package com.kidslock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 前台计时服务：累计观看时长，到点触发全屏答题锁屏。
 * 会话模型：
 *   - todayWatchedMs：今日累计"已解锁观看"时长（锁屏时把当日会话时长并入）
 *   - sessionStartMs：本次会话起点，会话时长 = now - sessionStartMs
 *   - 单次超时：会话时长 >= singleMinutes → 锁屏
 *   - 每日超时：todayWatchedMs + 当日会话时长 >= dailyMinutes → 锁屏
 * 修复项：
 *   - 停用时停止计时（不触发锁屏）
 *   - 跨午夜只重置每日累计，不清单次会话起点
 *   - 锁定状态下不再每秒拉起锁屏页（仅在进入锁定时拉起一次）
 */
public class QuizService extends Service {
    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "kidslock_timer";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private boolean running = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            tick();
            handler.postDelayed(this, 1000);
        }
    };

    // 锁定状态下低频尝试恢复锁屏页（切走如按HOME后尽力弹回，每3秒；系统可能拦截，尽力而为）
    private final Runnable lockRestorer = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (prefs != null && prefs.isLocked()) {
                try {
                    Intent i = new Intent(QuizService.this, LockScreenActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                } catch (Exception ignored) {
                }
            }
            handler.postDelayed(this, 3000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        createChannel();
        startForeground(NOTIF_ID, buildNotification(0));
        running = true;
        handler.post(ticker);
        handler.post(lockRestorer);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            running = true;
            handler.post(ticker);
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(lockRestorer);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void tick() {
        long now = System.currentTimeMillis();
        // 监控已停用：停止计时，不触发锁屏
        if (!prefs.isEnabled()) {
            prefs.setSessionStartMs(0);
            return;
        }
        // 跨天：只重置每日累计，保留单次会话起点（避免单次限制被跨午夜意外重置）
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        if (!today.equals(prefs.todayDate())) {
            prefs.setTodayDate(today);
            prefs.setTodayWatchedMs(0);
            prefs.setDailyLocked(false);   // 新的一天，当日额度重置
        }
        // 已锁定：保持锁定状态，不反复拉起锁屏页（进入锁屏只由 doLock 触发一次）
        if (prefs.isLocked()) {
            return;
        }
        // 计时
        long start = prefs.sessionStartMs();
        if (start == 0) { prefs.setSessionStartMs(now); start = now; }
        long sessionElapsed = now - start;
        long dayStart = dayStartMs();
        long todaySessionElapsed = Math.max(0, now - Math.max(start, dayStart));

        boolean single = prefs.singleMinutes() > 0 && sessionElapsed >= prefs.singleMinutes() * 60000L;
        boolean daily  = prefs.dailyMinutes()  > 0 && !prefs.isDailyLocked()
                && (prefs.todayWatchedMs() + todaySessionElapsed) >= prefs.dailyMinutes() * 60000L;

        if (single || daily) {
            doLock(sessionElapsed, todaySessionElapsed, daily);
            return;
        }
        // 更新通知显示今日已看时长
        long todayMs = prefs.todayWatchedMs() + todaySessionElapsed;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(todayMs / 60000));
    }

    /** 当日零点（epoch ms）。 */
    private long dayStartMs() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 执行锁屏：把当日会话时长并入每日累计、置锁屏状态，拉起锁屏页（仅此一次）。 */
    private void doLock(long sessionElapsed, long todaySessionElapsed, boolean daily) {
        prefs.setTodayWatchedMs(prefs.todayWatchedMs() + todaySessionElapsed);
        prefs.setSessionStartMs(0);
        prefs.setLocked(true);
        if (daily) prefs.setDailyLocked(true);   // 每日额度耗尽：答题不可解锁，仅家长密码可当日豁免
        startLockScreenIfNeeded();
    }

    private void startLockScreenIfNeeded() {
        try {
            Intent i = new Intent(this, LockScreenActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification(long minutes) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle("答题锁屏守护中")
          .setContentText("今日已看 " + minutes + " 分钟，到点需答题解锁")
          .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
          .setOngoing(true);
        // 点击进入家长设置
        Intent i = new Intent(this, MainActivity.class);
        int piFlag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlag |= PendingIntent.FLAG_IMMUTABLE;   // API<23 无该 flag
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, piFlag);
        b.setContentIntent(pi);
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "答题锁屏计时",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }
}
