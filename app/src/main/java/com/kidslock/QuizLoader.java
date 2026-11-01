package com.kidslock;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自定义题库加载器。
 * 读取两个来源并合并：
 *   - /sdcard/Download/txhlock/（U盘/ADB 放入）
 *   - 应用私有 smb_quiz/ 缓存目录（由 SMB 局域网同步拉取）
 *
 * 文件格式：
 *   # 这是注释
 *   [pinyin]          ← 段落名（题型），可为 pinyin / english / poetry / custom
 *   苹果|ping guo     ← 每行：题干|答案
 *   妈妈|ma ma
 *   [english]
 *   apple|苹果
 *   banana|香蕉
 */
public class QuizLoader {
    private static final String TAG = "QuizLoader";
    public static final String DIR = "/sdcard/Download/txhlock";

    private final Map<String, List<String[]>> customPool = new HashMap<>();
    private final java.util.HashSet<String> seen = new java.util.HashSet<>();

    /** 从磁盘加载自定义题库（本地目录 + SMB 缓存），返回是否加载到内容。 */
    public boolean loadFrom(Context context) {
        customPool.clear();
        seen.clear();
        boolean loaded = loadDir(new File(DIR));
        if (context != null) {
            loaded = loadDir(SMBHelper.cacheDir(context)) || loaded;
        }
        return loaded;
    }

    private boolean loadDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        boolean any = false;
        for (File f : files) {
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) continue;
            // currentSection 在每个文件内独立，避免串段
            String currentSection = null;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.startsWith("[") && line.endsWith("]")) {
                        currentSection = line.substring(1, line.length() - 1).trim().toLowerCase();
                        // Android 4.3 (API 18) 无 Map.putIfAbsent，用兼容写法
                        if (!customPool.containsKey(currentSection)) {
                            customPool.put(currentSection, new ArrayList<>());
                        }
                        continue;
                    }
                    if (currentSection == null) continue;
                    int idx = line.indexOf('|');
                    if (idx <= 0) continue;
                    String q = line.substring(0, idx).trim();
                    String a = line.substring(idx + 1).trim();
                    if (q.isEmpty() || a.isEmpty()) continue;
                    // 去重：本地目录与 SMB 缓存若有相同题，只保留一份，避免同题出现概率异常
                    String key = currentSection + "|" + q + "|" + a;
                    if (seen.add(key)) {
                        customPool.get(currentSection).add(new String[]{q, a});
                        any = true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "读取题库文件失败: " + f.getName(), e);
            }
        }
        return any;
    }

    /** 取某段落的题目对，若该段不存在或为空返回 null。 */
    public List<String[]> getSection(String section) {
        List<String[]> list = customPool.get(section);
        return (list != null && !list.isEmpty()) ? list : null;
    }

    public boolean hasCustom() {
        return !customPool.isEmpty();
    }
}

