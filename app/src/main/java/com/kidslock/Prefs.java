package com.kidslock;

import android.content.Context;
import android.content.SharedPreferences;

/** 应用设置与锁屏状态的统一持久化封装（SharedPreferences）。 */
public class Prefs {
    private static final String FILE = "kidslock_prefs";
    private final SharedPreferences sp;

    // 题型位掩码（新增学科继续用更高位，保证旧版本设置值仍能正确解读）
    public static final int TYPE_MATH      = 1;      // 口算（应用自动生成）
    public static final int TYPE_PINYIN    = 2;      // 认字（拼音）
    public static final int TYPE_POETRY    = 4;      // 古诗（接龙）
    public static final int TYPE_ENGLISH   = 8;      // 英语
    public static final int TYPE_CUSTOM    = 16;     // 题库 [custom] 段落
    public static final int TYPE_MATH2     = 32;     // 数学思维（应用题/几何/单位换算/找规律）
    public static final int TYPE_CHINESE   = 64;     // 语文（字词/成语/文学常识）
    public static final int TYPE_PHYSICS   = 128;    // 物理
    public static final int TYPE_CHEMISTRY = 256;    // 化学
    public static final int TYPE_BIOLOGY   = 512;    // 生物
    public static final int TYPE_HISTORY   = 1024;   // 历史
    public static final int TYPE_GEOGRAPHY = 2048;   // 地理
    public static final int TYPE_POLITICS  = 4096;   // 道德与法治

    /**
     * 默认开启：语数英 + 数学思维（小学段通用）。理化生史地政是初中科目，
     * 默认关闭，家长可在设置页按需勾选，避免小学生抽到超纲题。
     */
    public static final int DEFAULT_TYPES = TYPE_MATH | TYPE_PINYIN | TYPE_POETRY
            | TYPE_ENGLISH | TYPE_MATH2 | TYPE_CHINESE;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() { return sp.getBoolean("enabled", true); }
    public void setEnabled(boolean v) { sp.edit().putBoolean("enabled", v).apply(); }

    /** 单次连续观看时长（分钟），到点锁屏、答对后重新计时。0=不限制。 */
    public int singleMinutes() { return sp.getInt("single_minutes", 20); }
    public void setSingleMinutes(int v) { sp.edit().putInt("single_minutes", v).apply(); }

    /** 每日累计上限（分钟），0=不限制。 */
    public int dailyMinutes() { return sp.getInt("daily_minutes", 0); }
    public void setDailyMinutes(int v) { sp.edit().putInt("daily_minutes", v).apply(); }

    /**
     * 长关机重置阈值（分钟）：关机/待机/重启离开超过此时长 → 单次计时清零、解除锁定，开机从零计时；
     * 不超过则计时接着走、锁屏状态保留。0 = 不按关机时长清零（只有跨夜才清零）。
     * 与"单次连续观看"无关，单独设置。
     */
    public int awayResetMinutes() { return sp.getInt("away_reset_minutes", 30); }
    public void setAwayResetMinutes(int v) { sp.edit().putInt("away_reset_minutes", v).apply(); }

    /** 连续答对题数，达到才解锁。 */
    public int requiredCorrect() { return sp.getInt("required_correct", 3); }
    public void setRequiredCorrect(int v) { sp.edit().putInt("required_correct", v).apply(); }

    /** 家长 PIN，默认 1234。 */
    public String pin() { return sp.getString("pin", "1234"); }
    public void setPin(String v) { sp.edit().putString("pin", v).apply(); }

    /** 启用题型位掩码。 */
    public int quizTypes() { return sp.getInt("quiz_types", DEFAULT_TYPES); }
    public void setQuizTypes(int v) { sp.edit().putInt("quiz_types", v).apply(); }

    // ---- 教材版本（v3.0）：题目按「版本 + 年级」取，人教/北师大/苏教/外研/译林/通用 ----
    public static final String[] EDITIONS = {"rj", "bs", "sj", "wy", "yl", "ty"};
    public static final String[] EDITION_NAMES = {"人教版", "北师大", "苏教版", "外研版", "译林版", "通用"};

    public String textbook() {
        String v = sp.getString("textbook", "rj");
        for (String e : EDITIONS) if (e.equals(v)) return v;
        return "rj";
    }

