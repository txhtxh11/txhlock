package com.kidslock;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 家长设置（v4.0 经典版）。
 *
 * 回到 1.9.1 的交互：所有设置都在同一页、大字号、大按钮，用遥控器上下滚动即可，
 * 不再点开一层层弹层 —— 少一次进入、少一次返回。配色也回到 1.9.1 的深海军蓝 + 宝蓝。
 *
 * 与 1.9.1 的区别只在功能：悬浮层锁屏、教材版本、4.3 兼容修复、权限与诊断。
 */
public class MainActivity extends Activity {

    private Prefs prefs;
    private QuizEngine engine;

    private TextView tvStatus, tvVersion, btnEnable,
            btnSingleMinus, btnSinglePlus, tvSingle,
            btnDailyMinus, btnDailyPlus, tvDaily,
            btnAwayMinus, btnAwayPlus, tvAway,
            btnCorrMinus, btnCorrPlus, tvCorrect,
            btnLevelMinus, btnLevelPlus, tvLevel,
            tvQuizStatus, btnExportQuiz, btnReloadQuiz, btnClearLocal,
            tvSmbStatus, btnSmbSetup, btnSmbSyncNow,
            btnChangePin, btnBootLock, tvHomeStatus, btnAcc, tvAccStatus,
            btnOverlay, btnSysSettings, btnHomeSetup, tvHelp, btnFixAcc, btnTestLock;

    private LinearLayout editionRow, typeGrid;
    private FrameLayout gateLayer;

    private boolean authed = false;
    private long leftAtMs = 0L;
    private boolean gateOpen = false;

