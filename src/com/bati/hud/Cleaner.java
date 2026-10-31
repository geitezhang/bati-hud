package com.bati.hud;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

/**
 * 尝试清理第三方应用的后台进程（跳过 Aerial、代理与自身）。
 *
 * <p>能力边界（务必先读）：
 * <ul>
 *   <li>{@code killBackgroundProcesses} 只对"纯后台(cached)"进程有效。正在播放视频/音频、
 *       或持有前台服务的应用会被系统标记为 perceptible，该方法对它们<b>无效</b>。
 *       这是系统限制，不是 bug —— 想真正停掉只能走 adb/root 的 force-stop。</li>
 *   <li>本机 /proc 挂载为 {@code hidepid=invisible}，普通应用无法枚举其他进程，
 *       所以"杀掉几个"在应用层<b>无法统计</b>。旧版无条件 killed++ 并显示"已清 N 个"，
 *       那个数字是假的，2026-10-30 已移除。</li>
 *   <li>唯一真实可测的是清理前后的可用内存差，因此只回报这个。</li>
 * </ul>
 */
public class Cleaner {

    /** 白名单：桌面、代理，以及自己。杀掉会让电视直接断网或掉回原厂桌面。 */
    private static final java.util.Set<String> PROTECTED = new java.util.HashSet<>(java.util.Arrays.asList(
            "app.orchard.tv",
            "com.github.metacubex.clash.meta",
            "com.bati.hud"
    ));

    public interface Cb {
        /**
         * @param scanned 尝试过 killBackgroundProcesses 的应用数，<b>不是</b>实际杀掉的个数
         * @param freedMB 清理前后可用内存差，负数已归零
         */
        void done(int scanned, long freedMB);
    }

    public static void clean(final Context ctx, final Cb cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Stats s = new Stats();
                s.update();
                long before = s.memAvailMB;
                int scanned = 0;
                try {
                    ActivityManager am =
                            (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
                    PackageManager pm = ctx.getPackageManager();
                    List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                    String self = ctx.getPackageName();
                    for (ApplicationInfo ai : apps) {
                        if (ai.packageName.equals(self)) continue;
                        if (ai.uid < 10000) continue;   // 只动用户应用，系统的不碰
                        if (PROTECTED.contains(ai.packageName)) continue;
                        try {
                            am.killBackgroundProcesses(ai.packageName);
                        } catch (Exception ignored) {
                        }
                        scanned++;                       // 只记"尝试过"，不声称已杀
                    }
                } catch (Exception e) {
                    Log.w("BatiHud", "clean failed", e);
                }
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException ignored) {
                }
                Stats s2 = new Stats();
                s2.update();
                final long freed = s2.memAvailMB - before;
                final int sc = scanned;
                Log.i("BatiHud", "clean scanned=" + sc + " freedMB=" + freed);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (cb != null) cb.done(sc, Math.max(freed, 0));
                    }
                });
            }
        }).start();
    }
}