    public void setTextbook(String v) {
        for (String e : EDITIONS) {
            if (e.equals(v)) { sp.edit().putString("textbook", v).apply(); return; }
        }
    }

    /** 版本代码 → 显示名。 */
    public static String editionName(String code) {
        if (code == null) return "通用";
        for (int i = 0; i < EDITIONS.length; i++) {
            if (EDITIONS[i].equals(code)) return EDITION_NAMES[i];
        }
        return "通用";
    }

    // 注：v1.8 起取消"覆盖内置题"选项。内置题库是只读的，本地 / SMB 题库只能新增题目，
    // 不再有任何开关能让外部题库顶掉内置题，所以原先的 quiz_overwrite 字段已删除。

    /**
     * 孩子就读年级 1~9（小学一~六年级 = 1-6，初中七~九年级 = 7-9）。
     * 口算按年级自动生成，各学科题库按年级分档取题（只出该年级及以下的题）。
     * 兼容旧版字段 math_level（1-6）。
     */
    public int grade() {
        int g = sp.getInt("grade", 0);
        if (g >= 1 && g <= 9) return g;
        int old = sp.getInt("math_level", 1);   // 老版本只存 1-6 级
        return Math.max(1, Math.min(9, old));
    }

    public void setGrade(int v) { sp.edit().putInt("grade", Math.max(1, Math.min(9, v))).apply(); }

    /** 年级显示名（一年级…九年级）。 */
    public static String gradeName(int g) {
        if (g < 1) g = 1;
        if (g > 9) g = 9;
        return "一二三四五六七八九".substring(g - 1, g) + "年级";
    }

    /** 是否处于锁定状态（持久化，防重启绕过）。 */
    public boolean isLocked() { return sp.getBoolean("locked", false); }
    public void setLocked(boolean v) { sp.edit().putBoolean("locked", v).apply(); }

    /**
     * 上次弹出「主页键拦截失效」提醒的时刻（epoch ms）。
     * 用来做 24 小时节流：无障碍被解绑后家长迟早会进设置页，
     * 但不能每次进都弹一次。
     */
    public long lastKeyHintMs() { return sp.getLong("last_key_hint_ms", 0); }
    public void setLastKeyHintMs(long v) { sp.edit().putLong("last_key_hint_ms", v).apply(); }

    /**
     * 是否真的收到并吞掉过遥控器主页键。
     *
     * 只看 AccessibilityServiceInfo 的 FLAG_REQUEST_FILTER_KEY_EVENTS 是不够的：
     * 小米电视上这个 flag 会显示为"已开启"，但系统根本不会把 HOME 事件发给第三方服务，
     * 旧版因此误报"已物理拦截"，实际按下去照样回桌面。现在改成"真收到过一次才算数"，
     * 没收到过就老老实实按 0.8 秒轮询抢回来。
     */
    public boolean keyFilterSeen() { return sp.getBoolean("key_filter_seen", false); }
    public void setKeyFilterSeen(boolean v) { sp.edit().putBoolean("key_filter_seen", v).apply(); }

    // ---- 计时状态 ----
    public long todayWatchedMs() { return sp.getLong("today_watched_ms", 0); }
    public void setTodayWatchedMs(long v) { sp.edit().putLong("today_watched_ms", v).apply(); }

    public String todayDate() { return sp.getString("today_date", ""); }
    public void setTodayDate(String v) { sp.edit().putString("today_date", v).apply(); }

    /** 本次会话计时起点（epoch ms），0 表示未开始。 */
    public long sessionStartMs() { return sp.getLong("session_start_ms", 0); }
    public void setSessionStartMs(long v) { sp.edit().putLong("session_start_ms", v).apply(); }

    /** 最近一次正常计时 tick 的墙钟时刻（epoch ms），用于检测"关机/待机计时断档"。 */
    public long lastAliveMs() { return sp.getLong("last_alive_ms", 0); }
    public void setLastAliveMs(long v) { sp.edit().putLong("last_alive_ms", v).apply(); }

