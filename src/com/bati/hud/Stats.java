package com.bati.hud;

import android.os.StatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** 从 /proc 与 /sys 读取系统指标，全部为普通应用可读的路径。 */
public class Stats {

    public float memPct;
    public float swapPct;
    public float cpuPct;
    public float cpuMaxCorePct;
    public float tempC = -1f;
    public float diskPct;
    public int activeApps = -1;
    public long swapFreeMB = 0;
    public float load1 = -1f;
    public long uptimeMs = 0;
    public long swapRatePages = 0;
    public long memAvailMB = 0;
    public long memTotalMB = 0;
    public long swapUsedMB = 0;
    public long diskFreeMB = 0;

    private long lastCpuTotal = -1, lastCpuIdle = -1;
    private long[] lastCoreTotal, lastCoreIdle;
    private long lastPswp = -1;
    private long lastSwapTs = 0;

    public void update() {
        readMem();
        readCpu();
        readSwapRate();
        readTemp();
        readDisk();
        readLoad();
        uptimeMs = android.os.SystemClock.elapsedRealtime();
    }

    private void readMem() {
        long total = 0, avail = 0, swTotal = 0, swFree = 0;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:")) total = kb(line);
                else if (line.startsWith("MemAvailable:")) avail = kb(line);
                else if (line.startsWith("SwapTotal:")) swTotal = kb(line);
                else if (line.startsWith("SwapFree:")) swFree = kb(line);
            }
        } catch (Exception ignored) {
        } finally {
            close(r);
        }
        memTotalMB = total / 1024;
        memAvailMB = avail / 1024;
        memPct = total > 0 ? (float) (total - avail) / total * 100f : 0f;
        swapPct = swTotal > 0 ? (float) (swTotal - swFree) / swTotal * 100f : 0f;
        swapUsedMB = (swTotal - swFree) / 1024;
        swapFreeMB = swFree / 1024;
    }

    private void readCpu() {
        BufferedReader r = null;
        long total = 0, idle = 0;
        long[] coreTotal = null, coreIdle = null;
        try {
            r = new BufferedReader(new FileReader("/proc/stat"));
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("cpu")) break;
                String[] p = line.trim().split("\\s+");
                long t = 0;
                for (int i = 1; i < p.length && i <= 8; i++) t += Long.parseLong(p[i]);
                long id = Long.parseLong(p[4]) + (p.length > 5 ? Long.parseLong(p[5]) : 0);
                if (p[0].equals("cpu")) {
                    total = t;
                    idle = id;
                } else {
                    if (coreTotal == null) {
                        int cores = Runtime.getRuntime().availableProcessors();
                        coreTotal = new long[cores];
                        coreIdle = new long[cores];
                    }
                    if (n < coreTotal.length) {
                        coreTotal[n] = t;
                        coreIdle[n] = id;
                        n++;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            close(r);
        }
        if (lastCpuTotal > 0 && total >= lastCpuTotal) {
            long dt = total - lastCpuTotal, di = idle - lastCpuIdle;
            cpuPct = dt > 0 ? (float) (dt - di) / dt * 100f : 0f;
        }
        lastCpuTotal = total;
        lastCpuIdle = idle;

        if (coreTotal != null && lastCoreTotal != null) {
            float mx = 0;
            for (int i = 0; i < coreTotal.length && i < lastCoreTotal.length; i++) {
                long dt = coreTotal[i] - lastCoreTotal[i];
                long di = coreIdle[i] - lastCoreIdle[i];
                if (dt > 0) {
                    float v = (float) (dt - di) / dt * 100f;
                    if (v > mx) mx = v;
                }
            }
            cpuMaxCorePct = mx;
        }
        if (coreTotal != null) {
            lastCoreTotal = coreTotal;
            lastCoreIdle = coreIdle;
        }
    }

    private void readSwapRate() {
        long in = 0, out = 0;
        boolean any = false;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/vmstat"));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("pswpin ")) {
                    in = num(line);
                    any = true;
                } else if (line.startsWith("pswpout ")) {
                    out = num(line);
                    any = true;
                }
            }
        } catch (Exception ignored) {
        } finally {
            close(r);
        }
        long pswp = any ? in + out : -1;
        long now = System.currentTimeMillis();
        if (pswp >= 0 && lastPswp >= 0 && lastSwapTs > 0 && now > lastSwapTs) {
            long d = pswp - lastPswp;
            if (d < 0) d = 0;
            swapRatePages = d * 1000L / (now - lastSwapTs);
        }
        if (pswp >= 0) {
            lastPswp = pswp;
            lastSwapTs = now;
        }
    }

    private void readTemp() {
        float best = -1f;
        File dir = new File("/sys/class/thermal");
        File[] zones = dir.listFiles();
        if (zones == null) {
            tempC = -1f;
            return;
        }
        for (File z : zones) {
            if (!z.getName().startsWith("thermal_zone")) continue;
            float v = readFloat(new File(z, "temp"));
            if (v <= 0) continue;
            if (v > 1000) v = v / 1000f;
            if (v < 5 || v > 130) continue;
            if (v > best) best = v;
        }
        tempC = best;
    }

    private void readDisk() {
        try {
            StatFs fs = new StatFs("/data");
            long total = fs.getTotalBytes();
            long free = fs.getAvailableBytes();
            diskPct = total > 0 ? (float) (total - free) / total * 100f : 0f;
            diskFreeMB = free / (1024 * 1024);
        } catch (Exception ignored) {
        }
    }

    private void readLoad() {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/loadavg"));
            String s = r.readLine();
            r.close();
            if (s != null) load1 = Float.parseFloat(s.trim().split("\\s+")[0]);
        } catch (Exception ignored) {
        }
    }

    private static long kb(String line) {
        String[] p = line.trim().split("\\s+");
        try {
            return Long.parseLong(p[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long num(String line) {
        String[] p = line.trim().split("\\s+");
        try {
            return Long.parseLong(p[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static float readFloat(File f) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            String s = r.readLine();
            return s == null ? -1f : Float.parseFloat(s.trim());
        } catch (Exception e) {
            return -1f;
        } finally {
            close(r);
        }
    }

    private static void close(BufferedReader r) {
        if (r != null) try {
            r.close();
        } catch (Exception ignored) {
        }
    }
}
