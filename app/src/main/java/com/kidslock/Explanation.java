package com.kidslock;

/** 错题解析：题库没有逐题解析字段，这里按题型生成知识点级解析。 */
public final class Explanation {
    private Explanation() {}

    public static String make(QuizQuestion q) {
        if (q == null || q.options == null || q.correctIndex < 0
                || q.correctIndex >= q.options.length) return "";
        String ans = q.options[q.correctIndex];
        if (ans == null || ans.length() == 0) return "";
        String type = q.type == null ? "" : q.type;
        String tip;
        if ("math".equals(type) || "math2".equals(type)) {
            tip = "先看清运算符号和单位，再算一遍；容易错在进位、借位或单位换算。";
        } else if ("pinyin".equals(type)) {
            tip = "注意声母、韵母和声调，跟着读一遍。";
        } else if ("poetry".equals(type)) {
            tip = "这是必背诗句，建议和上一句连起来背。";
        } else if ("english".equals(type)) {
            tip = "记住这个单词的意思和拼写。";
        } else if ("chinese".equals(type)) {
            tip = "注意字形、字音和词义的区别。";
        } else if ("physics".equals(type) || "chemistry".equals(type) || "biology".equals(type)) {
            tip = "回到题目涉及的概念或公式，再记一遍。";
        } else if ("history".equals(type) || "geography".equals(type) || "politics".equals(type)) {
            tip = "抓住题干里的时间、地点或关键词。";
        } else {
            tip = "把这道题的知识点再记一遍。";
        }
        return "解析：正确答案是「" + ans + "」。" + tip;
    }
}
