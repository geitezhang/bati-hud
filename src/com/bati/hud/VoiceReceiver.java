package com.bati.hud;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 兜底入口：有些固件在按语音键时发广播而不是发 KeyEvent。
 * 收到就打开面板；没收到也无害。
 */
public class VoiceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Log.i("BatiHud", "voice broadcast " + (i == null ? "null" : i.getAction()));
        HudPanelActivity.open(c);
    }
}
