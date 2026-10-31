package com.bati.hud;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/** 监控面板：状态 / 配置 两个标签页，遥控器左右切标签、下移到按钮。 */
public class HudPanelActivity extends Activity {

    public static void open(Context c) {
        try {
            Intent i = new Intent(c, HudPanelActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            c.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private static final String[] STATE_LABELS = {
            "内存", "SWAP", "CPU", "温度", "存储", "系统", "活跃", "前台"
    };

    private static final String[] CFG_LABELS = {
            "设备", "系统", "版本", "芯片", "架构", "内存", "存储", "屏幕", "内核"
    };

    private final TextView[] stateVals = new TextView[STATE_LABELS.length];
    private final TextView[] cfgVals = new TextView[CFG_LABELS.length];

    private LinearLayout pageState;
    private LinearLayout pageConfig;
    private LinearLayout tabState;
    private LinearLayout tabConfig;

    private TextView btnClean;
    private TextView btnHud;
    private TextView btnClose;
    private final Handler h = new Handler(Looper.getMainLooper());
    private float d;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            Stats s = MonitorService.snapshot();
            if (stateVals[0] != null) {
                stateVals[0].setText("已用 " + (int) s.memPct + "%　·　可用 " + s.memAvailMB
                        + "M / 共 " + gb(s.memTotalMB));
                String swapTip = s.swapPct >= 90 && s.swapRatePages > 50 ? "　·　建议重启" : "";
                stateVals[1].setText("已用 " + s.swapUsedMB + "M（" + (int) s.swapPct
                        + "%）　·　换页 " + s.swapRatePages + " 页/秒" + swapTip);
                stateVals[2].setText("总 " + (int) s.cpuPct + "%　·　单核峰值 "
                        + (int) s.cpuMaxCorePct + "%");
                stateVals[3].setText(s.tempC < 0 ? "--" : ((int) s.tempC) + "℃");
                stateVals[4].setText("已用 " + (int) s.diskPct + "%　·　剩余 "
                        + (s.diskFreeMB / 1024) + "G");
                stateVals[5].setText("已运行 " + uptime(s.uptimeMs) + "　·　负载 "
                        + String.format(Locale.US, "%.2f", s.load1 < 0 ? 0f : s.load1));
                stateVals[6].setText(s.activeApps < 0
                        ? "需要授权后可见"
                        : "近 5 分钟用过 " + s.activeApps + " 个");
                String pkg = HudAccessibilityService.lastPkg();
                stateVals[7].setText(pkg == null || pkg.length() == 0 ? "--" : pkg);
            }
            h.postDelayed(this, 1000);
        }
    };

    private static String gb(long mb) {
        return String.format(Locale.US, "%.1fG", mb / 1024f);
    }

    private static String uptime(long ms) {
        long m = ms / 60000L;
        if (m < 60) return m + " 分钟";
        if (m < 1440) return (m / 60) + " 小时 " + (m % 60) + " 分";
        return (m / 1440) + " 天 " + ((m % 1440) / 60) + " 小时";
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;

        Window w = getWindow();
        w.setBackgroundDrawableResource(android.R.color.transparent);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams wlp = w.getAttributes();
        wlp.dimAmount = 0.62f;
        w.setAttributes(wlp);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x33000000);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(42), dp(30), dp(42), dp(28));
        card.setBackground(round(0xF2131921, 22, 0x2EFFFFFF));

