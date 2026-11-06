package com.kidslock;

/** 一道四选一题目。 */
public class QuizQuestion {
    public String question;          // 题干（如：苹果 或 3+5=?）
    public String[] options = new String[4]; // 4 个选项
    public int correctIndex;         // 正确选项下标 0..3
    public String type;              // math / pinyin / poetry / english / custom
    public String subLabel;          // 题目标签（用于显示：口算/认字/古诗/英语）
    public String explanation;       // 错题解析
    public String key;               // 稳定标识（题型 + 题干 + 答案）

    public QuizQuestion() {}
}
