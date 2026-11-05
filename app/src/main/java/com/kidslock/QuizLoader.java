package com.kidslock;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 题库加载器。
 *
 * 三个来源，按优先级从高到低加载（同段落、同题干以先加载的为准）：
 *   1. APK 内置 assets/quiz/*.txt            —— 开箱即用（小学~初中各学科），只读
 *   2. /sdcard/Download/txhlock/*.txt        —— U盘 / ADB 放入，只能新增
 *   3. 应用私有 smb_quiz/ 缓存目录            —— SMB 局域网同步，只能新增
 *
 * 内置题库是只读的：它打在 APK 里，本应用没有任何代码会去删它或改写它；
 * 而且它**最先加载**，所以本地/SMB 里出现「同段落 + 同题干」的题会被直接跳过，
 * 永远顶不掉内置题。外部题库的唯一作用就是"补题"。
 *
 * 文件格式：
 *   # 注释
 *   [科目@g年级]                        ← 段落名：科目 + 年级（g1~g9）
 *   题干|答案                             ← 旧格式：干扰项从同段其他答案里随机取
 *   题干|答案|干扰1;干扰2;干扰3           ← 新格式：干扰项自带，选项质量最可控
 *
 * 旧段名（pinyin / poetry / english / custom）不带年级后缀，任何年级都会用。
 */
public class QuizLoader {
    private static final String TAG = "QuizLoader";
    public static final String DIR = "/sdcard/Download/txhlock";
    public static final String ASSET_DIR = "quiz";

    /** 某年级题量少于这个数，就把更低年级的题并进来，避免选项反复重复。 */
    private static final int MIN_GRADE_POOL = 18;
    /** 两次读盘的最小间隔，避免每秒出题都去解析文件。 */
    private static final long RELOAD_THROTTLE_MS = 15000L;

    private final Map<String, List<String[]>> pool = new HashMap<>();
    private final HashSet<String> seen = new HashSet<>();
    private boolean loaded = false;
    private long lastLoadMs = 0L;

    /** 题目三元组：{题干, 答案, 干扰项（分号分隔，可为空字符串）}。 */
    public static final int IDX_Q = 0, IDX_A = 1, IDX_D = 2;

    /** 从磁盘 + APK 内置加载题库（带节流，内置优先、外部只补题）。 */
    public boolean loadFrom(Context context) {
        return loadFrom(context, false);
    }

    /**
     * @param force true = 无视节流立即重读（家长点"重新加载题库"时用）
     */
    public boolean loadFrom(Context context, boolean force) {
        long now = System.currentTimeMillis();
        if (loaded && !force && now - lastLoadMs < RELOAD_THROTTLE_MS) {
            return !pool.isEmpty();
        }
        pool.clear();
        seen.clear();
        loaded = true;
        lastLoadMs = now;

        // 加载顺序决定"谁赢"：先加载的进 seen，后加载的同名题会被跳过。
        // 内置题库固定第一个加载 —— 因此外部题库只能新增题目，无法覆盖内置题。
        boolean any = loadAssets(context);
        if (loadExternal(context)) any = true;
        return any;
    }

    /** 加载外部题库：本地目录 + SMB 缓存目录。 */
    private boolean loadExternal(Context context) {
        boolean any = loadDir(new File(DIR));
        if (context != null) {
            if (loadDir(SMBHelper.cacheDir(context))) any = true;
        }
        return any;
    }

