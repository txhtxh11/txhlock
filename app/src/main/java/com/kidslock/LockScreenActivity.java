package com.kidslock;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 全屏答题锁屏页（v2.0 卡片式）。
 *
 * - 全屏铺满并保持常亮，拦截返回键
 * - 遥控器方向键选答案 + OK 确认；数字键 1-4 / 字母键 A-D 也可快捷作答
 * - 连续答对 requiredCorrect 题解锁；答错清零重来
 * - 按【菜单键】进入家长密码通道（应用内置数字键盘）
 * - 锁屏状态写入持久化，按 HOME 切走会被重新拉起（尽力防绕过）
 */
public class LockScreenActivity extends Activity {

    /** 锁屏页是否在前台。QuizService / Keeper 用它避免"重复拉起"打断答题和家长密码输入。 */
    public static volatile boolean sForeground = false;

    private Prefs prefs;
    private QuizEngine engine;
    private TextView tvQuestion, tvProgress, tvType, tvHint, tvFeedback;
    private LinearLayout dotsRow;
    private LinearLayout[] optViews = new LinearLayout[4];
    private TextView[] optTexts = new TextView[4];
    private TextView[] optLetters = new TextView[4];
    private final String[] OPT_LETTERS = {"A", "B", "C", "D"};

    private QuizQuestion current;
    private int progress = 0;
    private boolean unlocking = false;
    private boolean answering = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrap(base));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        // 显示之前先按"离开规则"判定一次：待机唤醒 / 开机补拉 / 被系统恢复栈时，
        // 这个页面可能比 BootReceiver、计时服务更早被拉起来，此时持久化里还是"锁定中"。
        // 在这里先判定，长关机与跨夜就能当场清零解锁，不会先闪一屏答题再消失。
        Away.resolve(prefs);
        // 只有持久化状态确实"锁定中"才允许显示。
        if (!prefs.isLocked()) {
            finish();
            return;
        }
        prefs.setLocked(true);
        engine = new QuizEngine(this, prefs);

        setupFullscreen();
        setContentView(R.layout.activity_lock);

        tvQuestion = (TextView) findViewById(R.id.tvQuestion);
        tvProgress = (TextView) findViewById(R.id.tvProgress);
        tvType = (TextView) findViewById(R.id.tvType);
        tvHint = (TextView) findViewById(R.id.tvHint);
        tvFeedback = (TextView) findViewById(R.id.tvFeedback);
        dotsRow = (LinearLayout) findViewById(R.id.dots);

        int[] ids = {R.id.opt0, R.id.opt1, R.id.opt2, R.id.opt3};
        int[] textIds = {R.id.optText0, R.id.optText1, R.id.optText2, R.id.optText3};
        int[] letterIds = {R.id.optLetter0, R.id.optLetter1, R.id.optLetter2, R.id.optLetter3};
        for (int i = 0; i < 4; i++) {
            optViews[i] = (LinearLayout) findViewById(ids[i]);
            optTexts[i] = (TextView) findViewById(textIds[i]);
            optLetters[i] = (TextView) findViewById(letterIds[i]);
            Ui.focusScale(optViews[i]);
            final int idx = i;
            optViews[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    onSelect(idx);
                }
            });
        }

        showNextQuestion();
        updateProgressUI();
        optViews[0].requestFocus();
        handler.post(stateWatcher);
    }

    /**
     * 状态巡检：每秒看一眼持久化里的锁屏标记。
     * 解决"页面已经在前台，之后才被清零解锁"的漏洞（待机唤醒时系统会先恢复锁屏页）。
     */
    private final Runnable stateWatcher = new Runnable() {
        @Override public void run() {
            if (!prefs.isLocked()) {
                finish();
                return;
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void setupFullscreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void showNextQuestion() {
        current = engine.nextQuestion();
        tvQuestion.setText(current.question);
        tvType.setText(current.subLabel == null ? "答题" : current.subLabel);
        applyQuestionSize(current.question);
        for (int i = 0; i < 4; i++) {
            optViews[i].setBackgroundResource(R.drawable.bg_option);
            optLetters[i].setBackgroundResource(R.drawable.bg_badge);
            optLetters[i].setTextColor(Ui.color(this, R.color.accent));
            optTexts[i].setTextColor(Ui.color(this, R.color.text_main));
            optTexts[i].setText(current.options[i]);
            optTexts[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, optionSize(current.options[i]));
            // 纯拉丁内容（拼音、英文单词）换用内置字体：系统 DroidSans 缺 ǎǐǒǔǚ，
            // 会回退到中文字体、显示成又宽又带间隙的样子。
            optTexts[i].setTypeface(hasCjk(current.options[i]) ? null : latinTypeface());
        }
        optViews[0].requestFocus();
        updateHint();
    }

    /** 题目越长字号越小，保证一屏放得下、不裁字（最多 3 行）。 */
    private void applyQuestionSize(String q) {
        int len = q == null ? 0 : q.length();
        float sp;
        if (len <= 12) sp = 46;
        else if (len <= 24) sp = 38;
        else if (len <= 38) sp = 32;
        else sp = 26;
        tvQuestion.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    private float optionSize(String s) {
        int len = s == null ? 0 : s.length();
        if (len <= 10) return 28;
        if (len <= 16) return 25;
        if (len <= 24) return 22;
        return 19;
    }

    /** 拉丁/拼音专用字体（assets/fonts/pinyin.ttf，DejaVu 子集），懒加载；失败则用系统字体。 */
    private Typeface latinFace;
    private boolean latinFaceTried;

    private Typeface latinTypeface() {
        if (!latinFaceTried) {
            latinFaceTried = true;
            try {
                latinFace = Typeface.createFromAsset(getAssets(), "fonts/pinyin.ttf");
            } catch (Throwable t) {
                android.util.Log.w("txhlock", "拼音字体加载失败，用系统字体", t);
                latinFace = null;
            }
        }
        return latinFace;
    }

    /** 是否含汉字/全角标点（含则不换拉丁字体，交给系统字体渲染）。 */
    private static boolean hasCjk(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x2E80 && c <= 0x9FFF) return true;
            if (c >= 0xF900 && c <= 0xFAFF) return true;
            if (c >= 0xFF00 && c <= 0xFFEF) return true;
        }
        return false;
    }

    private void updateProgressUI() {
        int need = Math.max(1, prefs.requiredCorrect());
        tvProgress.setText("连续答对 " + progress + " / " + need + " 题可解锁");
        rebuildDots(need);
        updateHint();
    }

    /** 顶部进度圆点：答对几个亮几个，对称排布。 */
    private void rebuildDots(int need) {
        if (dotsRow == null) return;
        int n = Math.min(need, 10);
        if (dotsRow.getChildCount() != n) {
            dotsRow.removeAllViews();
            for (int i = 0; i < n; i++) {
                View dot = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(11), dp(11));
                lp.leftMargin = dp(i == 0 ? 0 : 7);
                dot.setLayoutParams(lp);
                dot.setBackgroundResource(R.drawable.bg_dot);
                dotsRow.addView(dot);
            }
        }
        for (int i = 0; i < n; i++) {
            dotsRow.getChildAt(i).setBackgroundResource(
                    i < progress ? R.drawable.bg_dot_on : R.drawable.bg_dot);
        }
    }

    private void updateHint() {
        if (prefs.isDailyLocked()) {
            tvHint.setText("今日观看时间已用完 · 答对也不能解锁 · 请家长按【菜单键】输入密码");
            tvHint.setTextColor(Ui.color(this, R.color.warn));
        } else {
            tvHint.setText("方向键选择 · OK 确认 · 菜单键输入家长密码");
            tvHint.setTextColor(Ui.color(this, R.color.text_faint));
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 作答：高亮选中项 + 明确的"答对/答错 + 正确答案"反馈，短暂停留后再换题。 */
    private void onSelect(int index) {
        if (current == null || answering) return;
        answering = true;

        if (index == current.correctIndex) {
            progress++;
            markOption(index, true);
            tvFeedback.setText("✓ 答对了");
            tvFeedback.setTextColor(Ui.color(this, R.color.ok));
            updateProgressUI();
            if (progress >= prefs.requiredCorrect()) {
                if (prefs.isDailyLocked()) {
                    // 每日额度已用完：答题不能解锁，必须家长密码（当日豁免）
                    tvFeedback.setText("今日观看时间已用完，请家长输入密码");
                    tvFeedback.setTextColor(Ui.color(this, R.color.warn));
                    progress = 0;
                    updateProgressUI();
                    handler.postDelayed(new Runnable() {
                        @Override public void run() {
                            tvFeedback.setText("");
                            showNextQuestion();
                            answering = false;
                        }
                    }, 1600);
                    return;
                }
                tvFeedback.setText("✓ 答对了，正在解锁…");
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        answering = false;
                        unlock(false);
                    }
                }, 700);
                return;
            }
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    tvFeedback.setText("");
                    showNextQuestion();
                    answering = false;
                }
            }, 650);
        } else {
            progress = 0;
            markOption(index, false);
            // 同时把正确项标绿，孩子一眼知道答案
            optViews[current.correctIndex].setBackgroundResource(R.drawable.bg_option_correct);
            tvFeedback.setText("✗ 正确答案是 " + OPT_LETTERS[current.correctIndex] + "."
                    + current.options[current.correctIndex]);
            tvFeedback.setTextColor(Ui.color(this, R.color.danger));
            updateProgressUI();
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    tvFeedback.setText("");
                    showNextQuestion();
                    answering = false;
                }
            }, 1700);
        }
    }

    /** 给选中的选项上色：答对绿框、答错红框。 */
    private void markOption(int index, boolean correct) {
        optViews[index].setBackgroundResource(
                correct ? R.drawable.bg_option_correct : R.drawable.bg_option_wrong);
        optLetters[index].setBackgroundResource(
                correct ? R.drawable.bg_option_correct : R.drawable.bg_option_wrong);
        optTexts[index].setTextColor(Ui.color(this,
                correct ? R.color.ok : R.color.danger));
        optLetters[index].setTextColor(Ui.color(this,
                correct ? R.color.ok : R.color.danger));
    }

    /** 解锁。每日额度耗尽时，答题无法解锁，仅家长密码可解（当日豁免）。 */
    private void unlock(boolean viaParent) {
        if (prefs.isDailyLocked() && !viaParent) {
            toast("今日观看时间已用完，请家长输入密码");
            return;
        }
        unlocking = true;
        if (viaParent && prefs.isDailyLocked()) {
            // 家长用密码解开"每日上限"锁屏 = 当日豁免：今天不再因每日累计上限锁屏。
            prefs.setDailyExempt(true);
        }
        prefs.setDailyLocked(false);
        prefs.setLocked(false);
        long now = System.currentTimeMillis();
        prefs.setSessionStartMs(now);
        prefs.setLastAliveMs(now);
        // 解锁后立刻放开"锁屏在前台"标记：QuizService 的 lockRestorer、无障碍服务的
        // 抢前台逻辑都以它为闸门，晚放一会儿会在切回原应用的路上再抢一次前台（可见卡顿）。
        sForeground = false;
        finish();
        // 不要退出动画：锁屏页是独立任务栈，淡出动画期间会先露出任务切换的底色，
        // 老电视上表现为"按完 OK 卡一下才回到原界面"。直接切换更干净。
        overridePendingTransition(0, 0);
    }

    // ---- 按键处理 ----
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            toast("看完题目才能解锁哦");
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showPinPad();
            return true;
        }
        // 快捷键作答：数字键 1-4 或字母键 A-D（有实体键/键盘的设备可用）
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_4) {
            onSelect(keyCode - KeyEvent.KEYCODE_1);
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_D) {
            onSelect(keyCode - KeyEvent.KEYCODE_A);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 返回键第二道闸门。
     * Activity.onKeyUp() 在某些 ROM 上仍会补一次 onBackPressed()，只拦截 onKeyDown
     * 不够保险；这里直接把 onBackPressed 也堵死，避免孩子按返回键退出锁屏页。
     */
    @Override public void onBackPressed() {
        toast("看完题目才能解锁哦");
    }

    /** 家长密码：应用内置数字键盘（可见输入框 + 数字键），遥控器即可输入。 */
    private void showPinPad() {
        LinearLayout panel = Ui.dialogPanel(this);
        TextView title = Ui.text(this, "家长密码", 24, Ui.color(this, R.color.text_main), true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        final Dialog dlg = Ui.dialog(this, panel, 520);
        final PinPad[] padRef = new PinPad[1];
        PinPad pad = new PinPad(this, "请输入 " + prefs.pin().length() + " 位家长密码",
                prefs.pin().length(), new PinPad.Listener() {
            @Override public void onEntered(String s) {
                if (s.equals(prefs.pin())) {
                    dlg.dismiss();
                    toast("密码正确，解锁");
                    unlock(true);
                } else {
                    padRef[0].showError("密码错误，请重试");
                }
            }

            @Override public void onCancelled() {
                dlg.dismiss();
            }
        });
        padRef[0] = pad;
        panel.addView(pad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dlg.setCancelable(true);
        dlg.show();
    }

    // ---- 防绕过：被切走(如按HOME)后重新拉起 ----
    @Override protected void onResume() {
        super.onResume();
        // 状态可能已在别处被改掉（例如离开判定为长关机/跨夜而清零解锁），回来时再判定一次
        Away.resolve(prefs);
        if (!prefs.isLocked()) {
            finish();
            return;
        }
        sForeground = true;
        updateHint();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onPause() {
        sForeground = false;
        super.onPause();
    }

    @Override protected void onStop() {
        super.onStop();
        if (!unlocking && prefs.isLocked()) {
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!unlocking && prefs.isLocked() && !sForeground) {
                        Keeper.launchLock(LockScreenActivity.this);
                    }
                }
            }, 300);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
