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
 * 答题面板控制器（v3.0）。
 *
 * 同一个面板既能放进 Activity（LockScreenActivity），也能放进系统悬浮层
 * （LockOverlay）—— 两种宿主共用一套出题、判题、解锁逻辑，行为完全一致。
 *
 * 悬浮层是解决"主页键回桌面闪一下"的关键：系统桌面启动后，悬浮层仍然盖在最上面，
 * 孩子根本看不到桌面。Activity 只作为没有悬浮窗权限时的兜底。
 */
public class QuizPanel {

    public interface Host {
        /** 解锁成功（viaParent=true 表示家长密码解锁）。宿主负责关闭自己。 */
        void onUnlocked(boolean viaParent);

        /** 面板发现已经不需要锁屏（例如跨夜清零），宿主应静默关闭。 */
        void onClosed();

        Context context();
    }

    private final Host host;
    private final Context ctx;
    private final Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private QuizEngine engine;
    private View root;
    private TextView tvQuestion, tvProgress, tvType, tvHint, tvFeedback;
    private LinearLayout dotsRow;
    private final LinearLayout[] optViews = new LinearLayout[4];
    private final TextView[] optTexts = new TextView[4];
    private final TextView[] optLetters = new TextView[4];
    private final String[] OPT_LETTERS = {"A", "B", "C", "D"};

    private QuizQuestion current;
    private int progress = 0;
    private boolean unlocking = false;
    private boolean answering = false;
    private boolean started = false;

    /** 家长密码浮层（不再用系统 Dialog，悬浮层里也能用）。 */
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

    /** 构建视图（只做一次）。 */
    public View createView() {
        if (root != null) return root;
        LayoutInflater inf = LayoutInflater.from(ctx);
        root = inf.inflate(R.layout.activity_lock, null, false);
        tvQuestion = (TextView) root.findViewById(R.id.tvQuestion);
        tvProgress = (TextView) root.findViewById(R.id.tvProgress);
        tvType = (TextView) root.findViewById(R.id.tvType);
        tvHint = (TextView) root.findViewById(R.id.tvHint);
        tvFeedback = (TextView) root.findViewById(R.id.tvFeedback);
        dotsRow = (LinearLayout) root.findViewById(R.id.dots);
        if (root instanceof KeyRoot) {
            ((KeyRoot) root).setKeyHandler(new KeyRoot.KeyHandler() {
                @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
                    return QuizPanel.this.onKeyDown(keyCode, event);
                }
            });
        }

