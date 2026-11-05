package com.kidslock;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
 * 家长设置主页（v2.0 卡片式仪表盘）。
 *
 * 布局：一屏放完，不再是一条长长的滚动列表 ——
 *   顶部：标题 + 守护开关 + 版本
 *   三张数据卡：今日已看 / 单次时长 / 连对解锁
 *   四张设置卡：观看时长 / 答题规则 / 题库管理 / 家长与防绕过
 *   底部：帮助与诊断 / 立即锁定测试
 *
 * 每张卡点开是一个居中的卡片弹层（可滚动），关闭后焦点回到原来那张卡。
 * 全应用只用一套间距、圆角、字号，保证"对称、统一"。
 */
public class MainActivity extends Activity {

    private Prefs prefs;
    private QuizEngine engine;

    private FrameLayout root, sheetLayer, gateLayer;
    private LinearLayout home;
    private TextView tvVersion, tvStatusPill;
    private TextView statToday, statTodaySub, statSingle, statSingleSub, statCorrect, statCorrectSub;
    private View barFill;
    private LinearLayout cardTime, cardQuiz, cardBank, cardGuard;
    private TextView btnHelp, btnTestLock;

    /** 家长验证状态：进入后台超过 8 分钟再回来要重新验证一次，避免来回切应用一直输密码。 */
    private boolean authed = false;
    private long leftAtMs = 0L;

