package com.kidslock;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 答题面板控制器（v4.0 经典版）。
 *
 * 同一个面板既能放进 Activity（LockScreenActivity），也能放进系统悬浮层
 * （LockOverlay）—— 两种宿主共用一套出题、判题、解锁逻辑。
 *
 * v4 的答题界面回到 1.9.1：四个答案排成一行，遥控器只需要左右移动；
 * 选中项蓝底 + 白字 + 白框，答对绿框、答错红框。
 */
public class QuizPanel {

    public interface Host {
        void onUnlocked(boolean viaParent);
        void onClosed();
        Context context();
    }

    private final Host host;
    private final Context ctx;
    private final Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // 锁屏页统一用 OK 键：长按呼出家长密码；短按 OK 仍然是选答案
    private boolean okHolding = false;
    private boolean okPinFired = false;
    private TextView tvUnlockHold;
    private long unlockHoldStartMs;
    private int unlockHoldTotalMs;
    private final Runnable okPinHold = new Runnable() {
        @Override public void run() {
            okPinFired = true;
            hideUnlockHold();
            if (!pinOpen) showParentPin();
        }
    };
    // 长按解锁：最后 20% 才显示读秒（10 秒 → 最后 2 秒）
    private final Runnable unlockShow = new Runnable() {
        @Override public void run() {
            if (!okHolding || tvUnlockHold == null) return;
            tvUnlockHold.setVisibility(View.VISIBLE);
            unlockTick.run();
        }
    };
    private final Runnable unlockTick = new Runnable() {
        @Override public void run() {
            if (!okHolding || tvUnlockHold == null) return;
            long left = unlockHoldTotalMs - (System.currentTimeMillis() - unlockHoldStartMs);
            int sec = (int) Math.ceil(Math.max(0, left) / 1000.0);
            if (sec < 1) sec = 1;
            tvUnlockHold.setText("继续按住 " + sec + " 秒…");
            if (left > 0) handler.postDelayed(this, 250);
        }
    };

    private QuizEngine engine;
    private View root;
    private TextView tvQuestion, tvProgress, tvHint, tvFeedback, tvExplain;
    private final TextView[] optTexts = new TextView[4];
    private final String[] OPT_LETTERS = {"A", "B", "C", "D"};

    private QuizQuestion current;
    private int progress = 0;
    private boolean unlocking = false;
    private boolean answering = false;
    private boolean started = false;

    private FrameLayout pinLayer;
    private boolean pinOpen = false;

    private final Runnable stateWatcher = new Runnable() {
        @Override public void run() {
            if (prefs == null) return;
            if (!prefs.isLocked()) {
                host.onClosed();
                return;
            }
            handler.postDelayed(this, 1000);
        }
    };

    public QuizPanel(Host host) {
        this.host = host;
        this.ctx = host.context();
        this.prefs = new Prefs(ctx);
    }

