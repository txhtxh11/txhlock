package com.kidslock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/**
 * 前台计时服务：累计观看时长，到点触发全屏答题锁屏。
 *
 * 会话模型：
 *   - todayWatchedMs：今日累计"已观看"时长（锁屏时把当日会话时长并入）
 *   - sessionStartMs：本次会话起点，会话时长 = now - sessionStartMs
 *   - 单次超时：会话时长 >= singleMinutes → 锁屏
 *   - 每日超时：todayWatchedMs + 当日会话时长 >= dailyMinutes → 锁屏（家长可当日豁免）
 *
 * 关机 / 重启 / 待机（v1.4 重做）：
 *   - 屏幕熄灭即暂停计时（不累计、不触发锁定）。
 *   - 离开后的接续判定统一由 {@link Away#resolve(Prefs)} 负责，本类只在 tick 与屏幕点亮时调用：
 *       短离开（小于长关机阈值且未跨夜）→ 计时暂停、锁屏状态保留（关机前锁屏则开机仍锁）；
 *       长离开（达到长关机阈值）→ 清零解锁、从零计时；
 *       跨夜 → 强制清零解锁、从零计时。
 *   - 不再拿"单次限额"当关机断档阈值（v1.3 的老毛病：单次设 20 分钟，关机 21 分钟就被当成
 *     有效休息直接解锁，家长根本没法单独控制）。
 */
public class QuizService extends Service {
    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "kidslock_timer";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private volatile boolean running = false;
    /** 上次推送到通知栏的"今日已看"分钟数，避免每秒 notify 一次（老电视上很费）。 */
    private long lastNotifMinutes = -1;