    /** 解析一个目录下的所有 .txt。 */
    private boolean loadDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        boolean any = false;
        for (File f : files) {
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) continue;
            InputStream in = null;
            try {
                in = new FileInputStream(f);
                if (parse(in, f.getName())) any = true;
            } catch (Exception e) {
                Log.w(TAG, "读取题库文件失败: " + f.getName(), e);
            } finally {
                closeQuietly(in);
            }
        }
        return any;
    }

    /** 解析 APK 内置题库 assets/quiz/*.txt。 */
    private boolean loadAssets(Context ctx) {
        AssetManager am = ctx.getAssets();
        String[] names;
        try {
            names = am.list(ASSET_DIR);
        } catch (Exception e) {
            return false;   // 没有内置题库目录，正常退回外部题库
        }
        if (names == null || names.length == 0) return false;
        boolean any = false;
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            if (!n.toLowerCase(Locale.ROOT).endsWith(".txt")) continue;
            InputStream in = null;
            try {
                in = am.open(ASSET_DIR + "/" + n);
                if (parse(in, n)) any = true;
            } catch (Exception e) {
                Log.w(TAG, "读取内置题库失败: " + n, e);
            } finally {
                closeQuietly(in);
            }
        }
        return any;
    }

    /** 统一的逐行解析。每行最多切成 3 段：题干 | 答案 | 干扰项。 */
    private boolean parse(InputStream in, String name) {
        boolean any = false;
        String section = null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                // 去掉 Windows 记事本可能留下的 BOM
                if (line.length() > 0 && line.charAt(0) == '\uFEFF') line = line.substring(1).trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                    if (!pool.containsKey(section)) pool.put(section, new ArrayList<String[]>());
                    continue;
                }
                if (section == null) continue;

                String[] parts = line.split("\\|", 3);
                if (parts.length < 2) continue;
                String q = parts[0].trim();
                String a = parts[1].trim();
                String d = parts.length >= 3 ? parts[2].trim() : "";
                if (q.isEmpty() || a.isEmpty()) continue;

                // 去重键 = 段落 + 题干。内置题库先加载占位，外部题库撞上同键即跳过，
                // 所以外部题库永远改不了内置题的答案，只能新增。
                String key = section + '\u0000' + q;
                if (!seen.add(key)) continue;

                List<String[]> list = pool.get(section);
                if (list == null) {
                    list = new ArrayList<String[]>();
                    pool.put(section, list);
                }
                list.add(new String[]{q, a, d});
                any = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "解析题库失败: " + name, e);
        } finally {
            closeQuietly(br);
        }
        return any;
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 把 "干扰1;干扰2;干扰3" 拆成数组（中英文分号都认）。 */
    public static String[] splitDistractors(String d) {
        if (d == null || d.isEmpty()) return new String[0];
        // ① 优先只按分号切（中英文分号都认）。
        //    选项正文里本来就允许有逗号，例如「(-1, 2)」「x=2，y=3」「不一样，前者大」，
        //    这类逗号是内容不是分隔符，不能拿去切。
        List<String> out = splitBy(d.replace('；', ';'));
        // ② 仅当分号切出来不足 3 个、且原文确实有逗号时，才退回“逗号也算分隔符”的旧规则，
        //    用于兼容早期用逗号写干扰项的题库文件。
        if (out.size() < 3 && (d.indexOf('，') >= 0 || d.indexOf(',') >= 0)) {
            List<String> alt = splitBy(d.replace('；', ';').replace('，', ';').replace(',', ';'));
            if (alt.size() > out.size()) out = alt;
        }
        return out.toArray(new String[0]);
    }

    /** 按分号拆分并去掉空白项。 */
    private static List<String> splitBy(String s) {
        List<String> out = new ArrayList<String>();
        String[] arr = s.split(";");
        for (int i = 0; i < arr.length; i++) {
            String t = arr[i].trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** 取某段落的题目（不带年级），不存在或为空返回 null。 */
    public List<String[]> getSection(String section) {
        List<String[]> list = pool.get(section);
        return (list != null && !list.isEmpty()) ? list : null;
    }

    /**
     * 按「科目 + 年级」取题。
     * 先取 subject@g{年级}；若题量不足 MIN_GRADE_POOL，依次并入更低年级（当复习）。
     * 该科目完全没有分级题时，退回无年级后缀的 subject 段落。
     * 都没有则返回 null —— 调用方应跳过这个题型（不出超纲题）。
     */
    public List<String[]> getForGrade(String subject, int grade) {
        return getForGrade(subject, null, grade);
    }

    /**
     * 按「科目 + 教材版本 + 年级」取题（v3.0）。
     * 段名格式：subject@edition@gN，例如 math@rj@g3 = 人教版三年级数学。
     * 查找顺序：该版本该年级 → 更低年级补足 → 该版本无题时退回通用版本。
     *
     * @param edition 教材版本代码（rj/bs/sj/wy/yl）；null 或空 = 通用
     */
    public List<String[]> getForGrade(String subject, String edition, int grade) {
        List<String[]> out = new ArrayList<>();
        int top = Math.max(1, Math.min(9, grade));
        if (edition != null && edition.length() > 0) {
            for (int g = top; g >= 1; g--) {
                List<String[]> l = pool.get(subject + "@" + edition + "@g" + g);
                if (l != null) out.addAll(l);
                if (out.size() >= MIN_GRADE_POOL) break;
            }
        }
        if (out.isEmpty()) {
            for (int g = top; g >= 1; g--) {
                List<String[]> l = pool.get(subject + "@g" + g);
                if (l != null) out.addAll(l);
                if (out.size() >= MIN_GRADE_POOL) break;
            }
        }
        if (out.isEmpty()) {
            List<String[]> l = pool.get(subject);
            if (l != null) out.addAll(l);
        }
        return out.isEmpty() ? null : out;
    }

    /** 该科目在该年级是否确实有题（用于出题前判断，避免题型开关开着却无题）。 */
    public boolean hasForGrade(String subject, int grade) {
        return getForGrade(subject, grade) != null;
    }

    public boolean hasCustom() {
        return !pool.isEmpty();
    }

    /** 已加载题目总数（设置页状态显示用）。 */
    public int totalQuestions() {
        int n = 0;
        for (List<String[]> l : pool.values()) n += (l == null ? 0 : l.size());
        return n;
    }

    /** 已加载的段落数。 */
    public int totalSections() {
        return pool.size();
    }

    /**
     * 把 APK 内置题库导出成明文 txt，方便家长查看、照着格式自己加题。
     * 建议导出到 DIR 下的子目录（子目录不会被当成题库加载，不会造成重复）。
     *
     * @return 导出的文件数；失败返回 -1
     */
    public static int exportBuiltin(Context ctx, File destDir) {
        if (ctx == null || destDir == null) return -1;
        AssetManager am = ctx.getAssets();
        String[] names;
        try {
            names = am.list(ASSET_DIR);
        } catch (Exception e) {
            return -1;
        }
        if (names == null || names.length == 0) return 0;
        if (!destDir.exists() && !destDir.mkdirs()) return -1;

        int n = 0;
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (!name.toLowerCase(Locale.ROOT).endsWith(".txt")) continue;
            InputStream in = null;
            java.io.FileOutputStream out = null;
            try {
                in = am.open(ASSET_DIR + "/" + name);
                out = new java.io.FileOutputStream(new File(destDir, name));
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                out.flush();
                n++;
            } catch (Exception e) {
                Log.w(TAG, "导出内置题库失败: " + name, e);
            } finally {
                closeQuietly(out);
                closeQuietly(in);
            }
        }
        return n;
    }
}
