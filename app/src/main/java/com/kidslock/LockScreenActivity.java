package com.kidslock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 全屏答题锁屏页。
 * - 全屏铺满并保持常亮，拦截返回键
 * - 遥控器方向键选答案 + OK 确认；数字键 1-4 快捷作答（有数字键的设备）
 * - 连续答对 requiredCorrect 题解锁；答错清零重来
 * - 按【菜单键】进入家长密码通道：应用内置数字键盘（可见输入），不依赖系统输入法
 * - 锁屏状态写入持久化，按 HOME 切走会被重新拉起（尽力防绕过）
 */
public class LockScreenActivity extends Activity {
    private Prefs prefs;
    private QuizEngine engine;
    private TextView tvQuestion, tvProgress, tvHint, tvFeedback;
    private Button[] optBtns = new Button[4];
    private QuizQuestion current;
    private int progress = 0;
    private boolean unlocking = false;
    private boolean answering = false;   // 反馈停留期间防重复作答
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        engine = new QuizEngine(this, prefs);
        prefs.setLocked(true);

        setupFullscreen();
        setContentView(R.layout.activity_lock);

        tvQuestion = findViewById(R.id.tvQuestion);
        tvProgress = findViewById(R.id.tvProgress);
        tvHint = findViewById(R.id.tvHint);
        tvFeedback = findViewById(R.id.tvFeedback);
        optBtns[0] = findViewById(R.id.opt0);
        optBtns[1] = findViewById(R.id.opt1);
        optBtns[2] = findViewById(R.id.opt2);
        optBtns[3] = findViewById(R.id.opt3);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            optBtns[i].setOnClickListener(v -> onSelect(idx));
        }
        showNextQuestion();
        updateProgressUI();
        optBtns[0].requestFocus();
    }

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
        for (int i = 0; i < 4; i++) {
            optBtns[i].setText((i + 1) + ". " + current.options[i]);
        }
        optBtns[0].requestFocus();
    }

    private void updateProgressUI() {
        tvProgress.setText("连续答对 " + progress + " / " + prefs.requiredCorrect() + " 题可解锁");
    }

    /** 作答：给出明确的"答对/答错 + 正确答案"反馈，短暂停留后再换题，不再无提示穷举。 */
    private void onSelect(int index) {
        if (current == null || answering) return;
        answering = true;
        if (index == current.correctIndex) {
            progress++;
            updateProgressUI();
            tvFeedback.setText("✓ 回答正确");
            tvFeedback.setTextColor(0xFF4CAF50);   // 绿
            if (progress >= prefs.requiredCorrect()) {
                tvFeedback.setText("✓ 回答正确，解锁成功！");
                handler.postDelayed(() -> {
                    answering = false;
                    unlock(false);
                }, 900);
                return;
            }
            handler.postDelayed(() -> {
                tvFeedback.setText("");
                showNextQuestion();
                answering = false;
            }, 700);
        } else {
            progress = 0;
            updateProgressUI();
            tvFeedback.setText("✗ 回答错误，正确答案是：" + current.options[current.correctIndex]);
            tvFeedback.setTextColor(0xFFE53935);   // 红
            handler.postDelayed(() -> {
                tvFeedback.setText("");
                showNextQuestion();
                answering = false;
            }, 1600);
        }
    }

    /** 解锁。每日额度耗尽时，答题无法解锁，仅家长密码可解（当日豁免）。 */
    private void unlock(boolean viaParent) {
        if (prefs.isDailyLocked() && !viaParent) {
            toast("今日观看时间已用完，请家长输入密码");
            return;
        }
        unlocking = true;
        if (viaParent) prefs.setDailyLocked(false);   // 家长当日豁免，允许继续观看
        prefs.setLocked(false);
        prefs.setSessionStartMs(System.currentTimeMillis());
        finish();
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
        // 数字键 1-4 快捷答题（有数字键的设备可用）
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_4) {
            onSelect(keyCode - KeyEvent.KEYCODE_1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** 家长密码：应用内置数字键盘（可见输入框 + 数字键），遥控器即可输入。 */
    private void showPinPad() {
        final AlertDialog[] holder = new AlertDialog[1];
        final PinPad[] padRef = new PinPad[1];
        PinPad pad = new PinPad(this, "家长密码（" + prefs.pin().length() + " 位）", prefs.pin().length(),
                new PinPad.Listener() {
            @Override public void onEntered(String s) {
                if (s.equals(prefs.pin())) {
                    toast("密码正确，解锁");
                    holder[0].dismiss();
                    unlock(true);
                } else {
                    padRef[0].setTitle("密码错误，请重试");
                    padRef[0].reset();
                }
            }
            @Override public void onCancelled() {}
        });
        padRef[0] = pad;
        holder[0] = new AlertDialog.Builder(this)
                .setView(pad)
                .setTitle(null)
                .setCancelable(true)
                .setNegativeButton("返回", (d, w) -> d.dismiss())
                .create();
        if (holder[0].getWindow() != null) holder[0].getWindow().setBackgroundDrawableResource(R.color.panel);
        holder[0].show();
    }

    // ---- 防绕过：被切走(如按HOME)后重新拉起 ----
    @Override protected void onStop() {
        super.onStop();
        if (!unlocking && prefs.isLocked()) {
            handler.postDelayed(() -> {
                if (!unlocking && prefs.isLocked()) {
                    try {
                        Intent i = new Intent(this, LockScreenActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(i);
                    } catch (Exception ignored) {
                    }
                }
            }, 200);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