    // 屏幕点亮（待机/关机恢复）时做接续判定：短离开续时、长离开/跨夜清零。
    private final BroadcastReceiver screenOnReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_SCREEN_ON.equals(intent.getAction()) && prefs != null) {
                Away.resolve(prefs);
            }
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try {
                tick();
            } catch (Exception e) {
                Log.w("txhlock", "tick failed: " + e);
            }
            handler.postDelayed(this, 1000);
        }
    };

    // 锁定状态下尝试恢复锁屏页（切走如按HOME后尽力弹回）。
    //
    // 节奏由「按键过滤是否生效」决定（v1.9）：
    //   · 拿到了 → 主页键在系统分发前就被吞掉，桌面根本不出现，3 秒一次足够（省电、不打扰）
    //   · 没拿到（无障碍被解绑 / 家长关过 / 覆盖安装后）→ 主页键能真的回桌面，
    //     这时必须把窗口压到 0.8 秒，孩子最多看到桌面闪一下就被抢回来
    // 每 10 秒复查一次状态，家长修好无障碍后自动降回 3 秒，不用重启应用。
    private static final long RESTORE_SLOW_MS = 3000L;
    private static final long RESTORE_FAST_MS = 800L;
    private static final long KEY_FILTER_RECHECK_MS = 10000L;
    private boolean keyFilterOk = true;
    private long lastKeyFilterCheckMs = 0L;

    // 注意：锁屏页已在前台时绝不重复拉起，否则会打断答题反馈和家长密码输入。
    private final Runnable lockRestorer = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastKeyFilterCheckMs >= KEY_FILTER_RECHECK_MS) {
                // 只有「真的拦住过一次主页键」才敢用 3 秒慢轮询；
                // 小米电视常常 flag 有、事件不来，这时必须保持 0.8 秒快抢。
                keyFilterOk = KeyFilter.isEffective(QuizService.this)
                        || LockOverlay.canShow(QuizService.this);
                lastKeyFilterCheckMs = now;
            }
            if (prefs != null && prefs.isLocked() && !LockScreenActivity.sForeground
                    && !LockOverlay.isShowing()) {
                // 用 launchLockNow 而不是 launchLock：后者有 1.5 秒节流，会把 0.8 秒的节奏拖慢
                Keeper.launchLockNow(QuizService.this);
            }
            handler.postDelayed(this, keyFilterOk ? RESTORE_SLOW_MS : RESTORE_FAST_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        // 服务启动即做一次接续判定（开机、被杀后重启都走这里）：
        // 短离开 → 续时且保留锁屏状态；长离开或跨夜 → 清零解锁、从零计时。
        Away.resolve(prefs);
        // 注册屏幕点亮广播：待机恢复时再次判定（幂等，重复调用不会重复扣减）
        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenOnReceiver, screenFilter);
        createChannel();
        // 首条通知直接用已记录的今日时长，避免开机瞬间显示"0 分钟"再跳变
        startForeground(NOTIF_ID, buildNotification(prefs.todayWatchedMs() / 60000));
        running = true;
        handler.post(ticker);
        handler.post(lockRestorer);
        // 服务哪怕被杀，AlarmManager 看门狗也能把锁屏救回来
        Keeper.scheduleWatchdog(this);
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
        try { unregisterReceiver(screenOnReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void tick() {
        long now = System.currentTimeMillis();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        // 屏幕熄灭（待机/关机）即暂停计时：不累计、不触发锁定，起点保留。
        // 注意此时不推进 lastAliveMs —— 屏幕点亮后由 Away.resolve 把这段时长判定为
        // "短离开（暂停）"或"长离开（清零）"。
        if (pm != null && !pm.isScreenOn()) {
            return;
        }
        // 接续判定（短离开暂停 / 长离开清零 / 跨夜清零），并推进心跳时间戳
        Away.resolve(prefs);

        // 监控已停用：停止计时，不触发锁屏
        if (!prefs.isEnabled()) {
            if (prefs.sessionStartMs() != 0) prefs.setSessionStartMs(0);
            return;
        }
        // 跨天：重置每日累计与每日标记（单次计时的跨夜清零由 Away 负责）
        String today = Away.dayKey(now);
        if (!today.equals(prefs.todayDate())) {
            prefs.setTodayDate(today);
            prefs.setTodayWatchedMs(0);
            prefs.setDailyLocked(false);    // 新的一天，当日额度重置
            prefs.setDailyExempt(false);    // 昨天的家长豁免不再有效
        }
        // 已锁定：保持锁定状态，不反复拉起锁屏页（进入锁屏只由 doLock 触发一次）
        if (prefs.isLocked()) {
            return;
        }
        // 起点为 0 或早于今日零点（跨夜/隔夜待机）时重置为当前时间，从 0 重新计时。
        // 关键：不要在这里把"已超过单次限额"当成陈旧起点重置 —— 那会把正常连续观看到点
        // 该锁屏误判成陈旧起点而重置，导致单次锁定永远不触发（v1.0 修复过）。
        // 关机/待机时长已由前面的 Away.resolve 处理，这里只管"连续观看"。
        long start = prefs.sessionStartMs();
        long dayStart = dayStartMs();
        if (start == 0 || start < dayStart) {
            prefs.setSessionStartMs(now);
            start = now;
        }
        long sessionElapsed = now - start;
        long todaySessionElapsed = Math.max(0, now - Math.max(start, dayStart));

        boolean single = prefs.singleMinutes() > 0 && sessionElapsed >= prefs.singleMinutes() * 60000L;
        boolean daily  = prefs.dailyMinutes() > 0
                && !prefs.isDailyLocked()        // 本次锁屏已由每日上限触发
                && !prefs.isDailyExempt()        // 家长今天已豁免
                && (prefs.todayWatchedMs() + todaySessionElapsed) >= prefs.dailyMinutes() * 60000L;

        if (single || daily) {
            Log.i("KidslockDiag", "LOCK single=" + single + " daily=" + daily
                    + " el=" + sessionElapsed + " tel=" + todaySessionElapsed);
            doLock(todaySessionElapsed, daily);
            return;
        }
        // 更新通知显示今日已看时长（仅在分钟数变化时推送，避免每秒刷通知）
        long todayMinutes = (prefs.todayWatchedMs() + todaySessionElapsed) / 60000;
        if (todayMinutes != lastNotifMinutes) {
            lastNotifMinutes = todayMinutes;
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NOTIF_ID, buildNotification(todayMinutes));
        }
    }

    /** 当日零点（epoch ms）。 */
    private long dayStartMs() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 执行锁屏：把当日会话时长并入每日累计、置锁屏状态，拉起锁屏页（仅此一次）。 */
    private void doLock(long todaySessionElapsed, boolean daily) {
        prefs.setTodayWatchedMs(prefs.todayWatchedMs() + todaySessionElapsed);
        prefs.setSessionStartMs(0);
        prefs.setLocked(true);
        if (daily) prefs.setDailyLocked(true);   // 每日额度耗尽：答题不可解锁，仅家长密码可当日豁免
        lastNotifMinutes = -1;
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
