package com.kidslock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 全屏答题锁屏页。
 * - 全屏铺满并保持常亮，拦截返回键
 * - 遥控器方向键选答案 + OK 确认；选项编号为 A/B/C/D，数字键 1-4 或字母键 A-D 也可快捷作答
 * - 连续答对 requiredCorrect 题解锁；答错清零重来
 * - 按【菜单键】进入家长密码通道：应用内置数字键盘（可见输入），不依赖系统输入法
 * - 锁屏状态写入持久化，按 HOME 切走会被重新拉起（尽力防绕过）
 */
public class LockScreenActivity extends Activity {

    /** 锁屏页是否在前台。QuizService / Keeper 用它避免"重复拉起"打断答题和家长密码输入。 */
    public static volatile boolean sForeground = false;

    private Prefs prefs;
    private QuizEngine engine;
    private TextView tvQuestion, tvProgress, tvHint, tvFeedback;
    private Button[] optBtns = new Button[4];
    /** 四个选项的编号：A / B / C / D。 */
    private static final String[] OPT_LETTERS = {"A", "B", "C", "D"};
    /** 序号与答案之间的留白（当前为空：A.紧跟答案）。 */
    private static final String OPT_GAP = "";
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
            // 选项格式：A.    答案（字母 + 点 + 留白）
            optBtns[i].setText(OPT_LETTERS[i] + "." + OPT_GAP + current.options[i]);
        }
        optBtns[0].requestFocus();
        // 文字对齐：全部单行 → 上下居中；只要有任意一项换行 → 四个一起靠上
        relayoutOptionText();
    }

    /** 依据"是否有选项换行"统一切换四个按钮的文字对齐方式。 */
    private void relayoutOptionText() {
        optBtns[0].postDelayed(new Runnable() {
            @Override public void run() {
                boolean anyWrap = false;
                for (int i = 0; i < 4; i++) {
                    android.text.Layout l = optBtns[i].getLayout();
                    if (l != null && l.getLineCount() > 1) {
                        anyWrap = true;
                        break;
                    }
                }
                int gravity = anyWrap
                        ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL)
                        : Gravity.CENTER;
                int padTop = anyWrap ? dp(8) : 0;
                for (int i = 0; i < 4; i++) {
                    optBtns[i].setGravity(gravity);
                    optBtns[i].setPadding(dp(10), padTop, dp(10), 0);
                }
            }
        }, 60);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
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
            tvFeedback.setText("✗ 回答错误，正确答案是：" + OPT_LETTERS[current.correctIndex] + "."
                    + OPT_GAP + current.options[current.correctIndex]);
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
    @Override protected void onResume() {
        super.onResume();
        sForeground = true;
    }

    @Override protected void onPause() {
        sForeground = false;
        super.onPause();
    }

    @Override protected void onStop() {
        super.onStop();
        if (!unlocking && prefs.isLocked()) {
            handler.postDelayed(() -> {
                if (!unlocking && prefs.isLocked() && !sForeground) {
                    Keeper.launchLock(this);
                }
            }, 300);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
