package com.kidslock;

import android.content.Context;
import android.content.SharedPreferences;

/** 应用设置与锁屏状态的统一持久化封装（SharedPreferences）。 */
public class Prefs {
    private static final String FILE = "kidslock_prefs";
    private final SharedPreferences sp;

    // 题型位掩码
    public static final int TYPE_MATH    = 1;
    public static final int TYPE_PINYIN  = 2;
    public static final int TYPE_POETRY  = 4;
    public static final int TYPE_ENGLISH = 8;
    public static final int TYPE_CUSTOM  = 16; // 题库 [custom] 段落
    public static final int DEFAULT_TYPES = TYPE_MATH | TYPE_PINYIN | TYPE_POETRY | TYPE_ENGLISH;

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

    /** 连续答对题数，达到才解锁。 */
    public int requiredCorrect() { return sp.getInt("required_correct", 3); }
    public void setRequiredCorrect(int v) { sp.edit().putInt("required_correct", v).apply(); }

    /** 家长 PIN，默认 1234。 */
    public String pin() { return sp.getString("pin", "1234"); }
    public void setPin(String v) { sp.edit().putString("pin", v).apply(); }

    /** 启用题型位掩码。 */
    public int quizTypes() { return sp.getInt("quiz_types", DEFAULT_TYPES); }
    public void setQuizTypes(int v) { sp.edit().putInt("quiz_types", v).apply(); }

    /** 是否处于锁定状态（持久化，防重启绕过）。 */
    public boolean isLocked() { return sp.getBoolean("locked", false); }
    public void setLocked(boolean v) { sp.edit().putBoolean("locked", v).apply(); }

    // ---- 计时状态 ----
    public long todayWatchedMs() { return sp.getLong("today_watched_ms", 0); }
    public void setTodayWatchedMs(long v) { sp.edit().putLong("today_watched_ms", v).apply(); }

    public String todayDate() { return sp.getString("today_date", ""); }
    public void setTodayDate(String v) { sp.edit().putString("today_date", v).apply(); }

    /** 本次会话计时起点（epoch ms），0 表示未开始。 */
    public long sessionStartMs() { return sp.getLong("session_start_ms", 0); }
    public void setSessionStartMs(long v) { sp.edit().putLong("session_start_ms", v).apply(); }

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

    /** 当日额度是否已耗尽（每日上限触发锁屏）。答题不可解锁，仅家长密码可当日豁免。 */
    public boolean isDailyLocked() { return sp.getBoolean("daily_locked", false); }
    public void setDailyLocked(boolean v) { sp.edit().putBoolean("daily_locked", v).apply(); }
}
