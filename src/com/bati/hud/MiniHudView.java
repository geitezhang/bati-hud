package com.bati.hud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** 极简版：一行三个数字，黑色半透明圆角底板，跟 Aerial 右上角图标同一行。 */
public class MiniHudView extends View {

    private static final int C_TEXT = 0xFFFFFFFF;

    private final float d;
    private final float PAD_X, PAD_Y, GAP;
    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pValue = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private String vMem = "--";
    private String vCpu = "--";
    private String vTmp = "--";

    private final String[] labels = {"内存", "CPU", "温度"};

    public MiniHudView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        PAD_X = dp(16);
        PAD_Y = dp(7);
        GAP = dp(26);

        pBg.setColor(0xB0000000);

        pLabel.setTextSize(dp(17));
        pLabel.setColor(0xE6FFFFFF);

        pValue.setTextSize(dp(17));
        pValue.setColor(C_TEXT);
        pValue.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    private float dp(float v) {
        return v * d;
    }

    public void setStats(Stats s) {
        if (s == null) return;
        vMem = ((int) s.memPct) + "%";
        vCpu = ((int) s.cpuPct) + "%";
        vTmp = s.tempC < 0 ? "--" : ((int) s.tempC) + "℃";
        invalidate();
    }

    private String valAt(int i) {
        return i == 0 ? vMem : i == 1 ? vCpu : vTmp;
    }

    /** 每段数值的固定槽宽，按最宽的情况（100% / 100℃）预留，位数变化不改变整体宽度。 */
    private float slotWidth(int i) {
        return pValue.measureText(i == 2 ? "100℃" : "100%");
    }

    private float contentWidth() {
        float w = 0;
        for (int i = 0; i < 3; i++) {
            w += pLabel.measureText(labels[i]) + dp(6) + slotWidth(i);
            if (i < 2) w += GAP;
        }
        return w;
    }

    @Override
    protected void onMeasure(int w, int h) {
        float textH = pValue.descent() - pValue.ascent();
        setMeasuredDimension((int) Math.ceil(contentWidth() + PAD_X * 2),
                (int) Math.ceil(textH + PAD_Y * 2));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        c.drawRoundRect(r, h / 2f, h / 2f, pBg);

        float baseline = h / 2f - (pValue.ascent() + pValue.descent()) / 2f;
        float x = PAD_X;
        for (int i = 0; i < 3; i++) {
            c.drawText(labels[i], x, baseline, pLabel);
            x += pLabel.measureText(labels[i]) + dp(6);
            c.drawText(valAt(i), x, baseline, pValue);
            x += slotWidth(i);
            if (i < 2) x += GAP;
        }
    }
}
