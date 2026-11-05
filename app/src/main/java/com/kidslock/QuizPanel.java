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

    private QuizEngine engine;
    private View root;
    private TextView tvQuestion, tvProgress, tvHint, tvFeedback;
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
        LayoutInflater inf = LayoutInflater.from(ctx);
        root = inf.inflate(R.layout.activity_lock, null, false);
        tvQuestion = (TextView) root.findViewById(R.id.tvQuestion);
        tvProgress = (TextView) root.findViewById(R.id.tvProgress);
        tvHint = (TextView) root.findViewById(R.id.tvHint);
        tvFeedback = (TextView) root.findViewById(R.id.tvFeedback);

        int[] ids = {R.id.opt0, R.id.opt1, R.id.opt2, R.id.opt3};
        for (int i = 0; i < 4; i++) {
            optTexts[i] = (TextView) root.findViewById(ids[i]);
            final int idx = i;
            optTexts[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    onSelect(idx);
                }
            });
        }
        if (root instanceof KeyRoot) {
            ((KeyRoot) root).setFocusTarget(optTexts[0]);
            ((KeyRoot) root).setKeyHandler(new KeyRoot.KeyHandler() {
                @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
                    return QuizPanel.this.onKeyDown(keyCode, event);
                }
            });
        }
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
            tvHint.setText("今日观看时间已用完 · 答对也不能解锁 · 请家长按【菜单键】输入密码");
            tvHint.setTextColor(Ui.color(ctx, R.color.warn));
        } else {
            tvHint.setText("用方向键选答案，按 OK 确认 · 菜单键输入家长密码");
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
            markOption(index, false);
            optTexts[current.correctIndex].setBackgroundResource(R.drawable.bg_option_correct);
            tvFeedback.setText("✗ 正确答案是 " + OPT_LETTERS[current.correctIndex] + "."
                    + current.options[current.correctIndex]);
            tvFeedback.setTextColor(Ui.color(ctx, R.color.danger));
            updateProgressUI();
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    tvFeedback.setText("");
                    showNextQuestion();
                    answering = false;
                }
            }, 1600);
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

    // ---------------- 家长密码浮层 ----------------

    public void showParentPin() {
        if (pinOpen || root == null) return;
        pinOpen = true;
        pinLayer = new FrameLayout(ctx);
        pinLayer.setBackgroundResource(R.drawable.bg_scrim);
        pinLayer.setClickable(true);
        pinLayer.setFocusable(true);

        LinearLayout card = Ui.dialogPanel(ctx);
        TextView title = Ui.text(ctx, "家长密码", 24, Ui.color(ctx, R.color.text_main), true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        final PinPad[] padRef = new PinPad[1];
        PinPad pad = new PinPad(ctx, "请输入 " + prefs.pin().length() + " 位家长密码",
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
