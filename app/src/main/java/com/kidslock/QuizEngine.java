package com.kidslock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 出题核心：根据启用的题型和设定年级生成四选一题目。
 *
 * 两类来源：
 *   1. 口算 —— 应用按年级自动生成（一年级 10 以内加减 → 九年级根式/有理数），无需题库
 *   2. 学科题 —— 从题库按「科目@g年级」取题，只出该年级及以下的题，不超纲
 *
 * 题库题目可自带干扰项（题干|答案|干扰1;干扰2;干扰3），
 * 没带干扰项时才退回"从同段其他答案里随机取"的旧策略。
 */
public class QuizEngine {
    private final Prefs prefs;
    private final QuizLoader loader;
    private final android.content.Context context;
    private final Random rnd = new Random();

    // 内置题库（作为题库缺失时的兜底）：String[]{题干, 答案}
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
        // 家长点"重新加载题库"时会重建引擎，强制重读
        loader.loadFrom(ctx, true);
    }

    /** 生成下一道题。 */
    public QuizQuestion nextQuestion() {
        // 带节流；本地/SMB 题库有新增后能自动生效
        loader.loadFrom(context, false);
        int types = prefs.quizTypes();
        int grade = prefs.grade();

        List<Integer> active = new ArrayList<>();
        if ((types & Prefs.TYPE_MATH) != 0) active.add(Prefs.TYPE_MATH);
        if ((types & Prefs.TYPE_PINYIN) != 0) active.add(Prefs.TYPE_PINYIN);
        if ((types & Prefs.TYPE_POETRY) != 0) active.add(Prefs.TYPE_POETRY);
        if ((types & Prefs.TYPE_ENGLISH) != 0) active.add(Prefs.TYPE_ENGLISH);
        if ((types & Prefs.TYPE_CUSTOM) != 0) active.add(Prefs.TYPE_CUSTOM);
        if ((types & Prefs.TYPE_MATH2) != 0) active.add(Prefs.TYPE_MATH2);
        if ((types & Prefs.TYPE_CHINESE) != 0) active.add(Prefs.TYPE_CHINESE);
        if ((types & Prefs.TYPE_PHYSICS) != 0) active.add(Prefs.TYPE_PHYSICS);
        if ((types & Prefs.TYPE_CHEMISTRY) != 0) active.add(Prefs.TYPE_CHEMISTRY);
        if ((types & Prefs.TYPE_BIOLOGY) != 0) active.add(Prefs.TYPE_BIOLOGY);
        if ((types & Prefs.TYPE_HISTORY) != 0) active.add(Prefs.TYPE_HISTORY);
        if ((types & Prefs.TYPE_GEOGRAPHY) != 0) active.add(Prefs.TYPE_GEOGRAPHY);
        if ((types & Prefs.TYPE_POLITICS) != 0) active.add(Prefs.TYPE_POLITICS);
        if (active.isEmpty()) active.add(Prefs.TYPE_MATH);

        // 某些学科在当前年级没有题（如小学开了物理），抽到就换一个，最多试几轮
        for (int attempt = 0; attempt < 8; attempt++) {
            int type = active.get(rnd.nextInt(active.size()));
            QuizQuestion q = build(type, grade);
            if (q != null) return q;
        }
        return mathQuestion(grade);   // 兜底：口算永远有题
    }

    private QuizQuestion build(int type, int grade) {
        switch (type) {
            case Prefs.TYPE_MATH:      return mathQuestion(grade);
            case Prefs.TYPE_PINYIN:    return poolQuestion("pinyin", PINYIN_POOL, "认字", "拼音");
            case Prefs.TYPE_POETRY:    return poolQuestion("poetry", POETRY_POOL, "古诗", "下一句");
            case Prefs.TYPE_CUSTOM:    return poolQuestion("custom", new String[0][0], "自定义", "答案");
            case Prefs.TYPE_ENGLISH:   return subjectQuestion("english", ENGLISH_POOL, "英语", "意思", grade);
            case Prefs.TYPE_MATH2:     return subjectQuestion("math2", null, "数学", "答案", grade);
            case Prefs.TYPE_CHINESE:   return subjectQuestion("chinese", null, "语文", "答案", grade);
            case Prefs.TYPE_PHYSICS:   return subjectQuestion("physics", null, "物理", "答案", grade);
            case Prefs.TYPE_CHEMISTRY: return subjectQuestion("chemistry", null, "化学", "答案", grade);
            case Prefs.TYPE_BIOLOGY:   return subjectQuestion("biology", null, "生物", "答案", grade);
            case Prefs.TYPE_HISTORY:   return subjectQuestion("history", null, "历史", "答案", grade);
            case Prefs.TYPE_GEOGRAPHY: return subjectQuestion("geography", null, "地理", "答案", grade);
            case Prefs.TYPE_POLITICS:  return subjectQuestion("politics", null, "政治", "答案", grade);
            default:                   return mathQuestion(grade);
        }
    }

    // ==================== 口算：按年级自动生成 ====================

    /** 数学口算自动生成（无需题库），1~6 年级为小学算术，7~9 年级为初中有理数/根式。 */
    private QuizQuestion mathQuestion(int grade) {
        QuizQuestion q = new QuizQuestion();
        q.type = "math";
        q.subLabel = "口算·" + Prefs.gradeName(grade);
        Random r = rnd;
        int a = 1, b = 1, correct = 1;
        String op = "+";
        String expr;   // 题干表达式（负数要加括号，乘方用上标）

        switch (grade) {
            case 1: // 一年级：10 以内加减
                if (r.nextBoolean()) { a = 1 + r.nextInt(9); b = 1 + r.nextInt(10 - a); op = "+"; correct = a + b; }
                else { a = 2 + r.nextInt(9); b = 1 + r.nextInt(a - 1); op = "-"; correct = a - b; }
                expr = a + " " + op + " " + b;
                break;
            case 2: // 二年级：20 以内加减 + 表内乘法
                switch (r.nextInt(3)) {
                    case 0: a = 1 + r.nextInt(19); b = 1 + r.nextInt(20 - a); op = "+"; correct = a + b; break;
                    case 1: a = 2 + r.nextInt(19); b = 1 + r.nextInt(a - 1); op = "-"; correct = a - b; break;
                    default: a = 2 + r.nextInt(8); b = 2 + r.nextInt(8); op = "×"; correct = a * b; break;
                }
                expr = a + " " + op + " " + b;
                break;
            case 3: // 三年级：100 以内加减、两三位×一位、整除
                switch (r.nextInt(4)) {
                    case 0: a = 10 + r.nextInt(50); b = 10 + r.nextInt(100 - a); op = "+"; correct = a + b; break;
                    case 1: a = 10 + r.nextInt(90); b = 1 + r.nextInt(a - 1); op = "-"; correct = a - b; break;
                    case 2: a = 11 + r.nextInt(9); b = 2 + r.nextInt(8); op = "×"; correct = a * b; break;
                    default: b = 2 + r.nextInt(8); int q3 = 2 + r.nextInt(8); a = b * q3; op = "÷"; correct = q3; break;
                }
                expr = a + " " + op + " " + b;
                break;
            case 4: // 四年级：千以内加减、两位数乘除
                switch (r.nextInt(4)) {
                    case 0: a = 100 + r.nextInt(400); b = 100 + r.nextInt(1000 - a); op = "+"; correct = a + b; break;
                    case 1: a = 200 + r.nextInt(800); b = 100 + r.nextInt(a - 100); op = "-"; correct = a - b; break;
                    case 2: a = 11 + r.nextInt(9); b = 11 + r.nextInt(9); op = "×"; correct = a * b; break;
                    default: b = 11 + r.nextInt(9); int q4 = 11 + r.nextInt(9); a = b * q4; op = "÷"; correct = q4; break;
                }
                expr = a + " " + op + " " + b;
                break;
            case 5: // 五年级：万以内加减、两位数乘除
                switch (r.nextInt(4)) {
                    case 0: a = 1000 + r.nextInt(4000); b = 1000 + r.nextInt(10000 - a); op = "+"; correct = a + b; break;
                    case 1: a = 2000 + r.nextInt(8000); b = 1000 + r.nextInt(a - 1000); op = "-"; correct = a - b; break;
                    case 2: a = 11 + r.nextInt(19); b = 11 + r.nextInt(19); op = "×"; correct = a * b; break;
                    default: b = 11 + r.nextInt(19); int q5 = 11 + r.nextInt(19); a = b * q5; op = "÷"; correct = q5; break;
                }
                expr = a + " " + op + " " + b;
                break;
            case 6: // 六年级：大数四则、两位数乘除
                switch (r.nextInt(4)) {
                    case 0: a = 1000 + r.nextInt(4000); b = 1000 + r.nextInt(10000 - a); op = "+"; correct = a + b; break;
                    case 1: a = 2000 + r.nextInt(8000); b = 1000 + r.nextInt(a - 1000); op = "-"; correct = a - b; break;
                    case 2: a = 11 + r.nextInt(30); b = 11 + r.nextInt(30); op = "×"; correct = a * b; break;
                    default: b = 11 + r.nextInt(30); int q6 = 11 + r.nextInt(30); a = b * q6; op = "÷"; correct = q6; break;
                }
                expr = a + " " + op + " " + b;
                break;
            case 7: // 七年级：有理数四则（含负数）、乘方
                switch (r.nextInt(4)) {
                    case 0:
                        a = signed(1, 15); b = signed(1, 15);
                        if (a == 0) a = 5; if (b == 0) b = -3;
                        op = "+"; correct = a + b;
                        expr = wrap(a) + " + " + wrap(b);
                        break;
                    case 1:
                        a = signed(1, 15); b = signed(1, 15);
                        if (a == 0) a = 5; if (b == 0) b = -3;
                        op = "-"; correct = a - b;
                        expr = wrap(a) + " - " + wrap(b);
                        break;
                    case 2:
                        a = signed(2, 9); b = signed(2, 9);
                        if (a == 0) a = 3; if (b == 0) b = -4;
                        op = "×"; correct = a * b;
                        expr = wrap(a) + " × " + wrap(b);
                        break;
                    default:
                        b = signed(2, 9); int qq = 2 + r.nextInt(6);
                        if (b == 0) b = -3;
                        a = b * qq; op = "÷"; correct = qq;
                        expr = wrap(a) + " ÷ " + wrap(b);
                        break;
                }
                break;
            case 8: // 八年级：平方根、立方根、乘方、有理数混合
                switch (r.nextInt(4)) {
                    case 0:
                        a = 2 + r.nextInt(11); correct = a; op = "√";
                        expr = "√" + (a * a);
                        break;
                    case 1:
                        a = 2 + r.nextInt(6); correct = a; op = "∛";
                        expr = "∛" + (a * a * a);
                        break;
                    case 2:
                        a = 2 + r.nextInt(4); b = 2 + r.nextInt(2);
                        correct = (int) Math.pow(a, b); op = "^";
                        expr = wrap(a) + sup(b);
                        break;
                    default:
                        a = signed(3, 9); b = signed(2, 5);
                        if (a == 0) a = -4; if (b == 0) b = 3;
                        correct = a * b; op = "×";
                        expr = wrap(a) + " × " + wrap(b);
                        break;
                }
                break;
            default: // 九年级：二次根式（完全平方）、立方根、乘方混合
                switch (r.nextInt(4)) {
                    case 0:
                        a = 5 + r.nextInt(11); correct = a; op = "√";
                        expr = "√" + (a * a);
                        break;
                    case 1:
                        a = 3 + r.nextInt(5); correct = a; op = "∛";
                        expr = "∛" + (a * a * a);
                        break;
                    case 2:
                        a = 2 + r.nextInt(3); b = 3;
                        correct = (int) Math.pow(a, b); op = "^";
                        expr = wrap(a) + sup(b);
                        break;
                    default:
                        a = signed(2, 9); b = 2 + r.nextInt(3);
                        correct = (int) Math.pow(a, b); op = "^";
                        expr = wrap(a) + sup(b);
                        break;
                }
                break;
        }

        q.question = expr + " = ?";
        fillNumericOptions(q, correct);
        return q;
    }

    /** -n ~ n 之间的随机整数（不含 0）。 */
    private int signed(int lo, int hi) {
        int v = lo + rnd.nextInt(hi - lo + 1);
        return rnd.nextBoolean() ? v : -v;
    }

    /** 负数加括号，避免 "3 × -4" 这种歧义写法。 */
    private static String wrap(int v) {
        return v < 0 ? "(" + v + ")" : String.valueOf(v);
    }

    /** 指数转上标。 */
    private static String sup(int n) {
        switch (n) {
            case 2: return "²";
            case 3: return "³";
            case 4: return "⁴";
            case 5: return "⁵";
            default: return "^" + n;
        }
    }

    // ==================== 学科题 ====================

    /**
     * 按科目 + 年级出题。
     *
     * @param builtin 可选的内置兜底池（只有英语用到）
     * @return null 表示该科目在当前年级没有可出的题，调用方应换其他题型
     */
    private QuizQuestion subjectQuestion(String subject, String[][] builtin, String label, String askLabel, int grade) {
        List<String[]> pool = new ArrayList<>();
        List<String[]> graded = loader.getForGrade(subject, grade);
        if (graded != null) pool.addAll(graded);
        if (builtin != null) {
            for (int i = 0; i < builtin.length; i++) {
                if (!hasQuestion(pool, builtin[i][0])) {
                    pool.add(new String[]{builtin[i][0], builtin[i][1], ""});
                }
            }
        }
        if (pool.isEmpty()) return null;

        String[] item = pool.get(rnd.nextInt(pool.size()));
        QuizQuestion q = new QuizQuestion();
        q.type = subject;
        q.subLabel = label + "·" + stageName(grade);
        q.question = item[0] + "（选" + askLabel + "）";
        fillOptions(q, pool, item);
        return q;
    }

    /** 学段名：小学 / 初中。 */
    private static String stageName(int grade) {
        return grade <= 6 ? "小学" : "初中";
    }

    private static boolean hasQuestion(List<String[]> pool, String q) {
        for (int i = 0; i < pool.size(); i++) {
            if (pool.get(i)[0].equals(q)) return true;
        }
        return false;
    }

    /** 固定段落（认字/古诗/自定义）：内置池 + 题库同段落合并，不按年级。 */
    private QuizQuestion poolQuestion(String section, String[][] builtin, String label, String askLabel) {
        List<String[]> pool = new ArrayList<>();
        for (int i = 0; i < builtin.length; i++) {
            pool.add(new String[]{builtin[i][0], builtin[i][1], ""});
        }
        List<String[]> custom = loader.getSection(section);
        if (custom != null) {
            // 题库里的同名题覆盖内置题（家长可修正答案）
            for (int i = 0; i < custom.size(); i++) {
                String[] c = custom.get(i);
                int hit = -1;
                for (int j = 0; j < pool.size(); j++) {
                    if (pool.get(j)[0].equals(c[0])) { hit = j; break; }
                }
                if (hit >= 0) pool.set(hit, c); else pool.add(c);
            }
        }
        if (pool.isEmpty()) return mathQuestion(prefs.grade());

        String[] item = pool.get(rnd.nextInt(pool.size()));
        QuizQuestion q = new QuizQuestion();
        q.type = section;
        q.subLabel = label;
        q.question = item[0] + "（选" + askLabel + "）";
        fillOptions(q, pool, item);
        return q;
    }

    // ==================== 选项生成 ====================

    /**
     * 生成 4 个文本选项并打乱。
     * 优先用题目自带的干扰项（题库第三列）；不足 3 个才从同段其他答案里补。
     */
    private void fillOptions(QuizQuestion q, List<String[]> pool, String[] item) {
        String correct = item[QuizLoader.IDX_A];
        String dstr = item.length > QuizLoader.IDX_D ? item[QuizLoader.IDX_D] : "";

        List<String> opts = new ArrayList<>();
        opts.add(correct);

        // 1) 自带干扰项
        String[] ds = QuizLoader.splitDistractors(dstr);
        for (int i = 0; i < ds.length && opts.size() < 4; i++) {
            if (!opts.contains(ds[i])) opts.add(ds[i]);
        }
        // 2) 不够就从同池其他答案补（旧行为）
        if (opts.size() < 4) {
            List<String[]> cand = new ArrayList<>(pool);
            Collections.shuffle(cand, rnd);
            for (int i = 0; i < cand.size() && opts.size() < 4; i++) {
                String ans = cand.get(i)[QuizLoader.IDX_A];
                if (!opts.contains(ans)) opts.add(ans);
            }
        }
        while (opts.size() < 4) opts.add("——");

        Collections.shuffle(opts, rnd);
        for (int i = 0; i < 4; i++) {
            q.options[i] = opts.get(i);
            if (opts.get(i).equals(correct)) q.correctIndex = i;
        }
    }

    /** 生成 4 个数字选项并打乱。 */
    private void fillNumericOptions(QuizQuestion q, int correct) {
        List<String> opts = new ArrayList<>();
        opts.add(String.valueOf(correct));
        int guard = 0;
        while (opts.size() < 4 && guard++ < 200) {
            int delta = 1 + rnd.nextInt(6);
            boolean minus = rnd.nextBoolean();
            int v = minus ? correct - delta : correct + delta;
            if (v < 0) v = correct + delta;
            String s = String.valueOf(v);
            if (!opts.contains(s)) opts.add(s);
        }
        while (opts.size() < 4) opts.add("——");

        Collections.shuffle(opts, rnd);
        for (int i = 0; i < 4; i++) {
            q.options[i] = opts.get(i);
            if (opts.get(i).equals(String.valueOf(correct))) q.correctIndex = i;
        }
    }

    /** 题库统计（设置页显示用）。 */
    public int questionCount() { return loader.totalQuestions(); }
    public int sectionCount() { return loader.totalSections(); }
}
