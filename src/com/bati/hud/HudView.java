package com.bati.hud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** 自绘的悬浮面板：两排 × 三列，六个指标，可展开/收起。 */
public class HudView extends View {

    public interface OnAction {
        void onClean();
    }

    private static final int C_MEM = 0xFF4A9EFF;
    private static final int C_CPU = 0xFFFFA94D;
    private static final int C_SWAP = 0xFFB197FC;
    private static final int C_TEMP = 0xFFFF6B6B;
    private static final int C_DISK = 0xFF51CF66;
    private static final int C_PROC = 0xFF4DD4C4;

    private final float d;
    private final float PAD, TILE_W, TILE_H, TILE_HC, GAP, BTN_H;

    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pValue = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPct = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBarBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBtnText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private boolean expanded = true;
    private String flash = null;
    private long flashUntil = 0;

    private final String[] labels = {"内存", "CPU", "SWAP", "温度", "存储", "进程"};
    private final float[] pcts = new float[6];
    private final String[] vals = new String[6];
    private final int[] colors = {C_MEM, C_CPU, C_SWAP, C_TEMP, C_DISK, C_PROC};
    private boolean procUsable = false;

    private OnAction action;
    private float downX, downY;
    private boolean interactive = true;

    /** 面板里只做展示，不响应触摸。 */
    public void setInteractive(boolean v) {
        interactive = v;
    }

    public HudView(Context c) {
        this(c, 1f);
    }

    /** scale 用来整体放大：投影仪观看距离远，面板里要放大才看得清。 */
    public HudView(Context c, float scale) {
        super(c);
        d = c.getResources().getDisplayMetrics().density * scale;
        PAD = dp(14);
        TILE_W = dp(96);
        TILE_H = dp(64);
        TILE_HC = dp(24);
        GAP = dp(8);
        BTN_H = dp(34);

        pBg.setColor(0xC8141A21);
        pBorder.setStyle(Paint.Style.STROKE);
        pBorder.setStrokeWidth(dp(1));
        pBorder.setColor(0x33FFFFFF);

        pLabel.setTextSize(dp(11));
        pLabel.setColor(0xB3FFFFFF);

        pValue.setTextSize(dp(16));
        pValue.setColor(Color.WHITE);
        pValue.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        pPct.setTextSize(dp(12));
        pPct.setColor(Color.WHITE);
        pPct.setTextAlign(Paint.Align.RIGHT);

        pBarBg.setColor(0x33FFFFFF);
        pBar.setColor(Color.WHITE);

        pBtnBg.setColor(0x1FFFFFFF);
        pBtnText.setTextSize(dp(13));
        pBtnText.setColor(Color.WHITE);
        pBtnText.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < 6; i++) vals[i] = "--";
    }

    private float dp(float v) {
        return v * d;
    }

    public void setAction(OnAction a) {
        action = a;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void toggle() {
        expanded = !expanded;
        requestLayout();
        invalidate();
    }

    public void setStats(Stats s) {
        if (s == null) return;
        pcts[0] = clamp(s.memPct);
        vals[0] = s.memAvailMB + "M 可用";
        pcts[1] = clamp(s.cpuPct);
        vals[1] = (int) s.cpuMaxCorePct + "% 峰核";
        pcts[2] = clamp(s.swapPct);
        vals[2] = s.swapUsedMB + "M " + s.swapRatePages + "/s";
        pcts[3] = s.tempC < 0 ? 0 : clamp(s.tempC * 100f / 90f);
        vals[3] = s.tempC < 0 ? "--" : ((int) s.tempC) + "°C";
        pcts[4] = clamp(s.diskPct);
        vals[4] = s.diskFreeMB / 1024 + "G 可用";

        long upH = s.uptimeMs / 3600000L;
        labels[5] = "运行";
        pcts[5] = clamp(upH * 100f / (24f * 7f));
        if (upH < 1) vals[5] = (s.uptimeMs / 60000L) + "分";
        else if (upH < 24) vals[5] = upH + "h";
        else vals[5] = (upH / 24) + "天" + (upH % 24) + "h";
        invalidate();
    }

    private static float clamp(float v) {
        if (v < 0) return 0;
        return v > 100 ? 100 : v;
    }

    public void flash(String msg) {
        flash = msg;
        flashUntil = System.currentTimeMillis() + 3500;
        invalidate();
    }

    private int contentRows() {
        return expanded ? 2 : 2;
    }

    @Override
    protected void onMeasure(int w, int h) {
        float th = expanded ? TILE_H : TILE_HC;
        float width = PAD * 2 + TILE_W * 3 + GAP * 2;
        float height = PAD * 2 + th * contentRows() + GAP * (contentRows() - 1);
        setMeasuredDimension((int) width, (int) height);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        c.drawRoundRect(r, dp(14), dp(14), pBg);
        c.drawRoundRect(r, dp(14), dp(14), pBorder);

        float th = expanded ? TILE_H : TILE_HC;
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            float x = PAD + col * (TILE_W + GAP);
            float y = PAD + row * (th + GAP);
            if (expanded) drawTileExpanded(c, i, x, y);
            else drawTileCollapsed(c, i, x, y);
        }

        if (expanded) {
            float by = PAD + th * 2 + GAP + dp(10);
            if (flash != null && System.currentTimeMillis() < flashUntil) {
                float ty = by + BTN_H / 2 - (pBtnText.descent() + pBtnText.ascent()) / 2;
                c.drawText(flash, w / 2f, ty, pBtnText);
            }
        }
    }

    private void drawTileExpanded(Canvas c, int i, float x, float y) {
        c.drawText(labels[i], x, y + dp(14), pLabel);
        float vy = y + TILE_H - dp(18);
        c.drawText(vals[i], x, vy, pValue);

        float by = y + TILE_H - dp(5);
        r.set(x, by, x + TILE_W, by + dp(4));
        c.drawRoundRect(r, dp(1.5f), dp(1.5f), pBarBg);
        if (pcts[i] > 0) {
            r.set(x, by, x + TILE_W * pcts[i] / 100f, by + dp(4));
            pBar.setColor(colors[i]);
            c.drawRoundRect(r, dp(1.5f), dp(1.5f), pBar);
        }
    }

    private void drawTileCollapsed(Canvas c, int i, float x, float y) {
        float ty = y + TILE_HC / 2 - (pLabel.descent() + pLabel.ascent()) / 2;
        c.drawText(labels[i], x, ty, pLabel);
        pPct.setColor(colors[i]);
        String s = i == 3 ? (vals[3].equals("--") ? "--" : vals[3])
                : i == 5 ? (vals[5].equals("--") ? "--" : vals[5].replace(" 个", ""))
                : ((int) pcts[i]) + "%";
        c.drawText(s, x + TILE_W, ty, pPct);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!interactive) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (Math.abs(e.getX() - downX) > dp(20) || Math.abs(e.getY() - downY) > dp(20))
                    return true;
                if (!expanded) {
                    expanded = true;
                    requestLayout();
                    invalidate();
                } else {
                    float by = PAD + TILE_H * 2 + GAP + dp(10);
                    if (e.getY() >= by && e.getY() <= by + BTN_H
                            && e.getX() >= PAD && e.getX() <= getWidth() - PAD) {
                        if (action != null) action.onClean();
                    } else if (e.getY() < PAD + dp(4)) {
                        expanded = false;
                        requestLayout();
                        invalidate();
                    }
                }
                return true;
        }
        return super.onTouchEvent(e);
    }
}