    private boolean gateOpen = false;
    private boolean sheetOpen = false;
    private View lastFocus;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.wrap(base));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        // 当前处于锁定状态：直接进入锁屏答题页（家长也需先解锁）
        if (prefs.isLocked()) {
            startActivity(new Intent(this, LockScreenActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return;
        }
        getWindow().setBackgroundDrawableResource(R.color.bg_dark);
        setContentView(R.layout.activity_main);
        applyFullscreen();

        bind();
        buildHero();
        buildGrid();
        wire();

        if (prefs.isEnabled()) Keeper.startService(this);
        engine = new QuizEngine(this, prefs);
        refreshAll();
        maybeAskNotification();
        showGate();
    }

    /**
     * 电视上不需要状态栏/导航栏，全屏显示卡片界面。
     * 4.3 只有 FLAG_FULLSCREEN；5.0 起再用 immersive 把导航栏也藏掉。
     */
    private void applyFullscreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
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

    // ================= 构建界面 =================

    private void bind() {
        root = (FrameLayout) findViewById(R.id.root);
        home = (LinearLayout) findViewById(R.id.home);
        sheetLayer = (FrameLayout) findViewById(R.id.sheetLayer);
        gateLayer = (FrameLayout) findViewById(R.id.gateLayer);
        tvVersion = (TextView) findViewById(R.id.tvVersion);
        tvStatusPill = (TextView) findViewById(R.id.tvStatusPill);
        btnHelp = (TextView) findViewById(R.id.btnHelp);
        btnTestLock = (TextView) findViewById(R.id.btnTestLock);
        tvVersion.setText(AppVer.VERSION);
        Ui.focusScale(tvStatusPill);
        Ui.focusScale(btnHelp);
        Ui.focusScale(btnTestLock);
    }

    /** 顶部三张数据卡。 */
    private void buildHero() {
        LinearLayout hero = (LinearLayout) findViewById(R.id.hero);
        LayoutInflater inf = LayoutInflater.from(this);

        LinearLayout c1 = (LinearLayout) inf.inflate(R.layout.item_stat, hero, false);
        statToday = (TextView) c1.findViewById(R.id.statLabel);
        statTodaySub = (TextView) c1.findViewById(R.id.statSub);
        statToday.setText("今日已看");
        ((TextView) c1.findViewById(R.id.statValue)).setText("0 分钟");
        statToday.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        // 今日卡里加一条进度条：今日已看 / 每日上限
        LinearLayout track = new LinearLayout(this);
        track.setBackgroundResource(R.drawable.bg_bar_track);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
        tlp.topMargin = Ui.dp(this, 8);
        track.setLayoutParams(tlp);
        track.setWeightSum(1000);
        barFill = new View(this);
        barFill.setBackgroundResource(R.drawable.bg_bar_fill);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        flp.weight = 0f;
        barFill.setLayoutParams(flp);
        track.addView(barFill);
        c1.addView(track);

        // 今日卡的数值控件单独存起来
        statToday.setTag(c1.findViewById(R.id.statValue));
        c1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openSheet("观看时长", "到点自动锁屏，答对后重新计时", sheetTime());
            }
        });
        Ui.focusScale(c1);
        addToHero(hero, c1, false);

        LinearLayout c2 = (LinearLayout) inf.inflate(R.layout.item_stat, hero, false);
        ((TextView) c2.findViewById(R.id.statLabel)).setText("单次连续观看");
        statSingle = (TextView) c2.findViewById(R.id.statValue);
        statSingleSub = (TextView) c2.findViewById(R.id.statSub);
        c2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openSheet("观看时长", "到点自动锁屏，答对后重新计时", sheetTime());
            }
        });
        Ui.focusScale(c2);
        addToHero(hero, c2, true);

        LinearLayout c3 = (LinearLayout) inf.inflate(R.layout.item_stat, hero, false);
        ((TextView) c3.findViewById(R.id.statLabel)).setText("连对解锁");
        statCorrect = (TextView) c3.findViewById(R.id.statValue);
        statCorrectSub = (TextView) c3.findViewById(R.id.statSub);
        c3.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openSheet("答题规则", "连续答对几题才能继续看", sheetQuiz());
            }
        });
        Ui.focusScale(c3);
        addToHero(hero, c3, true);
    }

    private void addToHero(LinearLayout hero, View card, boolean withGap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (withGap) lp.leftMargin = Ui.dp(this, 14);
        hero.addView(card, lp);
    }

    /** 2x2 设置卡片。 */
    private void buildGrid() {
        LinearLayout row1 = (LinearLayout) findViewById(R.id.gridRow1);
        LinearLayout row2 = (LinearLayout) findViewById(R.id.gridRow2);
        cardTime = addCard(row1, "观看时长", "单次 20 分钟 · 每日不限", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                openSheet("观看时长", "单次、每日上限、关机重置", sheetTime());
            }
        });
        cardQuiz = addCard(row1, "答题规则", "连对 3 题 · 二年级", true, new View.OnClickListener() {
            @Override public void onClick(View v) {
                openSheet("答题规则", "题数、年级、题型都可以改", sheetQuiz());
            }
        });
        cardBank = addCard(row2, "题库管理", "内置题库 · 958 道题", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                openSheet("题库管理", "内置只读 · 支持本地与局域网补题", sheetBank());
            }
        });
        cardGuard = addCard(row2, "家长与防绕过", "密码 4 位 · 开机先答题", true, new View.OnClickListener() {
            @Override public void onClick(View v) {
                openSheet("家长与防绕过", "密码、开机锁定、无障碍、卸载", sheetGuard());
            }
        });
    }

    private LinearLayout addCard(LinearLayout parent, String title, String sub,
                                 boolean right, View.OnClickListener l) {
        LinearLayout card = (LinearLayout) LayoutInflater.from(this)
                .inflate(R.layout.item_card, parent, false);
        ((TextView) card.findViewById(R.id.cardTitle)).setText(title);
        ((TextView) card.findViewById(R.id.cardSub)).setText(sub);
        card.setTag(sub);
        card.setOnClickListener(l);
        Ui.focusScale(card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (right) lp.leftMargin = Ui.dp(this, 14);
        parent.addView(card, lp);
        return card;
    }

    private void wire() {
        tvStatusPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleMonitor();
            }
        });
        btnHelp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openSheet("帮助与诊断", "常见问题、主页键、权限状态", sheetHelp());
            }
        });
        btnTestLock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                lockNow();
            }
        });
    }

    // ================= 刷新数据 =================

    private void refreshAll() {
        refreshStatus();
        refreshStats();
        refreshCardSubs();
    }

    private void refreshStatus() {
        boolean on = prefs.isEnabled();
        tvStatusPill.setText(on ? "● 守护中" : "○ 已停用");
        tvStatusPill.setTextColor(Ui.color(this,
                on ? R.color.accent : R.color.text_dim));
        tvStatusPill.setBackgroundResource(on ? R.drawable.bg_pill_on : R.drawable.bg_pill);
    }

    private void refreshStats() {
        long watched = prefs.todayWatchedMs();
        long sessionExtra = 0;
        long start = prefs.sessionStartMs();
        if (prefs.isEnabled() && start > 0 && !prefs.isLocked()) {
            sessionExtra = Math.max(0, System.currentTimeMillis() - start);
        }
        long todayMin = (watched + sessionExtra) / 60000L;
        ((TextView) statToday.getTag()).setText(todayMin + " 分钟");

        int daily = prefs.dailyMinutes();
        if (daily > 0) {
            statTodaySub.setText("每日上限 " + daily + " 分钟");
            Ui.setProgress(barFill, Math.min(1f, (float) todayMin / daily));
        } else {
            statTodaySub.setText("每日不限 · 仅按单次计时");
            Ui.setProgress(barFill, 0f);
        }

        statSingle.setText(prefs.singleMinutes() + " 分钟");
        statSingleSub.setText("到点锁屏 · 答对后重新计时");
        statCorrect.setText(prefs.requiredCorrect() + " 题");
        statCorrectSub.setText(Prefs.gradeName(prefs.grade()) + " · " + typeCount() + " 类题型");
    }

    private void refreshCardSubs() {
        String daily = prefs.dailyMinutes() > 0 ? ("每日 " + prefs.dailyMinutes() + " 分钟") : "每日不限";
        setCardSub(cardTime, "单次 " + prefs.singleMinutes() + " 分钟 · " + daily);
        setCardSub(cardQuiz, "连对 " + prefs.requiredCorrect() + " 题 · "
                + Prefs.gradeName(prefs.grade()) + " · " + typeCount() + " 类题型");
        int total = engine == null ? 0 : engine.questionCount();
        setCardSub(cardBank, total > 0 ? ("已加载 " + total + " 道题 · 内置只读") : "题库未加载 · 请检查权限");
        setCardSub(cardGuard, "家长密码 · 开机先答题：" + (prefs.lockOnBoot() ? "开" : "关"));
    }

    private void setCardSub(LinearLayout card, String text) {
        TextView sub = (TextView) card.findViewById(R.id.cardSub);
        sub.setText(text);
        card.setTag(text);
    }

    private int typeCount() {
        int t = prefs.quizTypes();
        int n = 0;
        for (int i = 0; i < 13; i++) if ((t & (1 << i)) != 0) n++;
        return n;
    }

    // ================= 家长门禁 =================

    private void showGate() {
        if (authed) return;
        gateOpen = true;
        home.setVisibility(View.INVISIBLE);
        gateLayer.removeAllViews();
        gateLayer.setVisibility(View.VISIBLE);
        gateLayer.setBackgroundColor(0xF2070A0F);

        LinearLayout card = Ui.dialogPanel(this);
        card.setBackgroundResource(R.drawable.bg_sheet);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = Ui.text(this, "家长验证", 26, Ui.color(this, R.color.text_main), true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView sub = Ui.text(this, "输入家长密码后进入设置（默认 1234）", 14,
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
                    home.setVisibility(View.VISIBLE);
                    refreshAll();
                    tvStatusPill.requestFocus();
                } else {
                    padRef[0].showError("密码错误，请重试");
                }
            }

            @Override public void onCancelled() {
                // 门禁不可取消：必须输对才能进设置
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

    // ================= 弹层框架 =================

    /**
     * 打开一个卡片弹层。
     * 主界面先变 INVISIBLE（不可见 = 不能获得焦点），保证遥控器不会跑到背后的卡片上。
     */
    private void openSheet(String title, String subtitle, View content) {
        lastFocus = getCurrentFocus();
        sheetOpen = true;
        home.setVisibility(View.INVISIBLE);
        sheetLayer.removeAllViews();
        sheetLayer.setVisibility(View.VISIBLE);
        sheetLayer.setBackgroundResource(R.drawable.bg_scrim);

        LinearLayout card = Ui.column(this);
        card.setBackgroundResource(R.drawable.bg_sheet);
        card.setPadding(Ui.dp(this, 34), Ui.dp(this, 22), Ui.dp(this, 34), Ui.dp(this, 18));

        // 标题行
        LinearLayout header = Ui.row(this);
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.text(this, title, 24, Ui.color(this, R.color.text_main), true));
        if (subtitle != null) {
            TextView s = Ui.text(this, subtitle, 13, Ui.color(this, R.color.text_dim), false);
            s.setPadding(0, Ui.dp(this, 4), 0, 0);
            titles.addView(s);
        }
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = Ui.text(this, "关闭", 16, Ui.color(this, R.color.text_dim), false);
        close.setBackgroundResource(R.drawable.bg_pill);
        close.setGravity(Gravity.CENTER);
        close.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 8));
        close.setClickable(true);
        close.setFocusable(true);
        Ui.focusScale(close);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeSheet();
            }
        });
        header.addView(close);
        card.addView(header);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        // 上下边缘淡出：内容比弹层高时，边缘会渐隐，提示"还能往下翻"
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(Ui.dp(this, 26));
        // 内容底部留一点白：最后一行不会被卡片边缘生硬截断
        if (content instanceof LinearLayout) {
            View bottom = new View(this);
            ((LinearLayout) content).addView(bottom, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 22)));
        }
        sv.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        svlp.topMargin = Ui.dp(this, 6);
        card.addView(sv, svlp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(this, 780), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.CENTER;
        lp.topMargin = Ui.dp(this, 26);
        lp.bottomMargin = Ui.dp(this, 26);
        sheetLayer.addView(card, lp);

        // 焦点：优先给内容里的第一个可聚焦控件，没有就落在"关闭"上
        final View first = findFirstFocusable(content);
        card.post(new Runnable() {
            @Override public void run() {
                if (first != null) first.requestFocus();
            }
        });
    }

    private void closeSheet() {
        sheetOpen = false;
        sheetLayer.removeAllViews();
        sheetLayer.setVisibility(View.GONE);
        home.setVisibility(View.VISIBLE);
        if (lastFocus != null) lastFocus.requestFocus();
    }

    /** 深度优先找第一个可见、可聚焦的控件。 */
    private View findFirstFocusable(View v) {
        if (v == null) return null;
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v.isFocusable() && v instanceof TextView) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findFirstFocusable(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    @Override public void onBackPressed() {
        if (sheetOpen) {
            closeSheet();
            return;
        }
        if (gateOpen) return;      // 门禁必须输密码
        super.onBackPressed();
    }

    // ================= 弹层内容 =================

    /** 观看时长。 */
    private View sheetTime() {
        LinearLayout c = Ui.column(this);

        c.addView(Ui.sectionLabel(this, "单次连续观看"));
        final LinearLayout[] h1 = new LinearLayout[1];
        h1[0] = stepperRow("到点自动锁屏", "答对后重新计时",
                prefs.singleMinutes() + " 分钟",
                new Runnable() {
                    @Override public void run() {
                        prefs.setSingleMinutes(clamp(prefs.singleMinutes() - 5, 5, 120));
                        updateStepper(h1[0], prefs.singleMinutes() + " 分钟");
                        refreshAll();
                    }
                },
                new Runnable() {
                    @Override public void run() {
                        prefs.setSingleMinutes(clamp(prefs.singleMinutes() + 5, 5, 120));
                        updateStepper(h1[0], prefs.singleMinutes() + " 分钟");
                        refreshAll();
                    }
                });
        c.addView(h1[0]);

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "每日累计上限"));
        final LinearLayout[] h2 = new LinearLayout[1];
        h2[0] = stepperRow("每天最多看多久", "0 = 不限",
                prefs.dailyMinutes() <= 0 ? "不限" : (prefs.dailyMinutes() + " 分钟"),
                new Runnable() {
                    @Override public void run() {
                        prefs.setDailyMinutes(clamp(prefs.dailyMinutes() - 30, 0, 600));
                        updateStepper(h2[0], prefs.dailyMinutes() <= 0 ? "不限" : (prefs.dailyMinutes() + " 分钟"));
                        refreshAll();
                    }
                },
                new Runnable() {
                    @Override public void run() {
                        prefs.setDailyMinutes(clamp(prefs.dailyMinutes() + 30, 0, 600));
                        updateStepper(h2[0], prefs.dailyMinutes() <= 0 ? "不限" : (prefs.dailyMinutes() + " 分钟"));
                        refreshAll();
                    }
                });
        c.addView(h2[0]);

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "关机 / 待机重置"));
        final LinearLayout[] h3 = new LinearLayout[1];
        h3[0] = stepperRow("离开超过多久算休息", "0 = 关闭（仅跨夜清零）",
                prefs.awayResetMinutes() <= 0 ? "关闭" : (prefs.awayResetMinutes() + " 分钟"),
                new Runnable() {
                    @Override public void run() {
                        prefs.setAwayResetMinutes(stepAway(prefs.awayResetMinutes(), -15));
                        updateStepper(h3[0], prefs.awayResetMinutes() <= 0 ? "关闭" : (prefs.awayResetMinutes() + " 分钟"));
                        refreshAll();
                    }
                },
                new Runnable() {
                    @Override public void run() {
                        prefs.setAwayResetMinutes(stepAway(prefs.awayResetMinutes(), 15));
                        updateStepper(h3[0], prefs.awayResetMinutes() <= 0 ? "关闭" : (prefs.awayResetMinutes() + " 分钟"));
                        refreshAll();
                    }
                });
        c.addView(h3[0]);

        c.addView(Ui.hint(this, "「单次连续观看」只看这一次连着看了多久；「每日累计上限」统计一整天。"
                + "关机/待机不到设定时长：计时暂停接着走；超过或跨夜：清零重来。"));
        return c;
    }

    /** 答题规则：题数 / 年级 / 题型。 */
    private View sheetQuiz() {
        LinearLayout c = Ui.column(this);

        c.addView(Ui.sectionLabel(this, "连续答对题数"));
        final LinearLayout[] h1 = new LinearLayout[1];
        h1[0] = stepperRow("答对几题才解锁", "答错一题清零重来",
                prefs.requiredCorrect() + " 题",
                new Runnable() {
                    @Override public void run() {
                        prefs.setRequiredCorrect(clamp(prefs.requiredCorrect() - 1, 1, 10));
                        updateStepper(h1[0], prefs.requiredCorrect() + " 题");
                        refreshAll();
                    }
                },
                new Runnable() {
                    @Override public void run() {
                        prefs.setRequiredCorrect(clamp(prefs.requiredCorrect() + 1, 1, 10));
                        updateStepper(h1[0], prefs.requiredCorrect() + " 题");
                        refreshAll();
                    }
                });
        c.addView(h1[0]);

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "孩子年级"));
        final LinearLayout[] h2 = new LinearLayout[1];
        h2[0] = stepperRow("只出该年级及以下的题", "不会超纲",
                Prefs.gradeName(prefs.grade()),
                new Runnable() {
                    @Override public void run() {
                        prefs.setGrade(clamp(prefs.grade() - 1, 1, 9));
                        updateStepper(h2[0], Prefs.gradeName(prefs.grade()));
                        refreshAll();
                    }
                },
                new Runnable() {
                    @Override public void run() {
                        prefs.setGrade(clamp(prefs.grade() + 1, 1, 9));
                        updateStepper(h2[0], Prefs.gradeName(prefs.grade()));
                        refreshAll();
                    }
                });
        c.addView(h2[0]);

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "启用题型（可多选）"));

        String[] names = {"口算", "认字", "古诗", "英语", "自定义", "数学",
                "语文", "物理", "化学", "生物", "历史", "地理", "政治"};
        int[] bits = {Prefs.TYPE_MATH, Prefs.TYPE_PINYIN, Prefs.TYPE_POETRY, Prefs.TYPE_ENGLISH,
                Prefs.TYPE_CUSTOM, Prefs.TYPE_MATH2, Prefs.TYPE_CHINESE, Prefs.TYPE_PHYSICS,
                Prefs.TYPE_CHEMISTRY, Prefs.TYPE_BIOLOGY, Prefs.TYPE_HISTORY,
                Prefs.TYPE_GEOGRAPHY, Prefs.TYPE_POLITICS};

        LinearLayout grid = Ui.column(this);
        for (int start = 0; start < names.length; start += 3) {
            LinearLayout row = Ui.row(this);
            for (int k = 0; k < 3; k++) {
                int idx = start + k;
                if (idx < names.length) {
                    final boolean on = (prefs.quizTypes() & bits[idx]) != 0;
                    final String name = names[idx];
                    final TextView chip = Ui.chip(this, on ? "✓ " + name : name, on);
                    final int bit = bits[idx];
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            prefs.setQuizTypes(prefs.quizTypes() ^ bit);
                            boolean nowOn = (prefs.quizTypes() & bit) != 0;
                            chip.setText(nowOn ? "✓ " + name : name);
                            chip.setBackgroundResource(nowOn ? R.drawable.bg_chip_on : R.drawable.bg_chip);
                            chip.setTextColor(Ui.color(MainActivity.this,
                                    nowOn ? R.color.text_main : R.color.text_dim));
                            refreshAll();
                        }
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    lp.setMargins(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
                    row.addView(chip, lp);
                } else {
                    View blank = new View(this);
                    row.addView(blank, new LinearLayout.LayoutParams(0, 1, 1f));
                }
            }
            grid.addView(row);
        }
        c.addView(grid);

        c.addView(Ui.hint(this, "口算是自动生成的算术题，不需要题库；其余学科从内置题库按年级取题。"
                + "小学阶段建议只开语数英，物理化学生物等初中科目可以关掉。"));
        return c;
    }

    /** 题库管理。 */
    private View sheetBank() {
        ensureStoragePermission();
        LinearLayout c = Ui.column(this);

        int total = engine == null ? 0 : engine.questionCount();
        int sections = engine == null ? 0 : engine.sectionCount();
        int local = countLocalTxt();

        TextView status = Ui.text(this, "已加载 " + total + " 道题 · " + sections + " 个知识段", 18,
                Ui.color(this, R.color.text_main), true);
        status.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        c.addView(status);
        TextView src = Ui.text(this, local > 0 ? ("内置题库 + 本地 " + local + " 个文件")
                : "内置题库（APK 自带，只读）", 13, Ui.color(this, R.color.text_dim), false);
        c.addView(src);

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "局域网 SMB 同步"));
        c.addView(actionRow("SMB 题库同步设置", prefs.smbEnabled()
                ? ("已开启 · " + prefs.smbHost() + "/" + prefs.smbShare())
                : "未开启 · 打开应用时自动拉取电脑共享的 txt", new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeSheet();
                showSmbDialog();
            }
        }));
        if (Build.VERSION.SDK_INT < 19) {
            c.addView(Ui.hint(this, "当前电视是 Android 4.3：SMB 组件在 4.3 上可能不可用。"
                    + "更稳的做法是把题库 txt 拷到 U 盘，放进电视的 /sdcard/Download/txhlock/ 目录。"));
        }

        // Android 11+ 读 /sdcard/Download 需要"所有文件访问"权限，这里按需引导，不再每次启动都跳系统页
        if (Build.VERSION.SDK_INT >= 30 && !isAllFilesAccess()) {
            c.addView(actionRow("授予题库读取权限", "读取 Download 里的自建题库（Android 11+）",
                    new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            try {
                                startActivity(new Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:" + getPackageName())));
                            } catch (Exception e) {
                                try {
                                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }));
        }

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "维护"));
        c.addView(actionRow("重新加载题库", "导入新题后立即生效", new View.OnClickListener() {
            @Override public void onClick(View v) {
                engine = new QuizEngine(MainActivity.this, prefs);
                closeSheet();
                refreshAll();
                toast("题库已重新加载");
            }
        }));
        c.addView(actionRow("导出内置题库", "导出成 txt，照着格式自己加题", new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeSheet();
                confirmExportBuiltin();
            }
        }));
        c.addView(actionRow("删除本地题库文件", "只删 /sdcard/Download/txhlock 里的 txt，不动内置", new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeSheet();
                confirmClearLocal();
            }
        }));

        c.addView(Ui.hint(this, "自建题库放在 /sdcard/Download/txhlock/*.txt，格式：\n"
                + "[科目@g年级] 换行 题干|答案|干扰项1;干扰项2;干扰项3\n"
                + "内置题库最先加载，同段落同题干的外部题会被自动跳过 —— 外部题只能新增，永远顶不掉内置题。"));
        return c;
    }

    /** 家长与防绕过。 */
    private View sheetGuard() {
        LinearLayout c = Ui.column(this);

        c.addView(Ui.sectionLabel(this, "家长"));
        c.addView(actionRow("修改家长密码", "当前密码 " + prefs.pin().length() + " 位", new View.OnClickListener() {
            @Override public void onClick(View v) {
                closeSheet();
                showChangePin();
            }
        }));

        c.addView(actionRow(prefs.lockOnBoot() ? "开机先答题：已开启" : "开机先答题：已关闭",
                "开启后重启电视也要先答一题，防止靠重启绕过",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        prefs.setLockOnBoot(!prefs.lockOnBoot());
                        closeSheet();
                        refreshAll();
                        toast(prefs.lockOnBoot()
                                ? "已开启：重启电视后先答题才能看"
                                : "已关闭：重启后沿用重启前的锁定状态");
                    }
                }));

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "防绕过（小米电视建议开启）"));

        boolean bound = KeyFilter.isBound(this);
        boolean filter = KeyFilter.hasKeyFilter(this);
        boolean effective = KeyFilter.isEffective(this);
        String accTitle;
        String accSub;
        if (bound && effective) {
            accTitle = "无障碍保活：已开启";
            accSub = "主页键已物理拦截 · 切走会立即抢回";
        } else if (bound && filter) {
            // 小米电视常见：声明了按键过滤，但系统根本不把 HOME 事件发过来。
            // 不吹"已拦截"，说明现在靠秒级抢回，按一下主页键就能实测。
            accTitle = "无障碍保活：已开启（主页键秒级抢回）";
            accSub = "部分小米电视不派发主页键事件 · 按一下主页键可验证是否被吞";
        } else if (bound) {
            accTitle = "无障碍保活：服务在跑，但没有按键过滤";
            accSub = "主页键只能靠 0.8 秒抢回，建议关掉再开一次";
        } else {
            accTitle = "无障碍保活：未开启";
            accSub = "不开启的话主页键拦不住，开机也可能不自启";
        }
        c.addView(actionRow(accTitle, accSub, new View.OnClickListener() {
            @Override public void onClick(View v) {
                openAccessibilitySettings();
            }
        }));

        c.addView(actionRow("打开系统设置", "自启动 / 后台运行 / 权限", new View.OnClickListener() {
            @Override public void onClick(View v) {
                openAppSettings();
            }
        }));

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "还原"));
        c.addView(actionRowDanger("还原并卸载", "先关无障碍，再卸载；小米桌面自动恢复",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        closeSheet();
                        showUninstallGuide();
                    }
                }));

        c.addView(Ui.hint(this, "本应用对系统桌面零改动：不接管桌面、不修改系统设置。"
                + "卸载后遥控器主页键立刻回到小米电视桌面，与安装前完全一致。"));
        return c;
    }

    /** 帮助与诊断。 */
    private View sheetHelp() {
        LinearLayout c = Ui.column(this);

        c.addView(Ui.sectionLabel(this, "状态诊断"));
        c.addView(diagLine("无障碍服务", KeyFilter.isBound(this) ? "已绑定" : "未开启",
                KeyFilter.isBound(this)));
        c.addView(diagLine("主页键拦截", KeyFilter.isEffective(this) ? "已物理拦截"
                        : (KeyFilter.hasKeyFilter(this) ? "秒级抢回（按主页键验证）" : "未生效"),
                KeyFilter.isEffective(this)));
        c.addView(diagLine("题库", (engine == null ? 0 : engine.questionCount()) + " 道题已加载",
                engine != null && engine.questionCount() > 0));
        c.addView(diagLine("守护开关", prefs.isEnabled() ? "开启中" : "已停用", prefs.isEnabled()));

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "主页键防回桌面失效了？"));
        c.addView(Ui.hint(this, "1. 点下面「去修复」，进入系统的无障碍设置；\n"
                + "2. 找到 txhlock（答题锁屏），把开关关掉，再重新打开；\n"
                + "3. 回到本页看状态：\n"
                + "   ·「已物理拦截」= 最好，按主页键桌面完全不出现；\n"
                + "   ·「秒级抢回」= 也正常，这台电视的固件不把主页键事件发给应用，\n"
                + "     锁屏会在 0.8 秒内把桌面盖回去，孩子最多看到闪一下。\n"
                + "覆盖安装后小米电视会解绑无障碍服务，这是最常见的失效原因。"));
        c.addView(actionRow("去修复主页键拦截", "打开系统无障碍设置", new View.OnClickListener() {
            @Override public void onClick(View v) {
                openAccessibilitySettings();
            }
        }));

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "怎么用遥控器操作"));
        c.addView(Ui.hint(this, "• 方向键移动焦点，选中的卡片会放大并出现蓝色边框；\n"
                + "• 按 OK（确认键）进入；\n"
                + "• 弹层里按返回键关闭；\n"
                + "• 锁屏页按【菜单键】可以输入家长密码直接解锁。"));

        c.addView(Ui.divider(this));
        c.addView(Ui.sectionLabel(this, "安装与升级"));
        c.addView(Ui.hint(this, "覆盖安装不会丢设置和题库。升级时如果主页键失效，"
                + "按上面的「去修复」重新打开一次无障碍即可。\n"
                + "当前版本：" + AppVer.VERSION));
        return c;
    }

    // ================= 通用控件 =================

    /**
     * 一行步进器：左边标题+说明，右边 [-] 数值 [+]。
     * 数值 TextView 存在 row 的 tag 里，方便点击后原地刷新。
     */
    private LinearLayout stepperRow(String title, String sub, String value,
                                    Runnable minus, Runnable plus) {
        LinearLayout box = Ui.row(this);
        box.setBackgroundResource(R.drawable.bg_ghost);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));

        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 17, Ui.color(this, R.color.text_main), true));
        if (sub != null) {
            TextView s = Ui.text(this, sub, 12, Ui.color(this, R.color.text_dim), false);
            s.setPadding(0, Ui.dp(this, 3), 0, 0);
            texts.addView(s);
        }
        box.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView m = Ui.smallButton(this, "−");
        m.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                minus.run();
            }
        });
        box.addView(m);

        TextView val = Ui.text(this, value, 20, Ui.color(this, R.color.text_main), true);
        val.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(Ui.dp(this, 132),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        box.addView(val, vlp);

        TextView p = Ui.smallButton(this, "+");
        p.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                plus.run();
            }
        });
        box.addView(p);

        box.setTag(val);
        return box;
    }

    private void updateStepper(LinearLayout row, String text) {
        if (row == null) return;
        Object t = row.getTag();
        if (t instanceof TextView) ((TextView) t).setText(text);
    }

    /** 一行可点的设置项。 */
    private LinearLayout actionRow(String title, String sub, View.OnClickListener l) {
        LinearLayout box = Ui.row(this);
        box.setBackgroundResource(R.drawable.bg_ghost);
        box.setFocusable(true);
        box.setClickable(true);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
        box.setOnClickListener(l);
        Ui.focusScale(box);

        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.text(this, title, 17, Ui.color(this, R.color.text_main), true));
        if (sub != null && sub.length() > 0) {
            TextView s = Ui.text(this, sub, 12, Ui.color(this, R.color.text_dim), false);
            s.setPadding(0, Ui.dp(this, 3), 0, 0);
            texts.addView(s);
        }
        box.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(Ui.text(this, "›", 26, Ui.color(this, R.color.text_faint), false));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        box.setLayoutParams(lp);
        return box;
    }

    private LinearLayout actionRowDanger(String title, String sub, View.OnClickListener l) {
        LinearLayout box = actionRow(title, sub, l);
        box.setBackgroundResource(R.drawable.bg_danger);
        return box;
    }

    /** 诊断项：左边名称，右边状态，状态用绿/橙区分。 */
    private LinearLayout diagLine(String label, String value, boolean ok) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 2), Ui.dp(this, 5), Ui.dp(this, 2), Ui.dp(this, 5));
        row.addView(Ui.text(this, label, 15, Ui.color(this, R.color.text_dim), false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, (ok ? "✓ " : "! ") + value, 15,
                Ui.color(this, ok ? R.color.ok : R.color.warn), true));
        return row;
    }

    // ================= 交互 =================

    private void toggleMonitor() {
        final boolean enabling = !prefs.isEnabled();
        confirm(enabling ? "开启守护？" : "停用守护？",
                enabling ? "开启后按设定时长计时，到点自动弹出答题锁屏。"
                        : "停用后不再计时、不再锁屏，孩子可以正常看电视。\n家长设置仍然需要密码。",
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

    private void lockNow() {
        confirm("立即锁定测试？", "马上弹出答题锁屏，答对 " + prefs.requiredCorrect()
                + " 题后才会解开。\n（这是测试功能，不会增加今日观看时长）", "锁定", new Runnable() {
            @Override public void run() {
                prefs.setLocked(true);
                startActivity(new Intent(MainActivity.this, LockScreenActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        });
    }

    /** 统一确认框。 */
    private void confirm(String title, String message, String okText, final Runnable onOk) {
        LinearLayout panel = Ui.dialogPanel(this);
        panel.addView(Ui.text(this, title, 22, Ui.color(this, R.color.text_main), true));
        TextView msg = Ui.text(this, message, 14, Ui.color(this, R.color.text_dim), false);
        msg.setLineSpacing(Ui.dp(this, 5), 1f);
        msg.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 18));
        panel.addView(msg);

        final Dialog dlg = Ui.dialog(this, panel, 560);
        LinearLayout buttons = Ui.row(this);
        TextView cancel = Ui.button(this, "取消", false, 52);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dlg.dismiss();
            }
        });
        TextView ok = Ui.button(this, okText, true, 52);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dlg.dismiss();
                onOk.run();
            }
        });
        buttons.addView(cancel);
        TextView gap = new TextView(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 14), 1));
        buttons.addView(ok);
        panel.addView(buttons);
        dlg.show();
        ok.requestFocus();
    }

    // ================= 原有业务逻辑 =================

    private void maybeAskNotification() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2001);
            } catch (Throwable ignored) {
            }
        }
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("找到 txhlock，把开关关掉再打开一次");
        } catch (Exception e) {
            toast("请到 设置 → 无障碍 里操作 txhlock");
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

    /** 修改家长密码：三步验证，应用内置数字键盘。 */
    private void showChangePin() {
        final String oldPin = prefs.pin();
        final int[] stage = {0};
        final String[] newPin = {""};

        LinearLayout panel = Ui.dialogPanel(this);
        TextView title = Ui.text(this, "修改家长密码", 22, Ui.color(this, R.color.text_main), true);
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

            @Override public void onCancelled() {
                dlg.dismiss();
            }
        });
        padRef[0] = pad;
        panel.addView(pad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dlg.show();
    }

    /** 局域网 SMB 题库同步设置。 */
    private void showSmbDialog() {
        LinearLayout panel = Ui.dialogPanel(this);
        panel.addView(Ui.text(this, "局域网 SMB 题库同步", 22, Ui.color(this, R.color.text_main), true));
        TextView tip = Ui.text(this, "填电脑的 IP 和共享文件夹名，保存后会立即同步一次。", 13,
                Ui.color(this, R.color.text_dim), false);
        tip.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        panel.addView(tip);

        EditText host = addField(panel, "服务器 IP（如 192.168.1.100）", prefs.smbHost(), true);
        EditText share = addField(panel, "共享名（如 share）", prefs.smbShare(), true);
        EditText user = addField(panel, "用户名（可留空）", prefs.smbUser(), true);
        EditText pass = addField(panel, "密码", prefs.smbPass(), true);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText path = addField(panel, "共享内子目录（可留空）", prefs.smbPath(), false);

        // 5 个输入框在小屏电视上会超过一屏，套一层 ScrollView，窗口高度限制在屏幕内
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final Dialog dlg = Ui.dialog(this, sv, 620, 460);
        LinearLayout buttons = Ui.row(this);
        TextView off = Ui.button(this, "停用", false, 52);
        TextView save = Ui.button(this, "保存并同步", true, 52);
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
        TextView tv = Ui.text(this, label, 13, Ui.color(this, R.color.text_dim), false);
        tv.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        root.addView(tv);
        EditText et = new EditText(this);
        et.setText(value);
        et.setTextColor(Ui.color(this, R.color.text_main));
        et.setHintTextColor(Ui.color(this, R.color.text_faint));
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        et.setSingleLine(true);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        et.setImeOptions(next ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        root.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    /** 执行一次 SMB 同步（后台线程）。 */
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

    /** Android 11+ 是否已拿到"所有文件访问"权限（读 /sdcard/Download 题库要用）。 */
    private boolean isAllFilesAccess() {
        if (Build.VERSION.SDK_INT < 30) return true;
        try {
            return android.os.Environment.isExternalStorageManager();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Android 6~10 读 Download 需要运行时存储权限；只在打开题库管理时问，不打扰启动。 */
    private void ensureStoragePermission() {
        if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 30) return;
        try {
            if (checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, 2002);
            }
        } catch (Throwable ignored) {
        }
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
        if (exist <= 0) {
            doExportBuiltin(dir);
            return;
        }
        confirm("覆盖导出目录里的文件？", "目录里已经有 " + exist + " 个 txt。\n"
                + "• 导出会用内置题库的原文覆盖同名文件；\n"
                + "• 你改过的内容会丢，找不回来。", "确定覆盖", new Runnable() {
            @Override public void run() {
                doExportBuiltin(dir);
            }
        });
    }

    private void doExportBuiltin(File dir) {
        int n = QuizLoader.exportBuiltin(this, dir);
        if (n > 0) {
            toast("已导出 " + n + " 个题库文件到：\n" + dir.getAbsolutePath());
        } else {
            toast("导出失败，请检查应用是否有存储写入权限");
        }
        refreshAll();
    }

    private void confirmClearLocal() {
        final int existing = countLocalTxt();
        if (existing <= 0) {
            toast("本地题库目录里没有 txt 文件");
            return;
        }
        confirm("确认删除本地题库？", "只删除 " + QuizLoader.DIR + " 里的 " + existing
                + " 个 txt 文件。\n• 内置题库是只读的，删不到也改不到；\n"
                + "• SMB 缓存目录同样不动；\n• 删掉的文件进不了回收站。", "确定删除", new Runnable() {
            @Override public void run() {
                clearLocalQuiz();
            }
        });
    }

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
        engine = new QuizEngine(this, prefs);
        refreshAll();
        if (existing == 0) {
            toast("本地题库目录已无 txt 文件");
        } else if (n == existing) {
            toast("已删除 " + n + " 个本地题库文件");
        } else {
            toast("删除失败：" + (existing - n) + " 个文件未删除，请检查存储权限");
        }
    }

    /** 还原并卸载：本应用从未改动系统桌面，卸载即完全恢复原样。 */
    private void showUninstallGuide() {
        confirm("还原并卸载", "本应用没有修改系统桌面，也没有禁用小米桌面的任何功能：\n\n"
                + "• 卸载后主页键立刻回到小米电视桌面；\n"
                + "• 答题记录、家长密码、题库设置随应用一起删除。\n\n"
                + "建议先到无障碍设置里关掉 txhlock，再卸载。", "去卸载", new Runnable() {
            @Override public void run() {
                try {
                    startActivity(new Intent(Intent.ACTION_DELETE,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    toast("请到 设置 → 应用 → txhlock 里卸载");
                }
            }
        });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 长关机阈值步进：0 = 关闭，往上第一档 15 分钟，最高 720 分钟。 */
    private int stepAway(int cur, int delta) {
        if (delta < 0) {
            return cur <= 0 ? 0 : Math.max(0, cur - 15);
        }
        return Math.min(720, (cur <= 0 ? 0 : cur) + 15);
    }

    // ================= 生命周期 =================

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null && !prefs.isLocked()) {
            refreshAll();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (authed && leftAtMs > 0
                && System.currentTimeMillis() - leftAtMs > 8L * 60L * 1000L) {
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
