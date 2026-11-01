package com.kidslock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 出题核心：根据启用的题型生成四选一题目。
 * 支持：数学口算(自动生成)、汉字拼音、古诗、英语单词、自定义(custom)，
 * 并与自定义 txt 题库合并随机出题。
 */
public class QuizEngine {
    private final Prefs prefs;
    private final QuizLoader loader;
    private final android.content.Context context;
    private final Random rnd = new Random();

    // 内置题库：String[]{题干, 答案}
    private static final String[][] PINYIN_POOL = {
        {"人","rén"},{"口","kǒu"},{"手","shǒu"},{"足","zú"},{"日","rì"},
        {"月","yuè"},{"山","shān"},{"水","shuǐ"},{"火","huǒ"},{"田","tián"},
        {"目","mù"},{"耳","ěr"},{"大","dà"},{"小","xiǎo"},{"上","shàng"},
        {"下","xià"},{"天","tiān"},{"地","dì"},{"云","yún"},{"雨","yǔ"},
        {"风","fēng"},{"雪","xuě"},{"花","huā"},{"草","cǎo"},{"鸟","niǎo"},
        {"鱼","yú"},{"虫","chóng"},{"车","chē"},{"马","mǎ"},{"牛","niú"},
        {"羊","yáng"},{"狗","gǒu"},{"猫","māo"},{"爸","bà"},{"妈","mā"},
        {"哥","gē"},{"姐","jiě"},{"弟","dì"},{"妹","mèi"},{"头","tóu"},
        {"眼","yǎn"},{"鼻","bí"},{"牙","yá"},{"心","xīn"},{"老师","lǎo shī"},
        {"学校","xué xiào"},{"书包","shū bāo"},{"朋友","péng you"},{"太阳","tài yáng"},{"星星","xīng xing"}
    };
    private static final String[][] POETRY_POOL = {
        {"床前明月光","疑是地上霜"},{"白日依山尽","黄河入海流"},
        {"春眠不觉晓","处处闻啼鸟"},{"锄禾日当午","汗滴禾下土"},
        {"鹅，鹅，鹅","曲项向天歌"},{"两个黄鹂鸣翠柳","一行白鹭上青天"},
        {"远看山有色","近听水无声"},{"小时不识月","呼作白玉盘"},
        {"举头望明月","低头思故乡"},{"谁知盘中餐","粒粒皆辛苦"},
        {"离离原上草","一岁一枯荣"},{"窗含西岭千秋雪","门泊东吴万里船"},
        {"儿童散学归来早","忙趁东风放纸鸢"},{"不解藏踪迹","浮萍一道开"}
    };
    private static final String[][] ENGLISH_POOL = {
        {"apple","苹果"},{"banana","香蕉"},{"orange","橙子"},{"dog","小狗"},
        {"cat","小猫"},{"bird","小鸟"},{"fish","鱼"},{"book","书"},
        {"pen","钢笔"},{"water","水"},{"milk","牛奶"},{"school","学校"},
        {"teacher","老师"},{"student","学生"},{"car","汽车"},{"bus","公交车"},
        {"red","红色"},{"blue","蓝色"},{"one","一"},{"two","二"},
        {"egg","鸡蛋"},{"cake","蛋糕"},{"hand","手"},{"nose","鼻子"}
    };

    public QuizEngine(android.content.Context ctx, Prefs prefs) {
        this.context = ctx;
        this.prefs = prefs;
        this.loader = new QuizLoader();
        loader.loadFrom(ctx);
    }

