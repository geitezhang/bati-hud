package com.bati.hud;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        // 按上次的选择恢复：关机前开着就显示，关着就不启动服务
        boolean on = Prefs.hudEnabled(c);
        android.util.Log.i("BatiHud", "boot restore hud=" + on);
        if (!on) return;
        if (!Settings.canDrawOverlays(c)) return;
        Intent s = new Intent(c, MonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Exception ignored) {
        }
    }
}
