package com.kidslock;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 错题本：把答错的题完整存下来（题干、选项、正确答案、解析），
 * 下次优先重练，答对后移出。最多保留 100 道，存在应用私有目录。
 */
public class WrongBook {
    private static final String TAG = "WrongBook";
    private static final String FILE = "wrong_book.json";
    private static final int MAX = 100;

    private final File file;
    private final List<Item> items = new ArrayList<Item>();

    public WrongBook(Context c) {
        file = new File(c.getFilesDir(), FILE);
        load();
    }

    public static String keyOf(QuizQuestion q) {
        if (q == null || q.question == null || q.options == null
                || q.correctIndex < 0 || q.correctIndex >= q.options.length) return "";
        return (q.type == null ? "" : q.type) + '\u0000' + q.question + '\u0000'
                + q.options[q.correctIndex];
    }

    public synchronized void add(QuizQuestion q) {
        String key = q == null ? "" : (q.key == null || q.key.length() == 0 ? keyOf(q) : q.key);
        if (key.length() == 0) return;
        for (int i = 0; i < items.size(); i++) {
            if (key.equals(items.get(i).key)) {
                Item it = items.remove(i);
                it.count++;
                it.at = System.currentTimeMillis();
                items.add(0, it);
                save();
                return;
            }
        }
        Item it = Item.from(q);
        if (it == null) return;
        items.add(0, it);
        while (items.size() > MAX) items.remove(items.size() - 1);
        save();
    }

    public synchronized void remove(QuizQuestion q) {
        String key = q == null ? "" : (q.key == null || q.key.length() == 0 ? keyOf(q) : q.key);
        if (key.length() == 0) return;
        boolean changed = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            if (key.equals(items.get(i).key)) {
                items.remove(i);
                changed = true;
            }
        }
        if (changed) save();
    }

    /** 下一道错题；没有则返回 null。 */
    public synchronized QuizQuestion next() {
        for (int i = 0; i < items.size(); i++) {
            QuizQuestion q = items.get(i).toQuestion();
            if (q != null) return q;
        }
        return null;
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized void clear() {
        items.clear();
        save();
    }

    private void load() {
        if (!file.exists()) return;
        InputStreamReader r = null;
        try {
            r = new InputStreamReader(new FileInputStream(file), "UTF-8");
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length() && items.size() < MAX; i++) {
                Item it = Item.from(arr.optJSONObject(i));
                if (it != null) items.add(it);
            }
        } catch (Throwable t) {
            Log.w(TAG, "load failed", t);
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
    }

    private void save() {
        OutputStreamWriter w = null;
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < items.size(); i++) arr.put(items.get(i).toJson());
            w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            w.write(arr.toString());
        } catch (Throwable t) {
            Log.w(TAG, "save failed", t);
        } finally {
            if (w != null) try { w.close(); } catch (Exception ignored) { }
        }
    }

    private static class Item {
        String key, question, type, subLabel, explanation;
        String[] options;
        int correctIndex, count;
        long at;

        static Item from(QuizQuestion q) {
            if (q == null || q.question == null || q.options == null
                    || q.options.length < 4 || q.correctIndex < 0 || q.correctIndex >= q.options.length) return null;
            Item it = new Item();
            it.key = q.key == null ? keyOf(q) : q.key;
            it.question = q.question;
            it.options = q.options.clone();
            it.correctIndex = q.correctIndex;
            it.type = q.type;
            it.subLabel = q.subLabel;
            it.explanation = q.explanation;
            it.count = 1;
            it.at = System.currentTimeMillis();
            return it;
        }

        static Item from(JSONObject o) {
            if (o == null) return null;
            try {
                String q = o.optString("q", "");
                JSONArray a = o.optJSONArray("o");
                if (q.length() == 0 || a == null || a.length() < 4) return null;
                Item it = new Item();
                it.question = q;
                it.options = new String[a.length()];
                for (int i = 0; i < a.length(); i++) it.options[i] = a.optString(i, "");
                it.correctIndex = o.optInt("c", 0);
                if (it.correctIndex < 0 || it.correctIndex >= it.options.length) return null;
                it.type = o.optString("t", "");
                it.subLabel = o.optString("s", "");
                it.explanation = o.optString("e", "");
                it.key = o.optString("k", "");
                it.count = o.optInt("n", 1);
                it.at = o.optLong("at", System.currentTimeMillis());
                if (it.key.length() == 0) {
                    QuizQuestion tmp = it.toQuestion();
                    it.key = keyOf(tmp);
                }
                return it;
            } catch (Throwable t) {
                return null;
            }
        }

        QuizQuestion toQuestion() {
            if (question == null || options == null
                    || options.length < 4 || correctIndex < 0 || correctIndex >= options.length) return null;
            QuizQuestion q = new QuizQuestion();
            q.question = question;
            q.options = options.clone();
            q.correctIndex = correctIndex;
            q.type = type;
            q.subLabel = subLabel;
            q.explanation = explanation;
            q.key = key;
            return q;
        }

        JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("k", key);
                o.put("q", question);
                JSONArray a = new JSONArray();
                for (int i = 0; i < options.length; i++) a.put(options[i]);
                o.put("o", a);
                o.put("c", correctIndex);
                o.put("t", type == null ? "" : type);
                o.put("s", subLabel == null ? "" : subLabel);
                o.put("e", explanation == null ? "" : explanation);
                o.put("n", count);
                o.put("at", at);
                return o;
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