    /** 生成下一道题。 */
    public QuizQuestion nextQuestion() {
        loader.loadFrom(context);   // 每次出题前刷新题库（SMB/本地变化后自动生效）
        int types = prefs.quizTypes();
        // 组装可用题型列表
        List<Integer> active = new ArrayList<>();
        if ((types & Prefs.TYPE_MATH) != 0) active.add(Prefs.TYPE_MATH);
        if ((types & Prefs.TYPE_PINYIN) != 0) active.add(Prefs.TYPE_PINYIN);
        if ((types & Prefs.TYPE_POETRY) != 0) active.add(Prefs.TYPE_POETRY);
        if ((types & Prefs.TYPE_ENGLISH) != 0) active.add(Prefs.TYPE_ENGLISH);
        if ((types & Prefs.TYPE_CUSTOM) != 0) active.add(Prefs.TYPE_CUSTOM);
        if (active.isEmpty()) active.add(Prefs.TYPE_MATH);

        int type = active.get(rnd.nextInt(active.size()));
        switch (type) {
            case Prefs.TYPE_MATH: return mathQuestion();
            case Prefs.TYPE_PINYIN: return poolQuestion("pinyin", PINYIN_POOL, "认字", "拼音");
            case Prefs.TYPE_POETRY: return poolQuestion("poetry", POETRY_POOL, "古诗", "下一句");
            case Prefs.TYPE_ENGLISH: return poolQuestion("english", ENGLISH_POOL, "英语", "意思");
            case Prefs.TYPE_CUSTOM: return poolQuestion("custom", new String[0][2], "自定义", "答案");
            default: return mathQuestion();
        }
    }

    /** 数学口算自动生成。 */
    private QuizQuestion mathQuestion() {
        QuizQuestion q = new QuizQuestion();
        q.type = "math";
        q.subLabel = "口算";
        boolean isAdd = rnd.nextBoolean();
        int a, b, correct;
        if (isAdd) {
            a = 1 + rnd.nextInt(10);   // 1..10
            b = 1 + rnd.nextInt(10);   // 1..10
            correct = a + b;
            q.question = a + " + " + b + " = ?";
        } else {
            a = 3 + rnd.nextInt(18);   // 3..20
            b = 1 + rnd.nextInt(a - 1); // 1..a-1
            correct = a - b;
            q.question = a + " - " + b + " = ?";
        }
        fillNumericOptions(q, correct);
        return q;
    }

    /** 从池中选题：题干->答案，答案作为正确选项。 */
    private QuizQuestion poolQuestion(String section, String[][] builtin, String label, String askLabel) {
        List<String[]> pool = mergedPool(section, builtin);
        if (pool.isEmpty()) return mathQuestion(); // 兜底

        String[] pair = pool.get(rnd.nextInt(pool.size()));
        QuizQuestion q = new QuizQuestion();
        q.type = section;
        q.subLabel = label;
        q.question = pair[0] + "（选" + askLabel + "）";
        fillTextOptions(q, pool, pair[1]);
        return q;
    }

    /** 合并内置池与自定义池。 */
    private List<String[]> mergedPool(String section, String[][] builtin) {
        List<String[]> pool = new ArrayList<>();
        Collections.addAll(pool, builtin);
        List<String[]> custom = loader.getSection(section);
        if (custom != null) pool.addAll(custom);
        return pool;
    }

    /** 生成4个文本选项并打乱。 */
    private void fillTextOptions(QuizQuestion q, List<String[]> pool, String correct) {
        List<String> opts = new ArrayList<>();
        opts.add(correct);
        // 从池中取3个不同答案作干扰项
        List<String[]> candidates = new ArrayList<>(pool);
        Collections.shuffle(candidates, rnd);
        for (String[] p : candidates) {
            if (opts.size() >= 4) break;
            String ans = p[1];
            if (!opts.contains(ans)) opts.add(ans);
        }
        while (opts.size() < 4) opts.add("——");
        Collections.shuffle(opts, rnd);
        for (int i = 0; i < 4; i++) {
            q.options[i] = opts.get(i);
            if (opts.get(i).equals(correct)) q.correctIndex = i;
        }
    }

    /** 生成4个数字选项并打乱。 */
    private void fillNumericOptions(QuizQuestion q, int correct) {
        List<String> opts = new ArrayList<>();
        opts.add(String.valueOf(correct));
        while (opts.size() < 4) {
            int delta = 1 + rnd.nextInt(6);
            boolean minus = rnd.nextBoolean();
            int v = minus ? correct - delta : correct + delta;
            if (v < 0) v = correct + delta;
            String s = String.valueOf(v);
            if (!opts.contains(s)) opts.add(s);
        }
        Collections.shuffle(opts, rnd);
        for (int i = 0; i < 4; i++) {
            q.options[i] = opts.get(i);
            if (opts.get(i).equals(String.valueOf(correct))) q.correctIndex = i;
        }
    }
}
