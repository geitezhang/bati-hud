package com.bati.hud;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.TextView;

/** 从桌面图标打开：直接进监控面板（语音键失效时的兜底入口）。 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (!Settings.canDrawOverlays(this)) {
            TextView tv = new TextView(this);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(18);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundColor(0xFF0E1319);
            tv.setPadding(48, 48, 48, 48);
            tv.setText("缺少「显示在其他应用上层」权限。\n\n"
                    + "请用电脑执行：\n"
                    + "adb shell appops set com.bati.hud SYSTEM_ALERT_WINDOW allow\n\n"
                    + "然后重新打开本应用。");
            setContentView(tv);
            return;
        }

        MonitorService.start(this);
        startActivity(new android.content.Intent(this, HudPanelActivity.class));
        finish();
    }
}