    private static final String[] EDITION_LABELS = {"人教版", "北师大", "苏教版", "外研版", "译林版", "通用"};

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrap(base));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        if (prefs.isLocked()) {
            Keeper.launchLock(this);
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        bind();
        buildEditionRow();
        buildTypeGrid();
        wire();
        if (prefs.isEnabled()) Keeper.startService(this);
        engine = new QuizEngine(this, prefs);
        refreshAll();
        showGate();
    }

    private void bind() {
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvVersion = (TextView) findViewById(R.id.tvVersion);
        tvVersion.setText(AppVer.VERSION);
        btnEnable = (TextView) findViewById(R.id.btnEnable);
        btnSingleMinus = (TextView) findViewById(R.id.btnSingleMinus);
        btnSinglePlus = (TextView) findViewById(R.id.btnSinglePlus);
        tvSingle = (TextView) findViewById(R.id.tvSingle);
        btnDailyMinus = (TextView) findViewById(R.id.btnDailyMinus);
        btnDailyPlus = (TextView) findViewById(R.id.btnDailyPlus);
        tvDaily = (TextView) findViewById(R.id.tvDaily);
        btnAwayMinus = (TextView) findViewById(R.id.btnAwayMinus);
        btnAwayPlus = (TextView) findViewById(R.id.btnAwayPlus);
        tvAway = (TextView) findViewById(R.id.tvAway);
        btnCorrMinus = (TextView) findViewById(R.id.btnCorrMinus);
        btnCorrPlus = (TextView) findViewById(R.id.btnCorrPlus);
        tvCorrect = (TextView) findViewById(R.id.tvCorrect);
        btnLevelMinus = (TextView) findViewById(R.id.btnLevelMinus);
        btnLevelPlus = (TextView) findViewById(R.id.btnLevelPlus);
        tvLevel = (TextView) findViewById(R.id.tvLevel);
        editionRow = (LinearLayout) findViewById(R.id.editionRow);
        typeGrid = (LinearLayout) findViewById(R.id.typeGrid);
        tvQuizStatus = (TextView) findViewById(R.id.tvQuizStatus);
        btnExportQuiz = (TextView) findViewById(R.id.btnExportQuiz);
        btnReloadQuiz = (TextView) findViewById(R.id.btnReloadQuiz);
        btnClearLocal = (TextView) findViewById(R.id.btnClearLocal);
        tvSmbStatus = (TextView) findViewById(R.id.tvSmbStatus);
        btnSmbSetup = (TextView) findViewById(R.id.btnSmbSetup);
        btnSmbSyncNow = (TextView) findViewById(R.id.btnSmbSyncNow);
        btnChangePin = (TextView) findViewById(R.id.btnChangePin);
        btnBootLock = (TextView) findViewById(R.id.btnBootLock);
        tvHomeStatus = (TextView) findViewById(R.id.tvHomeStatus);
        btnAcc = (TextView) findViewById(R.id.btnAcc);
        tvAccStatus = (TextView) findViewById(R.id.tvAccStatus);
        btnOverlay = (TextView) findViewById(R.id.btnOverlay);
        btnSysSettings = (TextView) findViewById(R.id.btnSysSettings);
        btnHomeSetup = (TextView) findViewById(R.id.btnHomeSetup);
        tvHelp = (TextView) findViewById(R.id.tvHelp);
        btnFixAcc = (TextView) findViewById(R.id.btnFixAcc);
        btnTestLock = (TextView) findViewById(R.id.btnTestLock);
        gateLayer = (FrameLayout) findViewById(R.id.gateLayer);

        for (TextView v : new TextView[]{btnEnable, btnSingleMinus, btnSinglePlus,
                btnDailyMinus, btnDailyPlus, btnAwayMinus, btnAwayPlus,
                btnCorrMinus, btnCorrPlus, btnLevelMinus, btnLevelPlus,
                btnExportQuiz, btnReloadQuiz, btnClearLocal, btnSmbSetup, btnSmbSyncNow,
                btnChangePin, btnBootLock, btnAcc, btnOverlay, btnSysSettings,
                btnHomeSetup, btnFixAcc, btnTestLock}) {
            v.setClickable(true);
            v.setFocusable(true);
        }
    }

    // ---------------- 教材版本 ----------------

    private void buildEditionRow() {
        editionRow.removeAllViews();
        for (int i = 0; i < Prefs.EDITIONS.length; i++) {
            final String code = Prefs.EDITIONS[i];
            final TextView chip = new TextView(this);
            chip.setText(EDITION_LABELS[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            chip.setGravity(Gravity.CENTER);
            chip.setIncludeFontPadding(false);
            chip.setClickable(true);
            chip.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 84), 1f);
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.setTextbook(code);
                    refreshEditionRow();
                    refreshAll();
                }
            });
            editionRow.addView(chip);
        }
        refreshEditionRow();
    }

    private void refreshEditionRow() {
        if (editionRow == null) return;
        String cur = prefs.textbook();
        for (int i = 0; i < editionRow.getChildCount(); i++) {
            TextView chip = (TextView) editionRow.getChildAt(i);
            boolean on = Prefs.EDITIONS[i].equals(cur);
            chip.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_option);
            chip.setTextColor(getResources().getColor(R.color.text_main));
            chip.setTypeface(Typeface.create("sans-serif", on ? Typeface.BOLD : Typeface.NORMAL));
        }
    }

    // ---------------- 题型 ----------------

    private void buildTypeGrid() {
        typeGrid.removeAllViews();
        String[] names = {"口算", "认字", "古诗", "英语", "自定义", "数学", "语文",
                "物理", "化学", "生物", "历史", "地理", "政治"};
        int[] bits = {Prefs.TYPE_MATH, Prefs.TYPE_PINYIN, Prefs.TYPE_POETRY, Prefs.TYPE_ENGLISH,
                Prefs.TYPE_CUSTOM, Prefs.TYPE_MATH2, Prefs.TYPE_CHINESE, Prefs.TYPE_PHYSICS,
                Prefs.TYPE_CHEMISTRY, Prefs.TYPE_BIOLOGY, Prefs.TYPE_HISTORY,
                Prefs.TYPE_GEOGRAPHY, Prefs.TYPE_POLITICS};
        for (int start = 0; start < names.length; start += 4) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            for (int k = 0; k < 4; k++) {
                int idx = start + k;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 84), 1f);
                lp.setMargins(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
                if (idx < names.length) {
                    final TextView chip = new TextView(this);
                    final int bit = bits[idx];
                    final String name = names[idx];
                    chip.setText(name);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
                    chip.setGravity(Gravity.CENTER);
                    chip.setIncludeFontPadding(false);
                    chip.setBackgroundResource(R.drawable.bg_option);
                    chip.setTextColor(getResources().getColor(R.color.text_main));
                    chip.setClickable(true);
                    chip.setFocusable(true);
                    chip.setLayoutParams(lp);
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            prefs.setQuizTypes(prefs.quizTypes() ^ bit);
                            refreshTypeButtons();
                            refreshAll();
                        }
                    });
                    row.addView(chip);
                } else {
                    View blank = new View(this);
                    blank.setLayoutParams(lp);
                    row.addView(blank);
                }
            }
            typeGrid.addView(row);
        }
    }

    private void refreshTypeButtons() {
        String[] names = {"口算", "认字", "古诗", "英语", "自定义", "数学", "语文",
                "物理", "化学", "生物", "历史", "地理", "政治"};
        int[] bits = {Prefs.TYPE_MATH, Prefs.TYPE_PINYIN, Prefs.TYPE_POETRY, Prefs.TYPE_ENGLISH,
                Prefs.TYPE_CUSTOM, Prefs.TYPE_MATH2, Prefs.TYPE_CHINESE, Prefs.TYPE_PHYSICS,
                Prefs.TYPE_CHEMISTRY, Prefs.TYPE_BIOLOGY, Prefs.TYPE_HISTORY,
                Prefs.TYPE_GEOGRAPHY, Prefs.TYPE_POLITICS};
        int idx = 0;
        for (int r = 0; r < typeGrid.getChildCount(); r++) {
            LinearLayout row = (LinearLayout) typeGrid.getChildAt(r);
            for (int c = 0; c < row.getChildCount(); c++) {
                View v = row.getChildAt(c);
                if (!(v instanceof TextView) || idx >= names.length) continue;
                TextView chip = (TextView) v;
                boolean on = (prefs.quizTypes() & bits[idx]) != 0;
                chip.setText(on ? "✓ " + names[idx] : names[idx]);
                chip.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_option);
                chip.setTextColor(getResources().getColor(R.color.text_main));
                idx++;
            }
        }
    }

    // ---------------- 交互 ----------------

    private void wire() {
        btnEnable.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleMonitor(); }
        });
        btnSingleMinus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setSingleMinutes(clamp(prefs.singleMinutes() - 5, 5, 120)); refreshAll();
            }
        });
        btnSinglePlus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setSingleMinutes(clamp(prefs.singleMinutes() + 5, 5, 120)); refreshAll();
            }
        });
        btnDailyMinus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setDailyMinutes(clamp(prefs.dailyMinutes() - 30, 0, 600)); refreshAll();
            }
        });
        btnDailyPlus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setDailyMinutes(clamp(prefs.dailyMinutes() + 30, 0, 600)); refreshAll();
            }
        });
        btnAwayMinus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setAwayResetMinutes(stepAway(prefs.awayResetMinutes(), -15)); refreshAll();
            }
        });
        btnAwayPlus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setAwayResetMinutes(stepAway(prefs.awayResetMinutes(), 15)); refreshAll();
            }
        });
        btnCorrMinus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setRequiredCorrect(clamp(prefs.requiredCorrect() - 1, 1, 10)); refreshAll();
            }
        });
        btnCorrPlus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setRequiredCorrect(clamp(prefs.requiredCorrect() + 1, 1, 10)); refreshAll();
            }
        });
        btnLevelMinus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setGrade(clamp(prefs.grade() - 1, 1, 9)); refreshAll();
            }
        });
        btnLevelPlus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setGrade(clamp(prefs.grade() + 1, 1, 9)); refreshAll();
            }
        });
        btnExportQuiz.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmExportBuiltin(); }
        });
        btnReloadQuiz.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                engine = new QuizEngine(MainActivity.this, prefs);
                refreshAll();
                toast("题库已重新加载");
            }
        });
        btnClearLocal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClearLocal(); }
        });
        btnSmbSetup.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSmbDialog(); }
        });
        btnSmbSyncNow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!prefs.smbEnabled()) { toast("请先在 SMB 同步设置里配置并保存启用"); return; }
                doSync(true);
            }
        });
        btnChangePin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showChangePin(); }
        });
        btnBootLock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean v2 = !prefs.lockOnBoot();
                prefs.setLockOnBoot(v2);
                refreshAll();
                toast(v2 ? "已开启：重启电视后先答题才能看" : "已关闭：重启沿用重启前状态");
            }
        });
        btnOverlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (LockOverlay.canShow(MainActivity.this)) {
                    toast("悬浮层已授权：主页键切桌面会被直接盖住");
                } else {
                    openOverlaySettings();
                }
            }
        });
        btnAcc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openAccessibilitySettings(); }
        });
        btnFixAcc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openAccessibilitySettings(); }
        });
        btnSysSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openAppSettings(); }
        });
        btnHomeSetup.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showUninstallGuide(); }
        });
        btnTestLock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirm("立即锁定测试？", "马上弹出答题锁屏，答对 " + prefs.requiredCorrect()
                        + " 题后才会解开。\n（测试不会增加今日观看时长）", "锁定", new Runnable() {
                    @Override public void run() {
                        prefs.setLocked(true);
                        Keeper.launchLockNow(MainActivity.this);
                    }
                });
            }
        });
    }

    private void refreshAll() {
        tvStatus.setText(prefs.isLocked() ? "当前状态：锁定中" : "当前状态：未锁定");
        btnEnable.setText(prefs.isEnabled() ? "已开启（点击停用）" : "已停用（点击开启）");
        tvSingle.setText(String.valueOf(prefs.singleMinutes()));
        tvDaily.setText(String.valueOf(prefs.dailyMinutes()));
        tvAway.setText(prefs.awayResetMinutes() <= 0 ? "关闭" : (prefs.awayResetMinutes() + " 分钟"));
        tvCorrect.setText(String.valueOf(prefs.requiredCorrect()));
        tvLevel.setText(Prefs.gradeName(prefs.grade()));
        btnBootLock.setText(prefs.lockOnBoot() ? "开机先答题：开" : "开机先答题：关");
        refreshTypeButtons();
        refreshEditionRow();
        refreshQuizStatus();
        refreshAccStatus();
        refreshOverlayStatus();
        refreshHelp();
        tvHomeStatus.setText("系统桌面：未做任何修改 —— 本应用只负责锁屏，不接管桌面，卸载后小米桌面自动恢复原样。");
    }

    private void refreshQuizStatus() {
        int total = engine == null ? 0 : engine.questionCount();
        int sections = engine == null ? 0 : engine.sectionCount();
        int local = countLocalTxt();
        String src = local > 0 ? ("内置 + 本地 " + local + " 个文件") : "内置题库";
        if (total > 0) {
            tvQuizStatus.setText("已加载 " + total + " 道题（" + src + "），共 " + sections
                    + " 个知识段。当前教材：" + Prefs.editionName(prefs.textbook()));
        } else {
            tvQuizStatus.setText("题库未加载：请检查存储读取权限");
        }
        if (prefs.smbEnabled()) {
            long t = prefs.lastSmbSync();
            String last = t > 0 ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    .format(new Date(t)) : "尚未同步成功";
            tvSmbStatus.setText("SMB 已开启：" + prefs.smbHost() + "/" + prefs.smbShare()
                    + "\n上次更新：" + last);
        } else {
            tvSmbStatus.setText("SMB 同步未开启");
        }
    }

    private void refreshAccStatus() {
        boolean bound = KeyFilter.isBound(this);
        boolean filter = KeyFilter.hasKeyFilter(this);
        boolean effective = KeyFilter.isEffective(this);
        boolean overlay = LockOverlay.canShow(this);
        if (overlay) {
            tvAccStatus.setText("锁屏方式：✅ 悬浮层（主页键切桌面也会被直接盖住）");
            tvAccStatus.setTextColor(getResources().getColor(R.color.ok));
        } else if (bound && effective) {
            tvAccStatus.setText("锁屏方式：✅ 无障碍物理拦截（桌面不出现）");
            tvAccStatus.setTextColor(getResources().getColor(R.color.ok));
        } else if (bound && filter) {
            tvAccStatus.setText("锁屏方式：⚠ 无障碍秒级抢回（桌面会闪一下，建议开悬浮层）");
            tvAccStatus.setTextColor(getResources().getColor(R.color.warn));
        } else if (bound) {
            tvAccStatus.setText("无障碍：⚠ 服务在跑，但没有按键过滤；建议关掉再开一次");
            tvAccStatus.setTextColor(getResources().getColor(R.color.warn));
        } else {
            tvAccStatus.setText("无障碍：❌ 未开启 —— 建议开启，开机自启更可靠");
            tvAccStatus.setTextColor(getResources().getColor(R.color.danger));
        }
    }

    private void refreshOverlayStatus() {
        if (Build.VERSION.SDK_INT >= 23) {
            boolean ok = LockOverlay.canShow(this);
            btnOverlay.setText(ok ? "锁屏方式：悬浮层已授权（推荐）" : "锁屏方式：点此允许「显示在其他应用上层」");
        } else {
            btnOverlay.setText("锁屏方式：悬浮层（Android 4.3 安装即授权）");
        }
    }

    private void refreshHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("主页键：").append(LockOverlay.canShow(this) ? "悬浮层覆盖（桌面不出现）"
                : (KeyFilter.isEffective(this) ? "已物理拦截"
                : (KeyFilter.hasKeyFilter(this) ? "秒级抢回" : "未生效"))).append("\n");
        sb.append("无障碍：").append(KeyFilter.isBound(this) ? "已绑定" : "未开启").append("\n");
        sb.append("题库：").append(engine == null ? 0 : engine.questionCount()).append(" 道\n");
        sb.append("悬浮层权限：").append(LockOverlay.canShow(this) ? "已授权" : "未授权").append("\n\n");
        sb.append("主页键防回桌面失效时：点下面「去修复」，把无障碍开关关掉再打开一次。");
        tvHelp.setText(sb.toString());
    }

    // ---------------- 监控开关 ----------------

    private void toggleMonitor() {
        final boolean enabling = !prefs.isEnabled();
        confirm(enabling ? "开启守护？" : "停用守护？",
                enabling ? "开启后按设定时长计时，到点自动弹出答题锁屏。"
                        : "停用后不再计时、不再锁屏。\n家长设置仍然需要密码。",
                enabling ? "开启" : "停用", new Runnable() {
                    @Override public void run() {
                        prefs.setEnabled(enabling);
                        if (enabling) {
                            Keeper.startService(MainActivity.this);
                        } else {
                            prefs.setSessionStartMs(0);
                            prefs.setLastAliveMs(System.currentTimeMillis());
                            stopService(new Intent(MainActivity.this, QuizService.class));
                        }
                        refreshAll();
                        toast(enabling ? "已开启守护" : "已停用守护");
                    }
                });
    }

    // ---------------- 家长门禁 ----------------

    private void showGate() {
        if (authed) return;
        gateOpen = true;
        gateLayer.removeAllViews();
        gateLayer.setVisibility(View.VISIBLE);
        gateLayer.setBackgroundColor(0xF20F141B);
        gateLayer.setClickable(true);
        gateLayer.setFocusable(true);

        LinearLayout card = Ui.dialogPanel(this);
        TextView title = Ui.text(this, "家长验证", 26, Ui.color(this, R.color.text_main), true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        TextView sub = Ui.text(this, "输入家长密码后进入设置（默认 1234）", 18,
                Ui.color(this, R.color.text_dim), false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        card.addView(sub);

        final PinPad[] padRef = new PinPad[1];
        PinPad pad = new PinPad(this, "请输入 " + prefs.pin().length() + " 位家长密码",
                prefs.pin().length(), new PinPad.Listener() {
            @Override public void onEntered(String s) {
                if (s.equals(prefs.pin())) {
                    authed = true;
                    gateOpen = false;
                    gateLayer.setVisibility(View.GONE);
                    refreshAll();
                    btnEnable.requestFocus();
                } else {
                    padRef[0].showError("密码错误，请重试");
                }
            }

            @Override public void onCancelled() {
                // 门禁不可取消
            }
        });
        padRef[0] = pad;
        pad.setCancelVisible(false);
        card.addView(pad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(this, 500), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        gateLayer.addView(card, lp);
    }

    // ---------------- 通用对话框 ----------------

    private void confirm(String title, String message, String okText, final Runnable onOk) {
        LinearLayout panel = Ui.dialogPanel(this);
        panel.addView(Ui.text(this, title, 24, Ui.color(this, R.color.text_main), true));
        TextView msg = Ui.text(this, message, 18, Ui.color(this, R.color.text_dim), false);
        msg.setLineSpacing(Ui.dp(this, 5), 1f);
        msg.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 18));
        panel.addView(msg);

        final Dialog dlg = Ui.dialog(this, panel, 620);
        LinearLayout buttons = Ui.row(this);
        TextView cancel = Ui.button(this, "取消", false, 72);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); }
        });
        TextView ok = Ui.button(this, okText, true, 72);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); onOk.run(); }
        });
        buttons.addView(cancel);
        TextView gap = new TextView(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 14), 1));
        buttons.addView(ok);
        panel.addView(buttons);
        dlg.show();
        // 默认焦点放在「确认」上：少按一次方向键
        final TextView okRef = ok;
        ok.post(new Runnable() {
            @Override public void run() { okRef.requestFocus(); }
        });
    }

    private void showChangePin() {
        final String oldPin = prefs.pin();
        final int[] stage = {0};
        final String[] newPin = {""};

        LinearLayout panel = Ui.dialogPanel(this);
        TextView title = Ui.text(this, "修改家长密码", 24, Ui.color(this, R.color.text_main), true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        final Dialog dlg = Ui.dialog(this, panel, 520);
        final PinPad[] padRef = new PinPad[1];
        PinPad pad = new PinPad(this, "请输入当前密码", oldPin.length(), new PinPad.Listener() {
            @Override public void onEntered(String s) {
                PinPad p = padRef[0];
                if (stage[0] == 0) {
                    if (s.equals(oldPin)) {
                        stage[0] = 1;
                        p.setMaxLen(0);
                        p.setTitle("请输入新密码（4-8 位），按【确认】提交");
                    } else {
                        p.showError("密码错误，请重试");
                    }
                } else if (stage[0] == 1) {
                    if (s.length() < 4 || s.length() > 8) {
                        p.showError("密码需 4-8 位，请重新输入");
                        return;
                    }
                    newPin[0] = s;
                    stage[0] = 2;
                    p.setMaxLen(0);
                    p.setTitle("请再次输入新密码确认");
                } else {
                    if (s.equals(newPin[0])) {
                        prefs.setPin(newPin[0]);
                        dlg.dismiss();
                        toast("新密码已保存");
                    } else {
                        stage[0] = 1;
                        p.setMaxLen(0);
                        p.showError("两次不一致，请重新输入新密码");
                    }
                }
            }

            @Override public void onCancelled() { dlg.dismiss(); }
        });
        padRef[0] = pad;
        panel.addView(pad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dlg.show();
    }

    private void showSmbDialog() {
        LinearLayout panel = Ui.dialogPanel(this);
        panel.addView(Ui.text(this, "局域网 SMB 题库同步", 24, Ui.color(this, R.color.text_main), true));
        TextView tip = Ui.text(this, "填电脑的 IP 和共享文件夹名，保存后会立即同步一次。", 16,
                Ui.color(this, R.color.text_dim), false);
        tip.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        panel.addView(tip);

        EditText host = addField(panel, "服务器 IP（如 192.168.1.100）", prefs.smbHost(), true);
        EditText share = addField(panel, "共享名（如 share）", prefs.smbShare(), true);
        EditText user = addField(panel, "用户名（可留空）", prefs.smbUser(), true);
        EditText pass = addField(panel, "密码", prefs.smbPass(), true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText path = addField(panel, "共享内子目录（可留空）", prefs.smbPath(), false);

        ScrollView sv = new ScrollView(this);
        sv.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final Dialog dlg = Ui.dialog(this, sv, 640, 480);

        LinearLayout buttons = Ui.row(this);
        TextView off = Ui.button(this, "停用", false, 64);
        TextView save = Ui.button(this, "保存并同步", true, 64);
        off.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setSmbEnabled(false);
                prefs.setSmbManifest("");
                dlg.dismiss();
                refreshAll();
                toast("已停用 SMB 同步");
            }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setSmbHost(host.getText().toString().trim());
                prefs.setSmbShare(share.getText().toString().trim());
                prefs.setSmbUser(user.getText().toString().trim());
                prefs.setSmbPass(pass.getText().toString());
                String p = path.getText().toString().trim();
                prefs.setSmbPath(p.isEmpty() ? "/" : p);
                prefs.setSmbEnabled(true);
                dlg.dismiss();
                refreshAll();
                toast("已保存，正在同步题库…");
                doSync(true);
            }
        });
        buttons.addView(off);
        TextView gap = new TextView(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 14), 1));
        buttons.addView(save);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 14);
        panel.addView(buttons, blp);
        dlg.show();
    }

    private EditText addField(LinearLayout root, String label, String value, boolean next) {
        TextView tv = Ui.text(this, label, 16, Ui.color(this, R.color.text_dim), false);
        tv.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        root.addView(tv);
        EditText et = new EditText(this);
        et.setText(value);
        et.setTextColor(Ui.color(this, R.color.text_main));
        et.setHintTextColor(Ui.color(this, R.color.text_faint));
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        et.setSingleLine(true);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        et.setImeOptions(next ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        root.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private void doSync(final boolean withToast) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final int n = SMBHelper.syncOnce(MainActivity.this, prefs);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (n > 0) {
                                prefs.setLastSmbSync(System.currentTimeMillis());
                                if (withToast) toast("更新成功，更新 " + n + " 个文件");
                            } else if (n == 0 && withToast) {
                                toast("题库没有变化，无需更新");
                            } else if (n < 0 && withToast) {
                                toast("连接失败，请检查地址/账号/网络");
                            }
                            engine = new QuizEngine(MainActivity.this, prefs);
                            refreshAll();
                        }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (withToast) toast("更新失败：" + e);
                        }
                    });
                }
            }
        }).start();
    }

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

    private void confirmExportBuiltin() {
        final File dir = new File(QuizLoader.DIR, "内置题库");
        int exist = 0;
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".txt")) exist++;
            }
        }
        if (exist <= 0) { doExportBuiltin(dir); return; }
        confirm("覆盖导出目录里的文件？", "目录里已经有 " + exist + " 个 txt。\n"
                + "导出会用内置题库原文覆盖同名文件，你改过的内容会丢。", "确定覆盖", new Runnable() {
            @Override public void run() { doExportBuiltin(dir); }
        });
    }

    private void doExportBuiltin(File dir) {
        int n = QuizLoader.exportBuiltin(this, dir);
        if (n > 0) {
            toast("已导出 " + n + " 个题库文件到：\n" + dir.getAbsolutePath());
        } else {
            toast("导出失败，请检查存储写入权限");
        }
        refreshAll();
    }

    private void confirmClearLocal() {
        final int existing = countLocalTxt();
        if (existing <= 0) { toast("本地题库目录里没有 txt 文件"); return; }
        confirm("确认删除本地题库？", "只删除 " + QuizLoader.DIR + " 里的 " + existing
                + " 个 txt 文件。内置题库是只读的，删不到也改不到。", "确定删除", new Runnable() {
            @Override public void run() {
                File dir = new File(QuizLoader.DIR);
                int n = 0;
                File[] fs = dir.listFiles();
                if (fs != null) {
                    for (File f : fs) {
                        if (f.isFile() && f.getName().toLowerCase().endsWith(".txt") && f.delete()) n++;
                    }
                }
                engine = new QuizEngine(MainActivity.this, prefs);
                refreshAll();
                toast("已删除 " + n + " 个本地题库文件");
            }
        });
    }

    private void showUninstallGuide() {
        confirm("还原并卸载", "本应用没有修改系统桌面，也没有禁用小米桌面：\n\n"
                + "• 卸载后主页键立刻回到小米电视桌面；\n"
                + "• 答题记录、家长密码、题库设置会一起删除。\n\n"
                + "建议先到无障碍设置里关掉 txhlock，再卸载。", "去卸载", new Runnable() {
            @Override public void run() {
                try {
                    startActivity(new Intent(Intent.ACTION_DELETE,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    toast("请到 设置 → 应用 → 答题锁屏 里卸载");
                }
            }
        });
    }

    // ---------------- 系统页面 ----------------

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("把 txhlock（答题锁屏）的开关关掉再打开一次");
        } catch (Exception e) {
            toast("请到 设置 → 无障碍 里操作");
        }
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            toast("打开「显示在其他应用上层 / 悬浮窗」后返回");
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception e2) {
                toast("请到 设置 → 应用 → 特殊权限 → 悬浮窗 里授权");
            }
        }
    }

    private void openAppSettings() {
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
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int stepAway(int cur, int delta) {
        if (delta < 0) return cur <= 0 ? 0 : Math.max(0, cur - 15);
        return Math.min(720, (cur <= 0 ? 0 : cur) + 15);
    }

    // ---------------- 生命周期 ----------------

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null && !prefs.isLocked()) refreshAll();
    }

    @Override protected void onStart() {
        super.onStart();
        if (authed && leftAtMs > 0 && System.currentTimeMillis() - leftAtMs > 8L * 60L * 1000L) {
            authed = false;
        }
        if (!authed && !gateOpen && prefs != null && !prefs.isLocked()) {
            showGate();
        }
    }

    @Override protected void onStop() {
        super.onStop();
        leftAtMs = System.currentTimeMillis();
        if (prefs != null && prefs.isLocked() && !isFinishing()) {
            finish();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (prefs != null && prefs.isLocked() && !isFinishing()) {
            finish();
        }
    }
}