        // 标题
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("巴蒂监控");
        title.setTextColor(0xF2FFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView hint = new TextView(this);
        hint.setText("左右切标签　·　返回键关闭");
        hint.setTextColor(0x66FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        head.addView(hint);
        card.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 标签
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabsLp.topMargin = dp(16);
        tabState = makeTab("状态");
        tabConfig = makeTab("配置");
        tabs.addView(tabState);
        tabs.addView(new View(this), new LinearLayout.LayoutParams(dp(24), 1));
        tabs.addView(tabConfig);
        card.addView(tabs, tabsLp);

        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lineLp.topMargin = dp(6);
        lineLp.bottomMargin = dp(10);
        card.addView(line, lineLp);

        // 内容区（两页等高，切换不跳）
        FrameLayout content = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38 * 9));
        pageState = buildPage(STATE_LABELS, stateVals);
        pageConfig = buildPage(CFG_LABELS, cfgVals);
        content.addView(pageState, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(pageConfig, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(content, contentLp);

        // 底部按钮
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footLp.topMargin = dp(16);

        btnHud = makeButton("", 150, false);
        paintHudButton();
        btnClean = makeButton("清理后台", 168, true);
        btnClose = makeButton("关闭", 118, false);
        foot.addView(btnHud);
        foot.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        foot.addView(btnClean);
        foot.addView(new View(this), new LinearLayout.LayoutParams(dp(16), 1));
        foot.addView(btnClose);
        card.addView(foot, footLp);

        btnHud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !Prefs.hudEnabled(HudPanelActivity.this);
                MonitorService.applyEnabled(HudPanelActivity.this, on);
                paintHudButton();
            }
        });

        btnClean.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnClean.setEnabled(false);
                btnClean.setText("清理中");
                Cleaner.clean(HudPanelActivity.this, new Cleaner.Cb() {
                    @Override
                    public void done(final int scanned, final long freed) {
                        btnClean.setEnabled(true);
                        // 只报真实可测的内存变化；"已杀 N 个"在 hidepid 下无法统计，已废弃
                        btnClean.setText(freed > 0 ? "释放 " + freed + " MB" : "已尽力");
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (btnClean != null) btnClean.setText("清理后台");
                            }
                        }, 2400);
                    }
                });
            }
        });
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(
                dp(700), ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.gravity = Gravity.CENTER;
        root.addView(card, cl);
        setContentView(root);

        fillConfig();
        showPage(0);
        tabState.setNextFocusDownId(btnClean.getId());
        tabConfig.setNextFocusDownId(btnClean.getId());
        tabState.requestFocus();
        h.post(refresh);
    }

    private LinearLayout buildPage(String[] labels, TextView[] store) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

            TextView l = new TextView(this);
            l.setText(labels[i]);
            l.setTextColor(0x80FFFFFF);
            l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            row.addView(l, new LinearLayout.LayoutParams(dp(76),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView v = new TextView(this);
            v.setText("--");
            v.setTextColor(0xEDFFFFFF);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            v.setSingleLine(true);
            row.addView(v, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            store[i] = v;
            page.addView(row);
        }
        return page;
    }

    private LinearLayout makeTab(final String text) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setFocusable(true);
        box.setClickable(true);
        box.setPadding(dp(2), dp(2), dp(2), 0);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(6));
        box.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final View bar = new View(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                dp(38), Math.max(2, dp(3)));
        barLp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(bar, barLp);

        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                if (has) showPage("状态".equals(text) ? 0 : 1);
            }
        });
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPage("状态".equals(text) ? 0 : 1);
            }
        });
        // 焦点 = 选中，视觉由 showPage 统一处理
        return box;
    }

    private void showPage(int index) {
        if (pageState == null) return;
        pageState.setVisibility(index == 0 ? View.VISIBLE : View.INVISIBLE);
        pageConfig.setVisibility(index == 0 ? View.INVISIBLE : View.VISIBLE);
        setTabOn(tabState, index == 0);
        setTabOn(tabConfig, index == 1);
    }

    private void setTabOn(LinearLayout box, boolean on) {
        ((TextView) box.getChildAt(0)).setTextColor(on ? 0xF2FFFFFF : 0x66FFFFFF);
        box.getChildAt(1).setBackgroundColor(on ? 0xF2FFFFFF : 0x00000000);
    }

    private void fillConfig() {
        Stats s = MonitorService.snapshot();
        DisplayMetrics dm = getResources().getDisplayMetrics();

        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : "--";
        int cores = Runtime.getRuntime().availableProcessors();
        String freq = maxFreq();

        String disp = Build.DISPLAY == null ? "--" : Build.DISPLAY;
        int sp = disp.lastIndexOf(' ');
        if (sp > 0) disp = disp.substring(sp + 1);

        long diskTotal = 0, diskFree = 0;
        try {
            android.os.StatFs fs = new android.os.StatFs("/data");
            diskTotal = fs.getTotalBytes() / (1024L * 1024L * 1024L);
            diskFree = fs.getAvailableBytes() / (1024L * 1024L * 1024L);
        } catch (Exception ignored) {
        }

        String model = Build.MODEL;
        if ("huanglong".equals(model)) model = "huanglong（希影 RS80 Ultra）";
        cfgVals[0].setText(model);
        cfgVals[1].setText("Android " + Build.VERSION.RELEASE
                + "（SDK " + Build.VERSION.SDK_INT + "）");
        cfgVals[2].setText(disp);
        cfgVals[3].setText(chipName() + "　·　" + cores + " 核 " + coreName());
        cfgVals[4].setText(abi + "（32 位）"
                + (freq == null ? "" : "　·　最高 " + freq));
        cfgVals[5].setText(gb(s.memTotalMB));
        cfgVals[6].setText(diskTotal + "G（整盘 " + emmcTotalGB() + "G）");
        cfgVals[7].setText(dm.widthPixels + "×" + dm.heightPixels
                + " @ " + dm.densityDpi + "dpi");
        cfgVals[8].setText("Linux " + System.getProperty("os.version"));
    }

    /** 从系统版本串里抠出芯片型号，比如 newlink_hi3751v660 → hi3751v660。 */
    private String chipName() {
        String src = Build.DISPLAY == null ? "" : Build.DISPLAY;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(hi\\d{4}[a-z0-9]*)").matcher(src);
        return m.find() ? m.group(1) : Build.BOARD;
    }

    /** CPU part 编码 → 核心代号，读不到就返回空。 */
    private String coreName() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("CPU part")) continue;
                String code = line.substring(line.indexOf(':') + 1).trim().toLowerCase(Locale.US);
                if ("0xd03".equals(code)) return "A53";
                if ("0xd05".equals(code)) return "A55";
                if ("0xd07".equals(code)) return "A57";
                if ("0xd08".equals(code)) return "A72";
                if ("0xd0a".equals(code)) return "A75";
                if ("0xd0b".equals(code)) return "A76";
                if ("0xd0d".equals(code)) return "A77";
                return code;
            }
        } catch (Exception ignored) {
        } finally {
            if (r != null) try {
                r.close();
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    /** 整块 eMMC 的物理容量（十进制 GB），用于说明标称容量与可用分区的差。 */
    private long emmcTotalGB() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/partitions"));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length == 4 && "mmcblk0".equals(p[3])) {
                    long kb = Long.parseLong(p[2]);
                    return Math.round(kb * 1024.0 / 1e9);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (r != null) try {
                r.close();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private String maxFreq() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(
                    "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"));
            String s = r.readLine();
            if (s == null) return null;
            long khz = Long.parseLong(s.trim());
            return String.format(Locale.US, "%.2fGHz", khz / 1000000f);
        } catch (Exception e) {
            return null;
        } finally {
            if (r != null) try {
                r.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 小号胶囊按钮：未选中半透明白描边，选中反白深色字。 */
    private TextView makeButton(String text, int widthDp, final boolean primary) {
        final TextView t = new TextView(this);
        t.setId(View.generateViewId());
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(50)));
        paintButton(t, false, primary);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                paintButton((TextView) v, has, primary);
                if (v == btnHud && !Prefs.hudEnabled(HudPanelActivity.this)) {
                    v.post(new Runnable() {
                        @Override
                        public void run() {
                            paintHudButton();
                        }
                    });
                }
                v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f)
                        .setDuration(100).start();
            }
        });
        return t;
    }

    /** 开关按钮：开=「桌面条 开」，关=「桌面条 关」。 */
    private void paintHudButton() {
        if (btnHud == null) return;
        boolean on = Prefs.hudEnabled(this);
        btnHud.setText(on ? "桌面条 开" : "桌面条 关");
        paintButton(btnHud, btnHud.hasFocus(), false);
        if (!on) btnHud.setTextColor(btnHud.hasFocus() ? 0xFF11161D : 0x80FFFFFF);
    }

    private void paintButton(TextView v, boolean focused, boolean primary) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(25));
        if (focused) {
            g.setColor(0xFFFFFFFF);
            g.setStroke(Math.max(2, dp(2)), 0xFFFFFFFF);
            v.setTextColor(0xFF11161D);
        } else {
            g.setColor(primary ? 0x26FFFFFF : 0x14FFFFFF);
            g.setStroke(Math.max(2, dp(1)), primary ? 0x59FFFFFF : 0x33FFFFFF);
            v.setTextColor(primary ? 0xF2FFFFFF : 0xB3FFFFFF);
        }
        v.setBackground(g);
    }

    private GradientDrawable round(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private int dp(float v) {
        return (int) (v * d);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    protected void onStart() {
        super.onStart();
        HudAccessibilityService.setPanelOpen(true);
        h.removeCallbacks(refresh);
        h.post(refresh);
    }

    @Override
    protected void onStop() {
        h.removeCallbacks(refresh);
        HudAccessibilityService.setPanelOpen(false);
        super.onStop();
    }
}
