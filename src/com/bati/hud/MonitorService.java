package com.bati.hud;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

/** 常驻服务：采数据，按「当前是否在桌面」决定悬浮条显隐。 */
public class MonitorService extends Service {

    public static final String ACTION_TOGGLE = "com.bati.hud.TOGGLE";
    public static final String ACTION_CLEAN = "com.bati.hud.CLEAN";
    public static boolean RUNNING = false;

    private static volatile MonitorService INSTANCE;

    private WindowManager wm;
    private MiniHudView view;
    private WindowManager.LayoutParams lp;
    private final Stats stats = new Stats();
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean desktopMode = true;
    private int statTick = 0;
    private int highTicks = 0;
    private long lastAutoExpand = 0;

    /** 由无障碍服务调用；没有无障碍权限时一直保持 true，退回原来的全屏显示行为。 */
    public static void setDesktopMode(boolean d) {
        MonitorService s = INSTANCE;
        if (s == null) return;
        s.desktopMode = d;
        s.h.post(new Runnable() {
            @Override
            public void run() {
                if (s.desktopMode) s.showOverlay();
                else s.hideOverlay();
            }
        });
    }

    /** 面板里关掉开关时调用：先收起悬浮条，再停服务（不再占通知栏和内存）。 */
    public static void stopNow(Context c) {
        final MonitorService s = INSTANCE;
        if (s != null) {
            s.h.post(new Runnable() {
                @Override
                public void run() {
                    s.hideOverlay();
                }
            });
        }
        try {
            c.stopService(new Intent(c, MonitorService.class));
        } catch (Exception ignored) {
        }
    }

    /** 开关状态变化后立即生效。 */
    public static void applyEnabled(Context c, boolean on) {
        Prefs.setHudEnabled(c, on);
        if (on) start(c);
        else stopNow(c);
    }

    public static void start(Context c) {
        Intent i = new Intent(c, MonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
        }
    }

    /** 近 5 分钟内用过的应用个数。真实进程数在 Android 10+ 普通应用读不到。 */
    private int countActiveApps() {
        try {
            android.app.usage.UsageStatsManager u = (android.app.usage.UsageStatsManager)
                    getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            java.util.List<android.app.usage.UsageStats> list = u.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 300000L, now);
            if (list == null) return -1;
            int n = 0;
            for (android.app.usage.UsageStats st : list) {
                if (st.getLastTimeUsed() >= now - 300000L) n++;
            }
            return n;
        } catch (Exception e) {
            return -1;
        }
    }

    public static Stats snapshot() {
        MonitorService s = INSTANCE;
        if (s != null) {
            s.stats.update();
            return s.stats;
        }
        Stats st = new Stats();
        st.update();
        return st;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                // 悬浮条没显示时不用采数据，面板自己会调 snapshot()
                if (view != null) {
                    stats.update();
                    if (++statTick % 3 == 1) stats.activeApps = countActiveApps();
                    view.setStats(stats);
                }
            } catch (Exception ignored) {
            }
            h.postDelayed(this, 2000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        RUNNING = true;
        INSTANCE = this;
        startForegroundSafe();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        stats.update();
        h.post(tick);
        if (desktopMode) h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (desktopMode && Prefs.hudEnabled(MonitorService.this)) showOverlay();
            }
        }, 300);
    }

    private void startForegroundSafe() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                NotificationChannel ch = new NotificationChannel("hud", "监控条",
                        NotificationManager.IMPORTANCE_MIN);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
                Notification n = new Notification.Builder(this, "hud")
                        .setContentTitle("巴蒂监控运行中")
                        .setContentText("桌面显示，语音键唤出面板")
                        .setSmallIcon(android.R.drawable.ic_menu_info_details)
                        .build();
                startForeground(1001, n);
            } else {
                Notification n = new Notification.Builder(this)
                        .setContentTitle("巴蒂监控运行中")
                        .setSmallIcon(android.R.drawable.ic_menu_info_details)
                        .build();
                startForeground(1001, n);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String a = intent.getAction();
            if (ACTION_TOGGLE.equals(a)) {
                HudPanelActivity.open(this);
            } else if (ACTION_CLEAN.equals(a)) {
                Cleaner.clean(this, null);
            }
        }
        if (desktopMode && view == null && Prefs.hudEnabled(this)) showOverlay();
        return START_STICKY;
    }

    private void showOverlay() {
        if (view != null || wm == null) return;
        if (!Prefs.hudEnabled(this)) return;
        if (!Settings.canDrawOverlays(this)) return;
        MiniHudView v = new MiniHudView(this);
        v.setStats(stats);

        // 用容器包一层做边距：END/BOTTOM 下自己算 x/y 容易被屏幕边缘裁掉
        float d = getResources().getDisplayMetrics().density;
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        // 右侧中部：上方躲开 Aerial 的控制中心、下方躲开 dock
        // 跟 Aerial 右上角的 WiFi / 电池图标同一行，落在它们左侧
        wrap.setPadding(0, (int) (46 * d) - 2, (int) (220 * d) - 4, 0);
        android.widget.FrameLayout.LayoutParams fp = new android.widget.FrameLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        fp.gravity = Gravity.END | Gravity.TOP;
        wrap.addView(v, fp);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        // 右下角：避开 Aerial 右上的控制中心、左上的时钟、底部居中的 dock
        p.gravity = Gravity.TOP | Gravity.END;
        p.x = 0;
        p.y = 0;
        try {
            wm.addView(wrap, p);
            view = v;
            lp = p;
            Log.i("BatiHud", "overlay shown");
        } catch (Exception e) {
            Log.w("BatiHud", "addView failed", e);
        }
    }

    private void hideOverlay() {
        if (view == null || wm == null) return;
        try {
            wm.removeView(view.getParent() instanceof android.view.View
                    ? (android.view.View) view.getParent() : view);
            Log.i("BatiHud", "overlay hidden");
        } catch (Exception ignored) {
        }
        view = null;
        lp = null;
    }

    @Override
    public void onDestroy() {
        RUNNING = false;
        h.removeCallbacksAndMessages(null);
        hideOverlay();
        if (INSTANCE == this) INSTANCE = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
