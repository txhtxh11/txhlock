package com.kidslock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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
            btnAwayMinus, btnAwayPlus,
            btnCorrMinus, btnCorrPlus, btnMath, btnPinyin, btnPoetry, btnEnglish, btnCustom,
            btnMath2, btnChinese, btnPhysics, btnChemistry, btnBiology,
            btnHistory, btnGeography, btnPolitics,
            btnLevelMinus, btnLevelPlus,
            btnReloadQuiz, btnExportQuiz, btnTestLock, btnChangePin, btnSmbSetup, btnSmbSyncNow, btnClearLocal, btnBootLock,
            btnHomeSetup, btnSysSettings, btnAcc;
    private TextView tvStatus, tvSingle, tvDaily, tvAway, tvCorrect, tvLevel, tvQuizStatus, tvSmbStatus, tvHomeStatus, tvAccStatus;
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
        // 右上角版本号：统一由 AppVer 控制，改版本只改那一个文件
        TextView tvVer = findViewById(R.id.tvVersion);
        if (tvVer != null) tvVer.setText(AppVer.LABEL);
        btnEnable = findViewById(R.id.btnEnable);
        btnSingleMinus = findViewById(R.id.btnSingleMinus);
        btnSinglePlus = findViewById(R.id.btnSinglePlus);
        tvSingle = findViewById(R.id.tvSingle);
        btnDailyMinus = findViewById(R.id.btnDailyMinus);
        btnDailyPlus = findViewById(R.id.btnDailyPlus);
        tvDaily = findViewById(R.id.tvDaily);
        btnAwayMinus = findViewById(R.id.btnAwayMinus);
        btnAwayPlus = findViewById(R.id.btnAwayPlus);
        tvAway = findViewById(R.id.tvAway);
        btnCorrMinus = findViewById(R.id.btnCorrMinus);
        btnCorrPlus = findViewById(R.id.btnCorrPlus);
        tvCorrect = findViewById(R.id.tvCorrect);
        btnLevelMinus = findViewById(R.id.btnLevelMinus);
        btnLevelPlus = findViewById(R.id.btnLevelPlus);
        tvLevel = findViewById(R.id.tvLevel);
        btnMath = findViewById(R.id.btnMath);
        btnPinyin = findViewById(R.id.btnPinyin);
        btnPoetry = findViewById(R.id.btnPoetry);
        btnEnglish = findViewById(R.id.btnEnglish);
        btnCustom = findViewById(R.id.btnCustom);
        btnMath2 = findViewById(R.id.btnMath2);
        btnChinese = findViewById(R.id.btnChinese);
        btnPhysics = findViewById(R.id.btnPhysics);
        btnChemistry = findViewById(R.id.btnChemistry);
        btnBiology = findViewById(R.id.btnBiology);
        btnHistory = findViewById(R.id.btnHistory);
        btnGeography = findViewById(R.id.btnGeography);
        btnPolitics = findViewById(R.id.btnPolitics);
        btnReloadQuiz = findViewById(R.id.btnReloadQuiz);
        btnExportQuiz = findViewById(R.id.btnExportQuiz);
        tvQuizStatus = findViewById(R.id.tvQuizStatus);
        btnTestLock = findViewById(R.id.btnTestLock);
        btnChangePin = findViewById(R.id.btnChangePin);
        btnSmbSetup = findViewById(R.id.btnSmbSetup);
        btnSmbSyncNow = findViewById(R.id.btnSmbSyncNow);
        btnClearLocal = findViewById(R.id.btnClearLocal);
        tvSmbStatus = findViewById(R.id.tvSmbStatus);
        btnBootLock = findViewById(R.id.btnBootLock);
        btnHomeSetup = findViewById(R.id.btnHomeSetup);
        btnSysSettings = findViewById(R.id.btnSysSettings);
        btnAcc = findViewById(R.id.btnAcc);
        tvAccStatus = findViewById(R.id.tvAccStatus);
        tvHomeStatus = findViewById(R.id.tvHomeStatus);
    }

    private void refreshAll() {
        refreshStatus();
        tvSingle.setText(String.valueOf(prefs.singleMinutes()));
        tvDaily.setText(String.valueOf(prefs.dailyMinutes()));
        tvAway.setText(awayText(prefs.awayResetMinutes()));
        tvCorrect.setText(String.valueOf(prefs.requiredCorrect()));
        tvLevel.setText(Prefs.gradeName(prefs.grade()));
        refreshTypeButtons();
        refreshQuizStatus();
    }

    /** 长关机阈值的显示文本：0 表示不按关机时长清零。 */
    private String awayText(int minutes) {
        return minutes <= 0 ? "关闭（仅跨夜清零）" : (minutes + " 分钟");
    }

    private void refreshStatus() {
        btnEnable.setText(prefs.isEnabled() ? "已开启（点击停用）" : "已停用（点击开启）");
        tvStatus.setText(prefs.isLocked() ? "当前状态：锁定中" : "当前状态：未锁定");
        if (btnBootLock != null) {
            btnBootLock.setText(prefs.lockOnBoot() ? "开机自动锁定：开" : "开机自动锁定：关");
        }
        refreshHomeStatus();
    }

    /** 本应用对系统桌面零改动：这里直接说明"卸载即还原"，并在下方给出还原卸载入口。 */
    private void refreshHomeStatus() {
        // 状态提示统一用与页面其它提示相同的灰色（text_dim），靠 ✅/⚠/❌ 符号区分状态
        int dim = getResources().getColor(R.color.text_dim);
        if (tvHomeStatus != null) {
            tvHomeStatus.setText("系统桌面：未做任何修改 —— 本应用只负责「锁屏」，"
                    + "不接管桌面、不修改系统设置，卸载后小米桌面自动恢复原样");
            tvHomeStatus.setTextColor(dim);
        }
        if (tvAccStatus != null) {
            int warn = getResources().getColor(R.color.text_warn);
            int danger = getResources().getColor(R.color.text_danger);
            boolean bound = isAccessibilityOn();
            boolean keyFilter = isKeyFilterActive();

            if (bound && keyFilter) {
                tvAccStatus.setText("无障碍保活：✅ 已开启 —— 开机自动锁屏 + 切走秒回锁屏"
                        + "\n主页键：✅ 已物理拦截（按下无反应，小米桌面不会出现）");
                tvAccStatus.setTextColor(dim);
                if (btnAcc != null) btnAcc.setText("管理无障碍保活（可关闭）");
            } else if (bound) {
                // 已绑定但没拿到键过滤：极少见，通常是系统刚起来还没配置完
                tvAccStatus.setText("无障碍保活：⚠ 服务在跑，但没拿到「按键过滤」权限"
                        + "\n主页键：⚠ 现在只靠 0.8 秒抢回，按下去会看到小米桌面闪一下"
                        + "\n修复：点下方按钮进辅助功能，把 txhlock 关掉再打开一次");
                tvAccStatus.setTextColor(warn);
                if (btnAcc != null) btnAcc.setText("① 点这里修复主页键拦截");
            } else if (isListedInSettings()) {
                // settings 里有名字，系统却没绑定 —— 覆盖安装后最典型的状态
                tvAccStatus.setText("无障碍保活：⚠ 被系统解绑了 —— 名字还在，服务没在跑"
                        + "\n后果：主页键拦不住（会先看到小米桌面），开机也可能不自启"
                        + "\n修复：点下方按钮进辅助功能，把 txhlock 关掉再打开一次");
                tvAccStatus.setTextColor(danger);
                if (btnAcc != null) btnAcc.setText("① 点这里修复主页键拦截");
            } else {
                tvAccStatus.setText("无障碍保活：❌ 未开启 —— 这是小米电视上最可靠的开机自启方式"
                        + "\n后果：主页键完全不拦、开机可能不自启"
                        + "\n修复：点下方按钮，在辅助功能里打开 txhlock");
                tvAccStatus.setTextColor(danger);
                if (btnAcc != null) btnAcc.setText("① 点这里开启无障碍保活");
            }
        }
    }

    /** 无障碍服务当前是否真的被系统绑定（不是查 settings 字符串）。 */
    private boolean isAccessibilityOn() {
        return KeyFilter.isBound(this);
    }

    /** settings 里是否登记过本服务：用来区分「从没开过」和「开过但被系统解绑了」。 */
    private boolean isListedInSettings() {
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) return false;
            String me = getPackageName() + "/" + BootAccessibility.class.getName();
            String me2 = getPackageName() + "/.BootAccessibility";
            return s.contains(me) || s.contains(me2);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 系统当前绑定的本服务，是否已经拿到了"按键过滤"能力。
     * 这决定了主页键是"按下无反应"还是"先回桌面再被抢回来"。
     */
    private boolean isKeyFilterActive() {
        return KeyFilter.hasKeyFilter(this);
    }

    /**
     * 主页键拦截失效时的醒目提示（家长进设置页才看得到，不会打扰孩子看电视）。
     * 24 小时内最多弹一次，避免每次进设置都被打断。
     */
    private void maybeWarnKeyFilter() {
        if (!prefs.isEnabled()) return;
        if (isKeyFilterActive()) return;
        long last = prefs.lastKeyHintMs();
        long now = System.currentTimeMillis();
        if (now - last < 24L * 3600L * 1000L) return;
        prefs.setLastKeyHintMs(now);

        String msg;
        if (isListedInSettings()) {
            msg = "系统把 txhlock 的无障碍服务解绑了（覆盖安装后常见）。\n\n"
                    + "现在的后果：孩子按遥控器主页键，会先看到小米桌面，再被 0.8 秒后抢回锁屏。\n\n"
                    + "修复只要一步：点「去修复」进辅助功能，把 txhlock 关掉，再打开一次。";
        } else if (!isAccessibilityOn()) {
            msg = "还没开启无障碍保活。\n\n"
                    + "现在的后果：主页键完全不拦，孩子可以直接回桌面；开机也可能不自启锁屏。\n\n"
                    + "点「去开启」进辅助功能，打开 txhlock 即可。";
        } else {
            msg = "无障碍服务已在运行，但没拿到「按键过滤」权限。\n\n"
                    + "后果：主页键拦不住，只能靠 0.8 秒轮询抢回。\n\n"
                    + "修复：进辅助功能，把 txhlock 关掉再打开一次。";
        }
        new AlertDialog.Builder(this)
                .setTitle("主页键拦截未生效")
                .setMessage(msg)
                .setPositiveButton("去修复", (d, w) -> openAccessibilitySettings())
                .setNegativeButton("稍后", null)
                .show();
    }

    private void refreshTypeButtons() {
        int t = prefs.quizTypes();
        btnMath.setText((t & Prefs.TYPE_MATH) != 0 ? "✓ 口算" : "口算");
        btnPinyin.setText((t & Prefs.TYPE_PINYIN) != 0 ? "✓ 认字" : "认字");
        btnPoetry.setText((t & Prefs.TYPE_POETRY) != 0 ? "✓ 古诗" : "古诗");
        btnEnglish.setText((t & Prefs.TYPE_ENGLISH) != 0 ? "✓ 英语" : "英语");
        btnCustom.setText((t & Prefs.TYPE_CUSTOM) != 0 ? "✓ 自定义" : "自定义");
        btnMath2.setText((t & Prefs.TYPE_MATH2) != 0 ? "✓ 数学" : "数学");
        btnChinese.setText((t & Prefs.TYPE_CHINESE) != 0 ? "✓ 语文" : "语文");
        btnPhysics.setText((t & Prefs.TYPE_PHYSICS) != 0 ? "✓ 物理" : "物理");
        btnChemistry.setText((t & Prefs.TYPE_CHEMISTRY) != 0 ? "✓ 化学" : "化学");
        btnBiology.setText((t & Prefs.TYPE_BIOLOGY) != 0 ? "✓ 生物" : "生物");
        btnHistory.setText((t & Prefs.TYPE_HISTORY) != 0 ? "✓ 历史" : "历史");
        btnGeography.setText((t & Prefs.TYPE_GEOGRAPHY) != 0 ? "✓ 地理" : "地理");
        btnPolitics.setText((t & Prefs.TYPE_POLITICS) != 0 ? "✓ 政治" : "政治");
    }

    private void refreshQuizStatus() {
        int localCount = countLocalTxt();
        boolean smb = prefs.smbEnabled();
        int total = (engine == null) ? 0 : engine.questionCount();
        String src = localCount > 0
                ? ("内置 + 自建 " + localCount + " 个文件" + (smb ? " + SMB" : ""))
                : (smb ? "内置 + SMB 题库" : "仅内置题库");
        if (total > 0) {
            tvQuizStatus.setText("已加载 " + total + " 道题（" + src + "），共 "
                    + ((engine == null) ? 0 : engine.sectionCount()) + " 个知识段");
        } else {
            tvQuizStatus.setText("题库未加载：请检查应用是否有存储读取权限");
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

    /** 导出前检查：目标目录已有 txt 就提醒会被覆盖（家长可能在里面改过题）。 */
    private void confirmExportBuiltin() {
        final java.io.File dir = new java.io.File(QuizLoader.DIR, "内置题库");
        int exist = 0;
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".txt")) exist++;
            }
        }
        if (exist <= 0) {
            doExportBuiltin(dir);
            return;
        }
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("⚠ 覆盖导出目录里的文件？")
                .setMessage("目录 " + dir.getAbsolutePath() + " 里已经有 " + exist + " 个 txt。\n\n"
                        + "• 导出会用内置题库的原文覆盖同名文件；\n"
                        + "• 你在这个目录里改过的内容会丢，找不回来；\n"
                        + "• 想保留的话，先点「取消」，把文件拷到别处再导出。")
                .setPositiveButton("确定覆盖", (d, w) -> doExportBuiltin(dir))
                .setNegativeButton("取消", null)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawableResource(R.color.panel);
        }
        dlg.show();
    }

    private void doExportBuiltin(java.io.File dir) {
        int n = QuizLoader.exportBuiltin(this, dir);
        if (n > 0) {
            toast("已导出 " + n + " 个题库文件到：\n" + dir.getAbsolutePath());
        } else {
            toast("导出失败，请检查应用是否有存储写入权限");
        }
        refreshQuizStatus();
    }

    /**
     * 删除本地题库文件前的二次确认。
     * 只动 /sdcard/Download/txhlock/ 根目录下的 txt（家长自己放进去的），
     * 内置题库（APK assets）与 SMB 缓存目录都不在删除范围内、也没有任何按钮能删。
     */
    private void confirmClearLocal() {
        final int existing = countLocalTxt();
        if (existing <= 0) {
            toast("本地题库目录里没有 txt 文件");
            return;
        }
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("⚠ 确认删除本地题库？")
                .setMessage("只删除你自己放进 " + QuizLoader.DIR + " 的 "
                        + existing + " 个 txt 文件。\n\n"
                        + "• 内置题库是只读的，删不到、也改不到，本操作完全不碰它；\n"
                        + "• SMB 缓存目录同样不动；\n"
                        + "• 删掉的文件进不了回收站，找不回来；\n"
                        + "• 想留着的话，先点「取消」，把文件拷到别处再删。")
                .setPositiveButton("确定删除", (d, w) -> clearLocalQuiz())
                .setNegativeButton("取消", null)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawableResource(R.color.panel);
        }
        dlg.show();
    }

    /** 删除本地题库文件：只删根目录下的 .txt 文件，不递归、不碰子目录，更碰不到内置题库。 */
    private void clearLocalQuiz() {
        File dir = new File(QuizLoader.DIR);
        int existing = countLocalTxt();
        int n = 0;
        if (dir.exists()) {
            File[] fs = dir.listFiles();
            if (fs != null) {
                for (File f : fs) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".txt") && f.delete()) n++;
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
                prefs.setLastAliveMs(System.currentTimeMillis());   // 停用期间不算"离开"，重开时从零计时
                stopService(new Intent(this, QuizService.class));
            }
            refreshStatus();
        });
        btnSingleMinus.setOnClickListener(v -> { prefs.setSingleMinutes(clamp(prefs.singleMinutes() - 1, 1, 120)); refreshAll(); });
        btnSinglePlus.setOnClickListener(v -> { prefs.setSingleMinutes(clamp(prefs.singleMinutes() + 1, 1, 120)); refreshAll(); });
        btnDailyMinus.setOnClickListener(v -> { prefs.setDailyMinutes(clamp(prefs.dailyMinutes() - 5, 0, 600)); refreshAll(); });
        btnDailyPlus.setOnClickListener(v -> { prefs.setDailyMinutes(clamp(prefs.dailyMinutes() + 5, 0, 600)); refreshAll(); });
        // 长关机阈值：以 5 分钟为步进，0/5/10…720；0 表示"不按关机时长清零"（仅跨夜清零）
        btnAwayMinus.setOnClickListener(v -> { prefs.setAwayResetMinutes(stepAway(prefs.awayResetMinutes(), -5)); refreshAll(); });
        btnAwayPlus.setOnClickListener(v -> { prefs.setAwayResetMinutes(stepAway(prefs.awayResetMinutes(), +5)); refreshAll(); });
        btnCorrMinus.setOnClickListener(v -> { prefs.setRequiredCorrect(clamp(prefs.requiredCorrect() - 1, 1, 10)); refreshAll(); });
        btnCorrPlus.setOnClickListener(v -> { prefs.setRequiredCorrect(clamp(prefs.requiredCorrect() + 1, 1, 10)); refreshAll(); });
        btnLevelMinus.setOnClickListener(v -> { prefs.setGrade(clamp(prefs.grade() - 1, 1, 9)); refreshAll(); });
        btnLevelPlus.setOnClickListener(v -> { prefs.setGrade(clamp(prefs.grade() + 1, 1, 9)); refreshAll(); });

        btnMath.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_MATH); refreshTypeButtons(); });
        btnPinyin.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_PINYIN); refreshTypeButtons(); });
        btnPoetry.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_POETRY); refreshTypeButtons(); });
        btnEnglish.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_ENGLISH); refreshTypeButtons(); });
        btnCustom.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_CUSTOM); refreshTypeButtons(); });
        btnMath2.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_MATH2); refreshTypeButtons(); });
        btnChinese.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_CHINESE); refreshTypeButtons(); });
        btnPhysics.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_PHYSICS); refreshTypeButtons(); });
        btnChemistry.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_CHEMISTRY); refreshTypeButtons(); });
        btnBiology.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_BIOLOGY); refreshTypeButtons(); });
        btnHistory.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_HISTORY); refreshTypeButtons(); });
        btnGeography.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_GEOGRAPHY); refreshTypeButtons(); });
        btnPolitics.setOnClickListener(v -> { prefs.setQuizTypes(prefs.quizTypes() ^ Prefs.TYPE_POLITICS); refreshTypeButtons(); });

        // 导出内置题库到子目录：方便家长查看格式、照着改，子目录不会被当题库加载
        btnExportQuiz.setOnClickListener(v -> confirmExportBuiltin());
        // 重新加载题库：真正重建出题引擎，使新题库立即生效
        btnReloadQuiz.setOnClickListener(v -> {
            engine = new QuizEngine(this, prefs);
            refreshQuizStatus();
            toast("题库已重新加载");
        });
        // 卸载本地题库：删除本地目录的 txt 题库文件，只保留内置与 SMB
        btnClearLocal.setOnClickListener(v -> confirmClearLocal());
        btnSmbSetup.setOnClickListener(v -> showSmbDialog());
        btnSmbSyncNow.setOnClickListener(v -> {
            if (!prefs.smbEnabled()) {
                toast("请先在 SMB 同步设置里配置并保存启用");
                return;
            }
            doSync(true);
        });
        btnChangePin.setOnClickListener(v -> showChangePin());
        // 开机自动锁定：开启后"重启电视"等于重新开始答题，不再能靠重启逃过去
        btnBootLock.setOnClickListener(v -> {
            boolean v2 = !prefs.lockOnBoot();
            prefs.setLockOnBoot(v2);
            refreshStatus();
            toast(v2 ? "已开启：重启跟随锁定状态（已锁仍锁、未锁续时）" : "已关闭：开机不再锁定");
        });
        // 还原并卸载：先关掉无障碍保活，再卸载。本应用从未改动系统桌面，卸载即完全恢复原样。
        btnHomeSetup.setOnClickListener(v -> showUninstallGuide());
        // 直接跳到本应用的系统详情页：MIUI 的"自启动/后台运行/权限"开关就在这一页
        btnSysSettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception e2) {
                    toast("无法打开系统设置");
                }
            }
        });
        // 无障碍保活：跳系统无障碍设置页（开 / 关都在这一页，家长手动一次即可）
        btnAcc.setOnClickListener(v -> {
            toast(isAccessibilityOn()
                    ? "已开启。把 txhlock 的开关关掉即可停用保活"
                    : "找到 txhlock，打开开关即可");
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                toast("请到 设置 → 无障碍 里操作 txhlock");
            }
        });
        btnTestLock.setOnClickListener(v -> {
            prefs.setLocked(true);
            startActivity(new Intent(this, LockScreenActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        });
    }

    /**
     * 还原并卸载。
     *
     * 设计前提：本应用对系统桌面【零改动】——
     *   · 不声明 CATEGORY_HOME（不参与"谁是桌面"的竞争）
     *   · 不禁用/替换小米桌面
     *   · 不修改任何系统设置（无障碍开关也是家长在系统里手动开的，卸载后自然失效）
     * 因此卸载后，遥控器主页键必然回到小米电视桌面，与安装前完全一致。
     * 这里只做两件事：① 引导关闭无障碍保活（避免系统里留下一个失效条目）② 调起系统卸载界面。
     */
    private void showUninstallGuide() {
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("还原并卸载")
                .setMessage("本应用没有修改系统桌面，也没有禁用小米桌面的任何功能：\n\n"
                        + "• 卸载后，主页键立刻回到小米电视桌面，与安装前完全一致\n"
                        + "• 答题记录、家长密码、题库设置随应用一起删除\n\n"
                        + "建议顺序：先点「① 关闭无障碍保活」，返回后再点「② 卸载」。")
                .setPositiveButton("① 关闭无障碍保活", (d, w) -> openAccessibilitySettings())
                .setNeutralButton("② 卸载", (d, w) -> doUninstall())
                .setNegativeButton("取消", null)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawableResource(R.color.panel);
        }
        dlg.show();
    }

    /** 跳到系统无障碍设置页，家长手动关掉 txhlock 的开关即可彻底清理。 */
    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("把 txhlock 的开关关掉，返回本页再点「卸载」");
        } catch (Exception e) {
            toast("请到 设置 → 无障碍 里关闭 txhlock");
        }
    }

    /** 调起系统卸载界面（应用内一键，不需要命令行）。 */
    private void doUninstall() {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast("请到 设置 → 应用 → txhlock 里卸载");
        }
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
                    // 家长已验证通过，才提示"主页键拦截失效"，孩子看不到这条
                    maybeWarnKeyFilter();
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

    /** 长关机阈值步进：0 = 关闭，往上第一档是 5 分钟，最高 720 分钟（12 小时）。 */
    private int stepAway(int cur, int delta) {
        if (delta < 0) {
            // 0（关闭）往下还是 0；否则减 5，减到 0 就停在"关闭"
            return cur <= 0 ? 0 : Math.max(0, cur - 5);
        }
        // 往上：从 0 直接到 5
        return Math.min(720, (cur <= 0 ? 0 : cur) + 5);
    }

    @Override protected void onResume() {
        super.onResume();
        registerClicks();
        refreshAll();
    }

    @Override protected void onPause() {
        super.onPause();
        // 锁屏一旦触发就关掉本页。
        //
        // 为什么必须是 onPause 而不是 onStop：锁屏页有独立 taskAffinity，跑在另一个任务栈里，
        // 切过去时 MIUI TV 只把本页挪到后台，onStop 迟迟不到（实测 40 秒后 MainActivity
        // 仍留在 Hist 里）。而本页不关的后果是：孩子答完题、锁屏页 finish() 之后，
        // 栈底这个设置页浮上来，重新挂出 onCreate 里那句「家长验证：请输入密码」——
        // 答对了题却卡在密码框前，只能按 HOME 逃到桌面，还伴随一次明显卡顿。
        // onPause 一定会先到（dumpsys 里能看到 mLastPausedActivity 就是本页），拿它当闸门最稳。
        if (prefs != null && prefs.isLocked() && !isFinishing()) {
            finish();
        }
    }

    @Override protected void onStop() {
        super.onStop();
        // 离开家长设置后重置验证状态，再次进入需重新输入家长 PIN
        parentAuthenticated = false;
        // 兜底：onPause 没能拿到锁屏标记时（极端时序），离开时再查一次
        if (prefs != null && prefs.isLocked() && !isFinishing()) {
            finish();
        }
    }
}