    // ---- 局域网 SMB 题库同步 ----
    public boolean smbEnabled() { return sp.getBoolean("smb_enabled", false); }
    public void setSmbEnabled(boolean v) { sp.edit().putBoolean("smb_enabled", v).apply(); }
    public String smbHost() { return sp.getString("smb_host", ""); }
    public void setSmbHost(String v) { sp.edit().putString("smb_host", v).apply(); }
    public String smbShare() { return sp.getString("smb_share", ""); }
    public void setSmbShare(String v) { sp.edit().putString("smb_share", v).apply(); }
    public String smbUser() { return sp.getString("smb_user", ""); }
    public void setSmbUser(String v) { sp.edit().putString("smb_user", v).apply(); }
    public String smbPass() { return sp.getString("smb_pass", ""); }
    public void setSmbPass(String v) { sp.edit().putString("smb_pass", v).apply(); }
    public String smbPath() { return sp.getString("smb_path", "/"); }
    public void setSmbPath(String v) { sp.edit().putString("smb_path", v).apply(); }
    /** SMB 远端清单指纹（name=size|mtime;...），用于检测文件变化。 */
    public String smbManifest() { return sp.getString("smb_manifest", ""); }
    public void setSmbManifest(String v) { sp.edit().putString("smb_manifest", v).apply(); }

    /** SMB 上次更新成功时间（epoch ms），0 = 从未成功。用于界面显示"更新成功 + 日期时间"。 */
    public long lastSmbSync() { return sp.getLong("smb_last_sync", 0); }
    public void setLastSmbSync(long v) { sp.edit().putLong("smb_last_sync", v).apply(); }

    /** 开机是否自动锁定（默认开：重启电视不能跳过答题）。 */
    public boolean lockOnBoot() { return sp.getBoolean("lock_on_boot", true); }
    public void setLockOnBoot(boolean v) { sp.edit().putBoolean("lock_on_boot", v).apply(); }

    /** 当日额度是否已耗尽（每日上限触发锁屏）。答题不可解锁，仅家长密码可当日豁免。 */
    public boolean isDailyLocked() { return sp.getBoolean("daily_locked", false); }
    public void setDailyLocked(boolean v) { sp.edit().putBoolean("daily_locked", v).apply(); }

    /**
     * 家长"当日豁免"：家长用密码解开"每日上限"锁屏后置位，当天不再因每日累计上限锁屏
     * （次日随日期变更自动清除）。区别于 isDailyLocked —— 后者表示"本次锁屏是每日上限导致的"。
     */
    public boolean isDailyExempt() { return sp.getBoolean("daily_exempt", false); }
    public void setDailyExempt(boolean v) { sp.edit().putBoolean("daily_exempt", v).apply(); }

    // ---------------- v5.0 壁纸伪装 ----------------
    public long lastBootAt() { return sp.getLong("last_boot_at", 0L); }
    public void setLastBootAt(long v) { sp.edit().putLong("last_boot_at", v).apply(); }

    public int holdSeconds() { return sp.getInt("hold_sec", 5); }
    public void setHoldSeconds(int v) { sp.edit().putInt("hold_sec", v).apply(); }

    public int unlockHoldSeconds() { return sp.getInt("unlock_hold_sec", 5); }
    public void setUnlockHoldSeconds(int v) { sp.edit().putInt("unlock_hold_sec", v).apply(); }

    public boolean disguise() { return sp.getBoolean("disguise", false); }
    public void setDisguise(boolean v) { sp.edit().putBoolean("disguise", v).apply(); }

    public boolean wallpaperBing() { return sp.getBoolean("wall_bing", true); }
    public void setWallpaperBing(boolean v) { sp.edit().putBoolean("wall_bing", v).apply(); }

    public int wallpaperIndex() { return sp.getInt("wall_index", 0); }
    public void setWallpaperIndex(int v) { sp.edit().putInt("wall_index", v).apply(); }

    public long wallpaperFetchedAt() { return sp.getLong("wall_at", 0L); }
    public void setWallpaperFetchedAt(long v) { sp.edit().putLong("wall_at", v).apply(); }

    public String wallpaperCredit() { return sp.getString("wall_credit", ""); }
    public void setWallpaperCredit(String v) { sp.edit().putString("wall_credit", v).apply(); }

    public String wallpaperFile() { return sp.getString("wall_file", ""); }
    public void setWallpaperFile(String v) { sp.edit().putString("wall_file", v).apply(); }
}
