package com.bati.hud;

import android.content.Context;
import android.content.SharedPreferences;

/** 本应用设置。目前只有一项：桌面监控条是否开启。 */
public class Prefs {

    private static final String FILE = "bati";
    private static final String KEY_HUD = "hud_on_desktop";

    /** 默认关闭：开机不显示桌面监控条，需要时在面板里手动打开。 */
    public static boolean hudEnabled(Context c) {
        try {
            return c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                    .getBoolean(KEY_HUD, false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setHudEnabled(Context c, boolean on) {
        try {
            SharedPreferences sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
            sp.edit().putBoolean(KEY_HUD, on).apply();
        } catch (Exception ignored) {
        }
    }
}
