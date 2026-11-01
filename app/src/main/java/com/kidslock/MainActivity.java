package com.kidslock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 家长设置界面。
 * - 进入需先通过家长 PIN 门禁（应用内置数字键盘）
 * - 监控开关：开启启动计时服务，停用停止服务并暂停计时
 * - 修改密码：内置数字键盘，新密码 4-8 位
 * - 重新加载题库：真正刷新出题数据
 */
public class MainActivity extends Activity {
    private Prefs prefs;
    private Button btnEnable, btnSingleMinus, btnSinglePlus, btnDailyMinus, btnDailyPlus,
            btnCorrMinus, btnCorrPlus, btnMath, btnPinyin, btnPoetry, btnEnglish, btnCustom,
            btnReloadQuiz, btnTestLock, btnChangePin, btnSmbSetup, btnSmbSyncNow, btnClearLocal;
    private TextView tvStatus, tvSingle, tvDaily, tvCorrect, tvQuizStatus, tvSmbStatus;
    private QuizEngine engine;
    private boolean parentAuthenticated = false;
    private boolean smbAutoSynced = false;   // 每次打开应用只自动同步一次 SMB

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        // 若当前处于锁定状态，直接进入锁屏答题页（家长也需先解锁）
        if (prefs.isLocked()) {
            startActivity(new Intent(this, LockScreenActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        bindViews();
        if (prefs.isEnabled()) startQuizService();
        engine = new QuizEngine(this, prefs);
        requestPermissionsIfNeeded();
        refreshAll();
        maybeAutoSyncSmb();   // 打开应用时自动更新一次 SMB 题库（若有启用）
        // 家长门禁：先验证 PIN 才允许操作设置
        showPinGate();
    }

    /** 运行时权限引导：通知(Android13+) + 全部文件访问(Android11+，读取 Download 题库)。 */
    private void requestPermissionsIfNeeded() {
        // 优先引导"所有文件访问"（读取 Download 题库）；先尝试应用专属权限页，避免跳到全局页
        if (Build.VERSION.SDK_INT >= 30 && !isAllFilesAccessGranted()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception ignored) {
                }
            }
        }
        // 通知权限(Android13+)延迟请求，避免与上面的权限页相互干扰
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001),
                    500);
        }
    }

    private boolean isAllFilesAccessGranted() {
        if (Build.VERSION.SDK_INT < 30) return true;
        try {
            return android.os.Environment.isExternalStorageManager();
        } catch (Exception e) {
            return false;
        }
    }

    private void bindViews() {
        tvStatus = findViewById(R.id.tvStatus);
        btnEnable = findViewById(R.id.btnEnable);
        btnSingleMinus = findViewById(R.id.btnSingleMinus);
        btnSinglePlus = findViewById(R.id.btnSinglePlus);
        tvSingle = findViewById(R.id.tvSingle);
        btnDailyMinus = findViewById(R.id.btnDailyMinus);
        btnDailyPlus = findViewById(R.id.btnDailyPlus);
        tvDaily = findViewById(R.id.tvDaily);
        btnCorrMinus = findViewById(R.id.btnCorrMinus);
        btnCorrPlus = findViewById(R.id.btnCorrPlus);
        tvCorrect = findViewById(R.id.tvCorrect);
        btnMath = findViewById(R.id.btnMath);
        btnPinyin = findViewById(R.id.btnPinyin);
        btnPoetry = findViewById(R.id.btnPoetry);
        btnEnglish = findViewById(R.id.btnEnglish);
        btnCustom = findViewById(R.id.btnCustom);
        btnReloadQuiz = findViewById(R.id.btnReloadQuiz);
        tvQuizStatus = findViewById(R.id.tvQuizStatus);
        btnTestLock = findViewById(R.id.btnTestLock);
        btnChangePin = findViewById(R.id.btnChangePin);
        btnSmbSetup = findViewById(R.id.btnSmbSetup);
        btnSmbSyncNow = findViewById(R.id.btnSmbSyncNow);
        btnClearLocal = findViewById(R.id.btnClearLocal);
        tvSmbStatus = findViewById(R.id.tvSmbStatus);
    }

    private void refreshAll() {
        refreshStatus();
        tvSingle.setText(String.valueOf(prefs.singleMinutes()));
        tvDaily.setText(String.valueOf(prefs.dailyMinutes()));
        tvCorrect.setText(String.valueOf(prefs.requiredCorrect()));
        refreshTypeButtons();
        refreshQuizStatus();
    }

    private void refreshStatus() {
        btnEnable.setText(prefs.isEnabled() ? "已开启（点击停用）" : "已停用（点击开启）");
        tvStatus.setText(prefs.isLocked() ? "当前状态：锁定中" : "当前状态：未锁定");
    }

    private void refreshTypeButtons() {
        int t = prefs.quizTypes();
        btnMath.setText((t & Prefs.TYPE_MATH) != 0 ? "✓ 口算" : "口算");
        btnPinyin.setText((t & Prefs.TYPE_PINYIN) != 0 ? "✓ 认字" : "认字");
        btnPoetry.setText((t & Prefs.TYPE_POETRY) != 0 ? "✓ 古诗" : "古诗");
        btnEnglish.setText((t & Prefs.TYPE_ENGLISH) != 0 ? "✓ 英语" : "英语");
        btnCustom.setText((t & Prefs.TYPE_CUSTOM) != 0 ? "✓ 自定义" : "自定义");
    }

    private void refreshQuizStatus() {
        int localCount = countLocalTxt();
        boolean smb = prefs.smbEnabled();
        if (localCount > 0) {
            tvQuizStatus.setText(smb
                    ? ("已加载本地题库（" + localCount + " 个文件）+ SMB 同步")
                    : ("已加载本地题库（" + localCount + " 个文件）：/sdcard/Download/txhlock/"));
        } else {
            tvQuizStatus.setText(smb
                    ? "本地无题库（当前使用 SMB 题库）"
                    : "本地无题库（放 /sdcard/Download/txhlock/*.txt 后点重新加载）");
        }
        if (smb) {
            String base = prefs.smbHost() + "/" + prefs.smbShare();
            long t = prefs.lastSmbSync();
            String last = t > 0
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(t))
                    : "尚未同步成功";
            tvSmbStatus.setText("SMB 已开启：" + base + "\n上次更新：" + last);
        } else {
            tvSmbStatus.setText("SMB 同步未开启");
        }
    }

    /** 统计本地题库目录下 .txt 文件数量。 */
    private int countLocalTxt() {
        File dir = new File(QuizLoader.DIR);
        if (!dir.exists()) return 0;
        File[] fs = dir.listFiles();
        if (fs == null) return 0;
        int n = 0;
        for (File f : fs) {
            if (f.getName().toLowerCase().endsWith(".txt")) n++;
        }
        return n;
    }

    /** 卸载本地题库：删除本地目录下所有 .txt 题库，重建引擎后本地状态显示"本地无题库"。 */
    private void clearLocalQuiz() {
        File dir = new File(QuizLoader.DIR);
        int existing = countLocalTxt();
        int n = 0;
        if (dir.exists()) {
            File[] fs = dir.listFiles();
            if (fs != null) {
                for (File f : fs) {
                    if (f.getName().toLowerCase().endsWith(".txt") && f.delete()) n++;
                }
            }
        }
        engine = new QuizEngine(this, prefs);   // 重建出题引擎，移除本地题（保留内置 + SMB）
        refreshAll();
        if (existing == 0) {
            toast("本地题库目录已无 txt 文件");
        } else if (n == existing) {
            toast("已卸载 " + n + " 个本地题库文件");
        } else {
            toast("卸载失败：" + (existing - n) + " 个文件未删除，请检查存储权限");
        }
    }

    /**
     * 执行一次 SMB 同步（后台线程）。成功则记录"更新成功时间"。
     * withToast：手动/保存后同步给用户明确提示；打开应用自动同步时不弹提示。
     */
    private void doSync(final boolean withToast) {
        new Thread(() -> {
            try {
                final int n = SMBHelper.syncOnce(MainActivity.this, prefs);
                runOnUiThread(() -> {
                    if (n > 0) {
                        // 真的有更新：记下本次成功同步时间
                        prefs.setLastSmbSync(System.currentTimeMillis());
                        if (withToast) {
                            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                                    .format(new Date());
                            toast("更新成功 " + time + "，更新 " + n + " 个文件");
                        }
                    } else if (n == 0) {
                        // 连接成功，远端题库没有变化 → 明确提示"无需更新"
                        if (withToast) {
                            long t = prefs.lastSmbSync();
                            String last = t > 0
                                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(t))
                                    : "从未成功";
                            toast("题库没有变化，无需更新（上次更新：" + last + "）");
                        }
                    } else if (withToast) {
                        // 连接失败：区分"有过成功历史"与"从未成功"
                        long t = prefs.lastSmbSync();
                        if (t > 0) {
                            String last = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                                    .format(new Date(t));
                            toast("连接失败，题库沿用上次（" + last + "）；请确认电脑已开机并开启共享");
                        } else {
                            toast("更新失败，请检查地址/账号/网络");
                        }
                    }
                    refreshAll();
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    if (withToast) toast("更新失败：" + e);
                    refreshAll();
                });
            }
        }).start();
    }

    /** 打开应用时自动同步一次 SMB（每次进程启动仅一次，不重复弹窗打扰）。 */
    private void maybeAutoSyncSmb() {
        if (smbAutoSynced) return;
        smbAutoSynced = true;
        if (prefs.smbEnabled()) {
            doSync(false);
        }
    }

    private void startQuizService() {
        Intent i = new Intent(this, QuizService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {
        }
    }

    private void registerClicks() {
        // 监控开关：开启启动计时，停用停止服务并暂停本次会话
        btnEnable.setOnClickListener(v -> {
            boolean en = !prefs.isEnabled();
            prefs.setEnabled(en);
            if (en) {
                startQuizService();
            } else {
                prefs.setSessionStartMs(0);
                stopService(new Intent(this, QuizService.class));
            }
            refreshStatus();
        });
        btnSingleMinus.setOnClickListener(v -> { prefs.setSingleMinutes(clamp(prefs.singleMinutes() - 1, 1, 120)); refreshAll(); });
        btnSinglePlus.setOnClickListener(v -> { prefs.setSingleMinutes(clamp(prefs.singleMinutes() + 1, 1, 120)); refreshAll(); });
        btnDailyMinus.setOnClickListener(v -> { prefs.setDailyMinutes(clamp(prefs.dailyMinutes() - 5, 0, 600)); refreshAll(); });
        btnDailyPlus.setOnClickListener(v -> { prefs.setDailyMinutes(clamp(prefs.dailyMinutes() + 5, 0, 600)); refreshAll(); });
        btnCorrMinus.setOnClickListener(v -> { prefs.setRequiredCorrect(clamp(prefs.requiredCorrect() - 1, 1, 10)); refreshAll(); });
        btnCorrPlus.setOnClickListener(v -> { prefs.setRequiredCorrect(clamp(prefs.requiredCorrect() + 1, 1, 10)); refreshAll(); });

        btnMath.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_MATH); refreshTypeButtons(); });
        btnPinyin.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_PINYIN); refreshTypeButtons(); });
        btnPoetry.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_POETRY); refreshTypeButtons(); });
        btnEnglish.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_ENGLISH); refreshTypeButtons(); });
        btnCustom.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_CUSTOM); refreshTypeButtons(); });

        // 重新加载题库：真正重建出题引擎，使新题库立即生效
        btnReloadQuiz.setOnClickListener(v -> {
            engine = new QuizEngine(this, prefs);
            refreshQuizStatus();
            toast("题库已重新加载");
        });
        // 卸载本地题库：删除本地目录的 txt 题库文件，只保留内置与 SMB
        btnClearLocal.setOnClickListener(v -> clearLocalQuiz());
        btnSmbSetup.setOnClickListener(v -> showSmbDialog());
        btnSmbSyncNow.setOnClickListener(v -> {
            if (!prefs.smbEnabled()) {
                toast("请先在 SMB 同步设置里配置并保存启用");
                return;
            }
            doSync(true);
        });
        btnChangePin.setOnClickListener(v -> showChangePin());
        btnTestLock.setOnClickListener(v -> {
            prefs.setLocked(true);
            startActivity(new Intent(this, LockScreenActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        });
    }

    /** 家长门禁：进入设置前必须先验证 PIN（应用内置数字键盘）。 */
    private void showPinGate() {
        if (parentAuthenticated) return;
        final AlertDialog[] holder = new AlertDialog[1];
        final PinPad[] padRef = new PinPad[1];
        PinPad pad = new PinPad(this, "家长验证：请输入密码", prefs.pin().length(), new PinPad.Listener() {
            @Override public void onEntered(String s) {
                if (s.equals(prefs.pin())) {
                    parentAuthenticated = true;
                    holder[0].dismiss();
                    refreshAll();
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
                .setCancelable(false)   // 必须验证通过才能操作设置
                .create();
        if (holder[0].getWindow() != null) holder[0].getWindow().setBackgroundDrawableResource(R.color.panel);
        holder[0].show();
    }

    /**
     * 修改家长密码：三步验证，应用内置数字键盘。
     * 0 = 验证旧密码；1 = 输入新密码(4-8位，按确认)；2 = 再次确认。
     */
    private void showChangePin() {
        final String oldPin = prefs.pin();
        final int[] stage = {0};
        final String[] newPin = {""};
        final AlertDialog[] holder = new AlertDialog[1];
        final PinPad[] padRef = new PinPad[1];
        PinPad pad = new PinPad(this, "请输入当前密码", oldPin.length(), new PinPad.Listener() {
            @Override public void onEntered(String s) {
                if (stage[0] == 0) {
                    if (s.equals(oldPin)) {
                        stage[0] = 1;
                        padRef[0].setMaxLen(0);   // 新密码位数不定，用确认键提交
                        padRef[0].setTitle("请输入新密码（4-8 位）");
                    } else {
                        padRef[0].setTitle("密码错误，请重试");
                    }
                } else if (stage[0] == 1) {
                    if (s.length() < 4 || s.length() > 8) {
                        padRef[0].setTitle("密码需 4-8 位，请重新输入");
                        return;
                    }
                    newPin[0] = s;
                    stage[0] = 2;
                    padRef[0].setTitle("请再次输入新密码确认");
                } else {
                    if (s.equals(newPin[0])) {
                        prefs.setPin(newPin[0]);
                        toast("新密码已保存");
                        holder[0].dismiss();
                    } else {
                        padRef[0].setTitle("两次不一致，请重新输入新密码");
                        stage[0] = 1;
                    }
                }
            }
            @Override public void onCancelled() {}
        });
        padRef[0] = pad;
        holder[0] = new AlertDialog.Builder(this)
                .setView(pad)
                .setTitle(null)
                .setCancelable(true)
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .create();
        if (holder[0].getWindow() != null) holder[0].getWindow().setBackgroundDrawableResource(R.color.panel);
        holder[0].show();
    }

    /** 局域网 SMB 题库同步设置对话框（家长配置一次，之后每30秒全自动更新）。 */
    private void showSmbDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(50, 30, 50, 30);

        EditText host = addField(root, "服务器IP或主机名（如 192.168.1.100）", prefs.smbHost(), true);
        EditText share = addField(root, "共享名（如 share，或电脑上共享文件夹名）", prefs.smbShare(), true);
        EditText user = addField(root, "用户名（可留空）", prefs.smbUser(), true);
        EditText pass = addField(root, "密码", prefs.smbPass(), true);
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText path = addField(root, "共享内子目录（可留空，如 题库 或 /一年级）", prefs.smbPath(), false);

        // 内容过多时允许滚动，避免底部"共享内子目录"和按钮被键盘/屏幕遮挡
        ScrollView sv = new ScrollView(this);
        sv.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("局域网 SMB 题库同步")
                .setView(sv)
                .setPositiveButton("保存并启用", (d, w) -> {
                    prefs.setSmbHost(host.getText().toString().trim());
                    prefs.setSmbShare(share.getText().toString().trim());
                    prefs.setSmbUser(user.getText().toString().trim());
                    prefs.setSmbPass(pass.getText().toString());
                    String p = path.getText().toString().trim();
                    prefs.setSmbPath(p.isEmpty() ? "/" : p);
                    prefs.setSmbEnabled(true);
                    refreshAll();
                    toast("已保存，正在连接 SMB 同步题库…");
                    doSync(true);   // 保存后立即同步一次并提示结果/时间
                })
                .setNegativeButton("停用 SMB", (d, w) -> {
                    prefs.setSmbEnabled(false);
                    prefs.setSmbManifest("");
                    refreshAll();
                    toast("已停用 SMB 同步");
                })
                .setNeutralButton("取消", null)
                .create();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawableResource(R.color.panel);
        dlg.show();
    }

    private EditText addField(LinearLayout root, String label, String value, boolean nextAction) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFDDDDDD);
        tv.setTextSize(18);
        tv.setPadding(0, 24, 0, 6);
        EditText et = new EditText(this);
        et.setText(value);
        et.setTextColor(0xFF111111);
        et.setTextSize(22);
        et.setSingleLine(true);
        et.setBackgroundResource(R.drawable.bg_option);
        // 软键盘回车键：前几项显示"下一项"并跳到下一输入框；最后一项显示"完成"收起键盘
        et.setImeOptions(nextAction ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        root.addView(tv);
        root.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override protected void onResume() {
        super.onResume();
        registerClicks();
        refreshAll();
    }

    @Override protected void onStop() {
        super.onStop();
        // 离开家长设置后重置验证状态，再次进入需重新输入家长 PIN
        parentAuthenticated = false;
    }
}
