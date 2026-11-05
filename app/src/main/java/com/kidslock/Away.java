package com.kidslock;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 关机 / 重启 / 待机"离开"后的计时接续判定 —— 全应用唯一入口。
 *
 * 背景：孩子关一下电视再开机，就等于把"单次连续观看"的计时清零了（v1.3 及更早就是这么漏的）。
 * 现在改成一套统一规则，短离开不清零、长离开和跨夜才清零：
 *
 *   1. 短离开（离开时长 &lt; 长关机阈值，且没跨夜）
 *      → 计时"暂停"：把离开的这段时长从本次会话里剔除，会话累计原样保留（不奖励也不惩罚）。
 *      → 锁屏状态原样保留：关机前锁屏 → 开机仍锁屏；关机前没锁 → 交给 tick 按累计时长决定。
 *   2. 长离开（离开时长 &gt;= 长关机阈值）
 *      → 清零：单次计时从当前时刻重新开始、解除锁定，当日额度/豁免一并清掉。视为"有效休息"。
 *   3. 跨夜（离开期间跨过本地零点）
 *      → 强制清零：单次计时从零、解除锁定，并清掉上一天的每日额度/豁免标记。
 *   4. 时钟不可信（时间戳回拨超过 1 分钟：开机 RTC 未同步、电池失效、被改时间）
 *      → 无法判断"离开了多久"，按清零解锁处理，避免时间戳在未来导致永久锁定。
 *
 * 结果经 prefs 持久化，本方法幂等：每次调用都会把"最后存活时间"推进到 now，
 * 重复调用（开机广播 + 无障碍 + 服务启动 + 屏幕点亮）不会把同一段离开时长扣两次。
 */
public final class Away {

    /** 正常心跳间隔上限：小于它视为普通 tick（广播抖动、调度延迟），不当作离开。 */
    private static final long MIN_AWAY_MS = 20 * 1000L;

    /** 心跳时间戳写盘节流：避免每秒一次 SharedPreferences 写入（老电视磁盘 I/O 不便宜）。 */
    private static final long TOUCH_MS = 5 * 1000L;

    /** 时钟抖动容忍：回拨小于这个值不算"时钟不可信"，只当普通抖动。 */
    private static final long CLOCK_JITTER_MS = 60 * 1000L;

    /** 无离开（正常心跳）。 */
    public static final int RESULT_NORMAL = 0;
    /** 短离开：计时暂停、锁屏状态保留。 */
    public static final int RESULT_SHORT_KEEP = 1;
    /** 长离开：清零并解锁。 */
    public static final int RESULT_RESET_LONG = 2;
    /** 跨夜：强制清零并解锁。 */
    public static final int RESULT_RESET_CROSSNIGHT = 3;
    /** 时钟不可信（回拨）：按"无法判断离开多久"处理，清零并解锁。 */
    public static final int RESULT_RESET_CLOCK = 4;

    private Away() {
    }

    /**
     * 判定并落盘。返回处理结果（用于日志/调试），调用方通常无需关心返回值。
     *
     * @param p 设置与计时状态
     */
    public static int resolve(Prefs p) {
        return resolve(p, System.currentTimeMillis());
    }

    /** 同 {@link #resolve(Prefs)}，但可指定"当前时刻"（便于逻辑自测）。 */
    public static int resolve(Prefs p, long now) {
        long last = p.lastAliveMs();

        // 首次运行：只记心跳，不做任何判定
        if (last <= 0) {
            p.setLastAliveMs(now);
            return RESULT_NORMAL;
        }

        // 时钟不可信（开机 RTC 未同步、电池没电、被手动改时间）：
        //  - 只回拨一点点（<1 分钟）当抖动，忽略；
        //  - 回拨超过 1 分钟就无法判断"到底离开了多久"，此时宁可清零解锁，
        //    也不能因为时间戳在未来而永远卡在锁定状态（v1.5 就是在这里死锁的）。
        if (last > now) {
            if (last - now < CLOCK_JITTER_MS) {
                p.setLastAliveMs(now);
                return RESULT_NORMAL;
            }
            resetAll(p, now);
            Log.i("KidslockDiag", "away CLOCK-ROLLBACK last-now=" + (last - now) / 1000
                    + "s -> RESET+UNLOCK from 0");
            return RESULT_RESET_CLOCK;
        }

        long gap = now - last;
        if (gap < MIN_AWAY_MS) {
            if (gap >= TOUCH_MS) p.setLastAliveMs(now);
            return RESULT_NORMAL;
        }

        boolean crossNight = !sameDay(last, now);
        int thresholdMin = p.awayResetMinutes();
        boolean longAway = thresholdMin > 0 && gap >= thresholdMin * 60000L;

        if (crossNight) {
            // 跨夜：无论离开多久，一律清零（含每日额度/豁免）
            resetAll(p, now);
            Log.i("KidslockDiag", "away CROSS-NIGHT gap=" + gap / 1000 + "s -> RESET+UNLOCK from 0");
            return RESULT_RESET_CROSSNIGHT;
        }

        if (longAway) {
            // 长关机：同样彻底清零并解锁。
            // v1.5 这里留了"每日额度已用完就不放开"的例外，结果家长看到的是
            // "关机一小时开机还是答题页，而且答题永远解不开（只能输密码）"——
            // 与"长关机重置清零、开机从零计时"的约定相反，现按约定统一清零。
            resetAll(p, now);
            Log.i("KidslockDiag", "away LONG gap=" + gap / 1000 + "s >= " + thresholdMin
                    + "min -> RESET+UNLOCK from 0");
            return RESULT_RESET_LONG;
        }

        // 短离开：计时暂停 —— 把离开时长整体后移起点，会话累计不变
        long start = p.sessionStartMs();
        if (start > 0) {
            p.setSessionStartMs(Math.min(start + gap, now));   // 防御时钟回拨：起点不越过当前时刻
        }
        p.setLastAliveMs(now);
        Log.d("KidslockDiag", "away SHORT gap=" + gap / 1000 + "s < " + thresholdMin
                + "min -> PAUSE (locked=" + p.isLocked() + ")");
        return RESULT_SHORT_KEEP;
    }

    /**
     * 彻底清零：单次计时从 now 重新计、解除锁定、当日额度与家长豁免一起清掉。
     * 长关机 / 跨夜 / 时钟不可信 三种情况共用，保证"重置清零"在三条路径上表现完全一致。
     */
    private static void resetAll(Prefs p, long now) {
        p.setSessionStartMs(now);
        p.setLocked(false);
        p.setDailyLocked(false);
        p.setDailyExempt(false);
        p.setTodayDate(dayKey(now));
        p.setTodayWatchedMs(0);
        p.setLastAliveMs(now);
    }

    /** 两个时刻是否同一天（本地时区）。 */
    public static boolean sameDay(long a, long b) {
        return dayKey(a).equals(dayKey(b));
    }

    /** 本地日期键 yyyy-MM-dd，与 QuizService 里的"今天"判定保持一致。 */
    public static String dayKey(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(ms));
    }
}
