package com.bati.hud;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 两个职责：
 * 1) 判断当前是不是 Aerial 桌面 —— 决定悬浮条显示还是隐藏。
 * 2) 把遥控器语音键当快捷键，唤出监控面板。
 *
 * 不读屏幕文字、不记录内容，只用包名判断前台是谁。
 */
public class HudAccessibilityService extends AccessibilityService {

    public static final String DESKTOP_PKG = "app.orchard.tv";
    private static final String TAG = "BatiHud";

    private static volatile boolean desktop = true;
    private static volatile String lastPkg = "";
    private static volatile HudAccessibilityService INSTANCE;

    /** 面板自己在前台时，悬浮条让位，别叠在一起。 */
    private static volatile boolean panelOpen = false;

    /** Aerial 控制中心展开时，悬浮条让位，避免压住它的标题和按钮。 */
    private volatile boolean controlCenterOpen = false;
    private volatile long lastKeyAt = 0;

    private final Handler h = new Handler(Looper.getMainLooper());

    public static boolean isDesktop() {
        return desktop;
    }

    public static String lastPkg() {
        return lastPkg;
    }

    public static void setPanelOpen(boolean v) {
        panelOpen = v;
        HudAccessibilityService s = INSTANCE;
        if (s != null) s.pushState();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
        lastKeyAt = android.os.SystemClock.uptimeMillis();
        Log.i(TAG, "a11y connected");
        // XML 里已经声明了，这里再硬设一次，防止个别固件不认 xml 的 flag
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo
                        .FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
                Log.i(TAG, "filter key events requested, flags=" + info.flags);
            }
        } catch (Exception ignored) {
        }
        // 用户关掉桌面监控时不再拉起服务
        if (Prefs.hudEnabled(this)) MonitorService.start(this);
        pushState();   // 服务重连后按当前状态恢复显示，否则会一直藏着
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                syncFront();
                pushState();
            }
        }, 500);
        // 每 5 秒校准一次，防止漏掉窗口事件
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                syncFront();
                h.postDelayed(this, 10000);
            }
        }, 8000);
        // 面板检测只在「最近按过键」或「面板正开着」时做——遍历节点树是跨进程调用，最贵
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                long idle = android.os.SystemClock.uptimeMillis() - lastKeyAt;
                if (desktop && (controlCenterOpen || idle < 15000)) {
                    refreshControlCenter();
                }
                h.postDelayed(this, 2000);
            }
        }, 2000);
    }

    private boolean shouldShow() {
        return desktop && !panelOpen && !controlCenterOpen;
    }

    private final Runnable ccCheck = new Runnable() {
        @Override
        public void run() {
            refreshControlCenter();
        }
    };

    /** Aerial 从右上角展开的浮层（控制中心、信号源菜单等）都带这几样文案。 */
    private static final String[] PANEL_MARKS = {"个性化", "信号源设置", "桌面守护"};

    private void refreshControlCenter() {
        boolean open = hasAnyMark();
        if (open != controlCenterOpen) {
            controlCenterOpen = open;
            Log.i(TAG, "aerial panel open=" + open);
            pushState();
        }
    }

    /** 递归收集节点文字。Compose 的树用 findAccessibilityNodeInfosByText 搜不到，只能自己走。 */
    private boolean hasAnyMark() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) return false;
            StringBuilder sb = new StringBuilder(512);
            collect(root, sb, 0);
            String t = sb.toString();
            if (t.length() > 0) Log.i(TAG, "a11y text[" + t.length() + "]="
                    + t.replace('\n', '|'));
            for (String m : PANEL_MARKS) {
                if (t.contains(m)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (root != null) try {
                root.recycle();
            } catch (Exception ignored) {
            }
        }
    }

    private void collect(AccessibilityNodeInfo n, StringBuilder sb, int depth) {
        if (n == null || depth > 30 || sb.length() > 4000) return;
        CharSequence tx = n.getText();
        if (tx != null && tx.length() > 0) sb.append(tx).append('\n');
        CharSequence cd = n.getContentDescription();
        if (cd != null && cd.length() > 0) sb.append(cd).append('\n');
        int c = n.getChildCount();
        for (int i = 0; i < c; i++) {
            AccessibilityNodeInfo ch = null;
            try {
                ch = n.getChild(i);
                if (ch != null) collect(ch, sb, depth + 1);
            } catch (Exception ignored) {
            } finally {
                if (ch != null) try {
                    ch.recycle();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void pushState() {
        MonitorService.setDesktopMode(shouldShow());
    }

    /** 主动查一次当前前台包名，用于服务刚连上、或长时间收不到事件时校准。 */
    private void syncFront() {
        String pkg = null;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence p = root.getPackageName();
                if (p != null) pkg = p.toString();
                root.recycle();
            }
        } catch (Exception ignored) {
        }
        if (pkg == null) {
            // 退一步：用窗口列表里 active 的那个
            try {
                for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                    if (w.isActive() && w.getType() == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                        AccessibilityNodeInfo r = w.getRoot();
                        if (r != null && r.getPackageName() != null) {
                            pkg = r.getPackageName().toString();
                            r.recycle();
                        }
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (pkg != null) applyFront(pkg, "");
    }

    private void applyFront(String pkg, String cls) {
        if (pkg.equals("com.bati.hud")) return;   // 自己的面板，不改状态
        lastPkg = pkg;
        boolean onDesktop = DESKTOP_PKG.equals(pkg) && !cls.contains("AerialDream");
        if (onDesktop != desktop) {
            Log.i(TAG, "front " + pkg + "/" + cls + " -> desktop=" + onDesktop);
            desktop = onDesktop;
        }
        pushState();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = e.getPackageName();
        if (p == null) return;
        CharSequence c = e.getClassName();
        applyFront(p.toString(), c == null ? "" : c.toString());
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            Log.i(TAG, "key " + code + " scan=" + event.getScanCode()
                    + " name=" + KeyEvent.keyCodeToString(code));
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            lastKeyAt = android.os.SystemClock.uptimeMillis();
        }
        if (event.getAction() == KeyEvent.ACTION_UP
                && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_BACK)) {
            h.removeCallbacks(ccCheck);
            h.postDelayed(ccCheck, 420);
        }
        if (event.getAction() == KeyEvent.ACTION_UP && isVoiceKey(code)) {
            Log.i(TAG, "voice key -> panel");
            HudPanelActivity.open(this);
            return true;
        }
        return false;
    }

    /** 语音键各种可能的 keycode：标准语音键、助手键、搜索键，外加厂商自定义 233。 */
    private static boolean isVoiceKey(int code) {
        return code == KeyEvent.KEYCODE_VOICE_ASSIST
                || code == KeyEvent.KEYCODE_ASSIST
                || code == KeyEvent.KEYCODE_SEARCH
                || code == 233;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        INSTANCE = null;
        return super.onUnbind(intent);
    }
}