        int[] ids = {R.id.opt0, R.id.opt1, R.id.opt2, R.id.opt3};
        int[] textIds = {R.id.optText0, R.id.optText1, R.id.optText2, R.id.optText3};
        int[] letterIds = {R.id.optLetter0, R.id.optLetter1, R.id.optLetter2, R.id.optLetter3};
        for (int i = 0; i < 4; i++) {
            optViews[i] = (LinearLayout) root.findViewById(ids[i]);
            optTexts[i] = (TextView) root.findViewById(textIds[i]);
            optLetters[i] = (TextView) root.findViewById(letterIds[i]);
            final int idx = i;
            optViews[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    onSelect(idx);
                }
            });
        }
        return root;
    }

    /** 开始出题（视图已 attach 后调用）。 */
    public void start() {
        if (started) return;
        started = true;
        engine = new QuizEngine(ctx, prefs);
        showNextQuestion();
        updateProgressUI();
        if (optViews[0] != null) optViews[0].requestFocus();
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
        tvType.setText(current.subLabel == null ? "答题" : current.subLabel);
        applyQuestionSize(current.question);
        for (int i = 0; i < 4; i++) {
            optViews[i].setBackgroundResource(R.drawable.bg_option);
            optLetters[i].setBackgroundResource(R.drawable.bg_badge);
            optLetters[i].setTextColor(Ui.color(ctx, R.color.text_main));
            optTexts[i].setTextColor(ctx.getResources().getColorStateList(R.color.block_text));
            optTexts[i].setText(current.options[i]);
            optTexts[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, optionSize(current.options[i]));
            optTexts[i].setTypeface(hasCjk(current.options[i]) ? null : latinTypeface());
        }
        if (optViews[0] != null) optViews[0].requestFocus();
        updateHint();
    }

    private void applyQuestionSize(String q) {
        int len = q == null ? 0 : q.length();
        float sp;
        if (len <= 12) sp = 42;
        else if (len <= 24) sp = 36;
        else if (len <= 38) sp = 30;
        else sp = 25;
        tvQuestion.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    private float optionSize(String s) {
        int len = s == null ? 0 : s.length();
        if (len <= 10) return 26;
        if (len <= 16) return 23;
        if (len <= 24) return 20;
        return 18;
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
        rebuildDots(need);
        updateHint();
    }

    private void rebuildDots(int need) {
        if (dotsRow == null) return;
        int n = Math.min(need, 10);
        if (dotsRow.getChildCount() != n) {
            dotsRow.removeAllViews();
            for (int i = 0; i < n; i++) {
                View dot = new View(ctx);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        Ui.dp(ctx, 18), Ui.dp(ctx, 5));
                lp.leftMargin = Ui.dp(ctx, i == 0 ? 0 : 6);
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
        if (tvHint == null) return;
        if (prefs.isDailyLocked()) {
            tvHint.setText("今日观看时间已用完 · 答对也不能解锁 · 请家长按【菜单键】输入密码");
            tvHint.setTextColor(Ui.color(ctx, R.color.warn));
        } else {
            tvHint.setText("方向键选择 · OK 确认 · 菜单键输入家长密码");
            tvHint.setTextColor(Ui.color(ctx, R.color.text_faint));
        }
    }

    private void onSelect(int index) {
        if (current == null || answering) return;
        answering = true;
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
            optViews[current.correctIndex].setBackgroundResource(R.drawable.bg_option_correct);
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
        optViews[index].setBackgroundResource(
                correct ? R.drawable.bg_option_correct : R.drawable.bg_option_wrong);
        optTexts[index].setTextColor(Ui.color(ctx,
                correct ? R.color.ok : R.color.danger));
        optLetters[index].setTextColor(Ui.color(ctx,
                correct ? R.color.ok : R.color.danger));
    }

    /** 解锁：写状态 + 通知宿主关闭。 */
    private void unlock(boolean viaParent) {
        if (prefs.isDailyLocked() && !viaParent) {
            return;
        }
        unlocking = true;
        if (viaParent && prefs.isDailyLocked()) {
            prefs.setDailyExempt(true);
        }
        prefs.setDailyLocked(false);
        prefs.setLocked(false);
        long now = System.currentTimeMillis();
        prefs.setSessionStartMs(now);
        prefs.setLastAliveMs(now);
        handler.removeCallbacksAndMessages(null);
        host.onUnlocked(viaParent);
    }

    // ---------------- 按键 ----------------

    /**
     * 返回 true 表示已消费。
     * 悬浮层和 Activity 都把按键先交给这里，保证行为一致。
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (pinOpen) return false;   // 家长键盘自己处理
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;             // 锁屏里返回键无效
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

    // ---------------- 家长密码浮层 ----------------

    public void showParentPin() {
        if (pinOpen || root == null) return;
        pinOpen = true;
        pinLayer = new FrameLayout(ctx);
        pinLayer.setBackgroundResource(R.drawable.bg_scrim);
        pinLayer.setClickable(true);
        pinLayer.setFocusable(true);

        LinearLayout card = Ui.dialogPanel(ctx);
        TextView title = Ui.text(ctx, "家长密码", 22, Ui.color(ctx, R.color.text_main), true);
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
        if (optViews[0] != null) optViews[0].requestFocus();
    }

    public boolean isPinOpen() {
        return pinOpen;
    }
}