    public View createView() {
        if (root != null) return root;
        // 注意：锁屏界面**完全用代码构建**，不再 inflate activity_lock.xml。
        // 原因：部分 MIUI Android 4.3 的二进制 XML 解析器会在这个布局上抛
        // StringBlock index out of bounds（AOSP 4.3 正常），用代码建视图可以绕开。
        KeyRoot r = new KeyRoot(ctx);
        r.setBackgroundColor(Ui.color(ctx, R.color.bg_dark));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        int padH = Ui.dp(ctx, 48);
        col.setPadding(padH, Ui.dp(ctx, 34), padH, Ui.dp(ctx, 26));
        r.addView(col, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView title = Ui.text(ctx, "答题解锁才能继续看电视", 32,
                Ui.color(ctx, R.color.text_main), true);
        col.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        tvProgress = Ui.text(ctx, "", 24, Ui.color(ctx, R.color.accent), false);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Ui.dp(ctx, 10);
        col.addView(tvProgress, plp);

        LinearLayout qBox = new LinearLayout(ctx);
        qBox.setOrientation(LinearLayout.VERTICAL);
        qBox.setGravity(Gravity.CENTER);
        col.addView(qBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        tvQuestion = new TextView(ctx);
        tvQuestion.setTextColor(Ui.color(ctx, R.color.text_main));
        tvQuestion.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        tvQuestion.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tvQuestion.setGravity(Gravity.CENTER);
        tvQuestion.setIncludeFontPadding(false);
        tvQuestion.setMaxLines(3);
        tvQuestion.setLineSpacing(Ui.dp(ctx, 6), 1f);
        qBox.addView(tvQuestion, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        tvFeedback = new TextView(ctx);
        tvFeedback.setTextColor(Ui.color(ctx, R.color.ok));
        tvFeedback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        tvFeedback.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tvFeedback.setGravity(Gravity.CENTER);
        tvFeedback.setIncludeFontPadding(false);
        tvFeedback.setMaxLines(2);
        tvFeedback.setMinHeight(Ui.dp(ctx, 40));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.bottomMargin = Ui.dp(ctx, 10);
        col.addView(tvFeedback, flp);

        tvExplain = Ui.text(ctx, "", 18, Ui.color(ctx, R.color.text_dim), false);
        tvExplain.setGravity(Gravity.CENTER);
        tvExplain.setMaxLines(4);
        tvExplain.setLineSpacing(Ui.dp(ctx, 3), 1f);
        tvExplain.setVisibility(View.GONE);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        elp.bottomMargin = Ui.dp(ctx, 8);
        col.addView(tvExplain, elp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        col.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 120)));

        for (int i = 0; i < 4; i++) {
            final TextView o = new TextView(ctx);
            o.setTextColor(Ui.color(ctx, R.color.text_main));
            o.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            o.setGravity(Gravity.CENTER);
            o.setIncludeFontPadding(false);
            o.setMaxLines(2);
            o.setBackgroundResource(R.drawable.bg_option);
            o.setPadding(Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8), 0);
            o.setClickable(true);
            o.setFocusable(true);
            final int idx = i;
            o.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onSelect(idx); }
            });
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            olp.rightMargin = Ui.dp(ctx, 10);
            row.addView(o, olp);
            optTexts[i] = o;
        }

        tvHint = Ui.text(ctx, "", 20, Ui.color(ctx, R.color.text_dim), false);
        tvHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(ctx, 18);
        col.addView(tvHint, hlp);

        tvUnlockHold = Ui.text(ctx, "", 24, Ui.color(ctx, R.color.accent), true);
        tvUnlockHold.setGravity(Gravity.CENTER);
        tvUnlockHold.setVisibility(View.GONE);
        FrameLayout.LayoutParams ulp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        ulp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        ulp.bottomMargin = Ui.dp(ctx, 76);
        r.addView(tvUnlockHold, ulp);

        root = r;
        r.setFocusTarget(optTexts[0]);
        r.setKeyHandler(new KeyRoot.KeyHandler() {
            @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
                return QuizPanel.this.onKeyDown(keyCode, event);
            }
            @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
                return QuizPanel.this.onKeyUp(keyCode, event);
            }
        });
        return root;
    }

    public void start() {
        if (started) return;
        started = true;
        engine = new QuizEngine(ctx, prefs);
        showNextQuestion();
        updateProgressUI();
        if (optTexts[0] != null) optTexts[0].requestFocus();
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (optTexts[0] != null) optTexts[0].requestFocus();
            }
        }, 250);
        handler.postDelayed(stateWatcher, 1000);
    }

    public void stop() {
        started = false;
        handler.removeCallbacksAndMessages(null);
    }

    public boolean isUnlocking() {
        return unlocking;
    }

    public View getView() {
        return root;
    }

    // ---------------- 出题与判题 ----------------

    private void showNextQuestion() {
        current = engine.nextQuestion();
        tvQuestion.setText(current.question);
        if (tvExplain != null) {
            tvExplain.setVisibility(View.GONE);
            tvExplain.setText("");
        }
        applyQuestionSize(current.question);
        // 四个答案用同一个字号（取最长的那条），整行看起来才整齐
        int maxLen = 0;
        for (int i = 0; i < 4; i++) maxLen = Math.max(maxLen, current.options[i].length() + 2);
        float rowSize = optionSize(maxLen);
        for (int i = 0; i < 4; i++) {
            optTexts[i].setBackgroundResource(R.drawable.bg_option);
            optTexts[i].setTextColor(Ui.color(ctx, R.color.text_main));
            optTexts[i].setText(OPT_LETTERS[i] + "." + current.options[i]);
            optTexts[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, rowSize);
            optTexts[i].setTypeface(hasCjk(current.options[i]) ? null : latinTypeface());
        }
        if (optTexts[0] != null) optTexts[0].requestFocus();
        updateHint();
    }

    private void applyQuestionSize(String q) {
        int len = q == null ? 0 : q.length();
        float sp;
        if (len <= 12) sp = 46;
        else if (len <= 24) sp = 40;
        else if (len <= 38) sp = 34;
        else sp = 28;
        tvQuestion.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    /** 一行四个格子，字越短越大；长答案最多两行。 */
    private float optionSize(int totalLen) {
        int len = totalLen;
        if (len <= 7) return 30;
        if (len <= 9) return 22;
        if (len <= 12) return 19;
        return 17;
    }

    private Typeface latinFace;
    private boolean latinFaceTried;

    private Typeface latinTypeface() {
        if (!latinFaceTried) {
            latinFaceTried = true;
            try {
                latinFace = Typeface.createFromAsset(ctx.getAssets(), "fonts/pinyin.ttf");
            } catch (Throwable t) {
                latinFace = null;
            }
        }
        return latinFace;
    }

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
        updateHint();
    }

    private void updateHint() {
        if (tvHint == null) return;
        if (prefs.isDailyLocked()) {
            tvHint.setText("今日观看时间已用完 · 答对也不能解锁 · 请家长长按 OK 键输入密码");
            tvHint.setTextColor(Ui.color(ctx, R.color.warn));
        } else {
            tvHint.setText("用方向键选答案，短按 OK 确认 · 长按 OK 键 " + prefs.unlockHoldSeconds() + " 秒输入家长密码");
            tvHint.setTextColor(Ui.color(ctx, R.color.text_dim));
        }
    }

    private void onSelect(int index) {
        if (current == null || answering) return;
        answering = true;
        // 数字键直接作答时，把焦点也移到所选答案，避免两个选项同时高亮
        if (optTexts[index] != null) optTexts[index].requestFocus();
        if (index == current.correctIndex) {
            progress++;
            engine.onAnswered(current, true);
            markOption(index, true);
            tvFeedback.setText("✓ 答对了");
            tvFeedback.setTextColor(Ui.color(ctx, R.color.ok));
            updateProgressUI();
            if (progress >= prefs.requiredCorrect()) {
                if (prefs.isDailyLocked()) {
                    tvFeedback.setText("今日观看时间已用完，请家长输入密码");
                    tvFeedback.setTextColor(Ui.color(ctx, R.color.warn));
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
                }, 600);
                return;
            }
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    tvFeedback.setText("");
                    showNextQuestion();
                    answering = false;
                }
            }, 600);
        } else {
            progress = 0;
            engine.onAnswered(current, false);
            markOption(index, false);
            optTexts[current.correctIndex].setBackgroundResource(R.drawable.bg_option_correct);
            tvFeedback.setText("✗ 正确答案是 " + OPT_LETTERS[current.correctIndex] + "."
                    + current.options[current.correctIndex]);
            tvFeedback.setTextColor(Ui.color(ctx, R.color.danger));
            if (current.explanation != null && current.explanation.length() > 0) {
                tvExplain.setText(current.explanation);
                tvExplain.setVisibility(View.VISIBLE);
            }
            updateProgressUI();
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    tvFeedback.setText("");
                    tvExplain.setVisibility(View.GONE);
                    showNextQuestion();
                    answering = false;
                }
            }, 3000);
        }
    }

    private void markOption(int index, boolean correct) {
        optTexts[index].setBackgroundResource(
                correct ? R.drawable.bg_option_correct : R.drawable.bg_option_wrong);
    }

    private void unlock(boolean viaParent) {
        if (prefs.isDailyLocked() && !viaParent) return;
        unlocking = true;
        if (viaParent && prefs.isDailyLocked()) prefs.setDailyExempt(true);
        prefs.setDailyLocked(false);
        prefs.setLocked(false);
        long now = System.currentTimeMillis();
        prefs.setSessionStartMs(now);
        prefs.setLastAliveMs(now);
        handler.removeCallbacksAndMessages(null);
        host.onUnlocked(viaParent);
    }

    // ---------------- 按键 ----------------

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (pinOpen) return false;
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.getRepeatCount() == 0) {
                okHolding = true;
                okPinFired = false;
                handler.removeCallbacks(okPinHold);
                handler.removeCallbacks(unlockShow);
                handler.removeCallbacks(unlockTick);
                if (tvUnlockHold != null) tvUnlockHold.setVisibility(View.GONE);
                unlockHoldTotalMs = Math.max(3, Math.min(20, prefs.unlockHoldSeconds())) * 1000;
                unlockHoldStartMs = System.currentTimeMillis();
                handler.postDelayed(okPinHold, unlockHoldTotalMs);
                handler.postDelayed(unlockShow, (long) (unlockHoldTotalMs * 0.8));
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showParentPin();
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_4) {
            onSelect(keyCode - KeyEvent.KEYCODE_1);
            return true;
        }
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_D) {
            onSelect(keyCode - KeyEvent.KEYCODE_A);
            return true;
        }
        return false;
    }

    private void hideUnlockHold() {
        if (tvUnlockHold != null) tvUnlockHold.setVisibility(View.GONE);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            handler.removeCallbacks(okPinHold);
            handler.removeCallbacks(unlockShow);
            handler.removeCallbacks(unlockTick);
            hideUnlockHold();
            boolean shouldClick = okHolding && !okPinFired && !pinOpen;
            okHolding = false;
            if (shouldClick && root != null) {
                View f = root.findFocus();
                if (f != null) f.performClick();
            }
            return true;
        }
        return false;
    }

    // ---------------- 家长密码浮层 ----------------

    public void showParentPin() {
        if (pinOpen || root == null) return;
        pinOpen = true;
        pinLayer = new FrameLayout(ctx);
        pinLayer.setBackgroundResource(R.drawable.bg_scrim);
        pinLayer.setClickable(true);
        pinLayer.setFocusable(true);

        LinearLayout card = Ui.pinPanel(ctx);
        TextView title = Ui.text(ctx, "家长密码（" + prefs.pin().length() + " 位）", 26, 0xFFDDDDDD, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        final PinPad[] padRef = new PinPad[1];
        PinPad pad = new PinPad(ctx, "",
                prefs.pin().length(), new PinPad.Listener() {
            @Override public void onEntered(String s) {
                if (s.equals(prefs.pin())) {
                    hideParentPin();
                    unlock(true);
                } else {
                    padRef[0].showError("密码错误，请重试");
                }
            }

            @Override public void onCancelled() {
                hideParentPin();
            }
        });
        padRef[0] = pad;
        card.addView(pad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(ctx, 500), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        pinLayer.addView(card, lp);
        ((FrameLayout) root).addView(pinLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pad.requestFocus();
    }

    public void hideParentPin() {
        if (pinLayer != null) {
            ((FrameLayout) root).removeView(pinLayer);
            pinLayer = null;
        }
        pinOpen = false;
        if (optTexts[0] != null) optTexts[0].requestFocus();
    }

    public boolean isPinOpen() {
        return pinOpen;
    }
}
