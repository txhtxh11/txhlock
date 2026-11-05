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
        // 显示之前先按"离开规则"判定一次：待机唤醒 / 开机补拉 / 被系统恢复栈时，
        // 这个页面可能比 BootReceiver、计时服务更早被拉起来，此时持久化里还是"锁定中"。
        // 在这里先判定，长关机与跨夜就能当场清零解锁，不会先闪一屏答题再消失。
        Away.resolve(prefs);
        // 只有持久化状态确实"锁定中"才允许显示。
        // 开机补拉 / 看门狗可能在"离开判定已清零解锁"之后仍然把这个页面拉起来，
        // 旧版在这里无条件 setLocked(true)，等于把刚解开的锁又锁回去（长关机也逃不掉）。
        if (!prefs.isLocked()) {
            finish();
            return;
        }
        prefs.setLocked(true);
        engine = new QuizEngine(this, prefs);   // 确认要显示锁屏页了才加载题库（过期拉起不白扫文件）

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
            // Material 主题的 Button 默认 textAllCaps=true，会把答案里的拼音（rén）和
            // 英语单词（apple）强制转成全大写（RÉN / APPLE）。这里关掉，原样照题库显示。
            // 布局里也加了 android:textAllCaps="false"，双保险（防个别 ROM 主题覆盖）。
            optBtns[i].setAllCaps(false);
            optBtns[i].setOnClickListener(v -> onSelect(idx));
        }
        showNextQuestion();
        updateProgressUI();
        optBtns[0].requestFocus();
        handler.post(stateWatcher);
    }

    /**
     * 状态巡检：每秒看一眼持久化里的锁屏标记。
     *
     * 解决"页面已经在前台，之后才被清零解锁"的漏洞：待机唤醒时系统会先恢复锁屏页，
     * 计时服务/开机广播稍后才做离开判定；判定把 locked 清成 false 之后，如果没人再检查，
     * 这个页面就会一直停在答题界面（v1.5 的"跨夜开机还在答题"就是这么来的）。
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
        for (int i = 0; i < 4; i++) {
            // 选项格式：A.    答案（字母 + 点 + 留白）
            optBtns[i].setText(OPT_LETTERS[i] + "." + OPT_GAP + current.options[i]);
            // 纯拉丁内容（拼音、英文单词）换用内置字体：系统 DroidSans 缺 ǎǐǒǔǚ
            // 这几个带声调的元音，会回退到中文字体、显示成又宽又带间隙的样子。
            optBtns[i].setTypeface(hasCjk(current.options[i]) ? null : latinTypeface());
        }
        optBtns[0].requestFocus();
        // 文字对齐：全部单行 → 上下居中；只要有任意一项换行 → 四个一起靠上
        relayoutOptionText();
    }

    /** 拉丁/拼音专用字体（assets/fonts/pinyin.ttf，DejaVu 子集），懒加载；失败则用系统字体。 */
    private android.graphics.Typeface latinFace;
    private boolean latinFaceTried;

    private android.graphics.Typeface latinTypeface() {
        if (!latinFaceTried) {
            latinFaceTried = true;
            try {
                latinFace = android.graphics.Typeface.createFromAsset(getAssets(), "fonts/pinyin.ttf");
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
            if (c >= 0x2E80 && c <= 0x9FFF) return true;   // 汉字、部首
            if (c >= 0xF900 && c <= 0xFAFF) return true;   // 兼容汉字
            if (c >= 0xFF00 && c <= 0xFFEF) return true;   // 全角符号（、（）等）
        }
        return false;
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
        if (viaParent && prefs.isDailyLocked()) {
            // 家长用密码解开"每日上限"锁屏 = 当日豁免：今天不再因每日累计上限锁屏。
            // 旧版这里把 dailyLocked 置回 false，而当日累计时长已经超过上限，
            // 下一秒 tick 立刻又判定超限并锁上 —— 家长密码等于白输。
            prefs.setDailyExempt(true);
        }
        prefs.setDailyLocked(false);
        prefs.setLocked(false);
        long now = System.currentTimeMillis();
        prefs.setSessionStartMs(now);    // 解锁后重新从 0 计时
        prefs.setLastAliveMs(now);       // 心跳对齐，避免解锁瞬间被误判成"离开过"
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
        // 状态可能已在别处被改掉（例如离开判定判定为长关机/跨夜而清零解锁），
        // 这时不该继续显示锁屏页。回来时再判定一次，保证"长关机/跨夜必清零"。
        Away.resolve(prefs);
        if (!prefs.isLocked()) {
            finish();
            return;
        }
        sForeground = true;
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
