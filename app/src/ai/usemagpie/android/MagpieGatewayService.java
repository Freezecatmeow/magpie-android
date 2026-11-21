package ai.usemagpie.android;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Foreground dataSync service owning the libmagpie.so process lifecycle. */
public class MagpieGatewayService extends Service {
    static final String TAG = "MagpieSvc";
    public static final String ACTION_START = "ai.usemagpie.action.START";
    public static final String ACTION_STOP = "ai.usemagpie.action.STOP";
    public static final String ACTION_RESTART = "ai.usemagpie.action.RESTART";
    static final int PORT = 3430;

    private Process proc;
    private Thread supervisor;
    private volatile boolean userStopped = false;
    private volatile boolean inited = false;
    private volatile int crashCount = 0;
    private PowerManager.WakeLock wakeLock;
    private android.net.wifi.WifiManager.WifiLock wifiLock;
    private AndroidHostBridge bridge;
    private String caPath = "";
    private NetworkMonitor networkMonitor;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            userStopped = true;
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        userStopped = false;
        foreground();
        if (ACTION_RESTART.equals(action)) {
            stopProcess();
        }
        ensureStarted();
        return START_STICKY;
    }

    private void foreground() {
        Notification n = NotificationHelper.buildServiceNotification(this, "网关 127.0.0.1:" + PORT + " · 正在启动…");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NotificationHelper.NOTIF_SERVICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NotificationHelper.NOTIF_SERVICE_ID, n);
        }
    }

    private void updateNotification(String text) {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NotificationHelper.NOTIF_SERVICE_ID, NotificationHelper.buildServiceNotification(this, text));
    }

    private synchronized void ensureStarted() {
        if (proc != null && proc.isAlive()) return;
        try {
            if (!inited) { initOnce(); inited = true; }
            startProcess();
            startSupervisor();
        } catch (Throwable t) {
            Log.e(TAG, "ensureStarted failed", t);
        }
    }

    /** Strict ordered init pipeline (T04): CA export -> sync seed -> first-time mirror -> random key -> bridge. */
    private void initOnce() {
        try { caPath = CaBundleExporter.export(this); } catch (Throwable t) { Log.w(TAG, "ca export: " + t); }
        try { seedSync(); } catch (Throwable t) { Log.e(TAG, "seedSync", t); }
        try { firstTimeMirror(); } catch (Throwable t) { Log.e(TAG, "firstTimeMirror", t); }
        String key = loadOrGenKey();
        MagpieAppState.webKey = key;
        MagpieAppState.port = PORT;
        MagpieAppState.caPath = caPath;
        startBridge(key);
        startNetworkMonitor();
        Log.i(TAG, "init done, key length=" + key.length());
    }

    private void startBridge(String key) {
        try {
            bridge = new AndroidHostBridge(hostActions, key);
            bridge.start();
            MagpieAppState.bridgePort = bridge.port();
            Log.i(TAG, "host bridge on 127.0.0.1:" + bridge.port());
        } catch (Throwable t) { Log.w(TAG, "bridge start: " + t); }
    }

    private void startNetworkMonitor() {
        try {
            networkMonitor = new NetworkMonitor(this, new NetworkMonitor.Callback() {
                public void onProxyChanged(String host, int port) { MagpieAppState.proxyHost = host == null ? "" : host; MagpieAppState.proxyPort = port; }
                public void onNetworkChanged() { try { caPath = CaBundleExporter.export(MagpieGatewayService.this); MagpieAppState.caPath = caPath; } catch (Throwable ignored) {} }
            });
            networkMonitor.start();
            MagpieAppState.proxyHost = "";
            MagpieAppState.proxyPort = 0;
        } catch (Throwable t) { Log.w(TAG, "network monitor: " + t); }
    }

    private final AndroidHostBridge.Actions hostActions = new AndroidHostBridge.Actions() {
        public void openUrl(String url) { MainActivity ui = MainActivity.INSTANCE; if (ui != null) ui.openExternal(url); }
        public void copy(String text) { MainActivity ui = MainActivity.INSTANCE; if (ui != null) ui.doCopy(text); }
        public void notify(String channel, String title, String body, String view) { NotificationHelper.postView(MagpieGatewayService.this, channel, title, body, view); }
        public void wakeLock(boolean on) { setWakeLock(on); }
        public void theme(String color, boolean dark) { MainActivity ui = MainActivity.INSTANCE; if (ui != null) ui.applySystemBars(color, dark); }
        public void textSize(int pct) { MainActivity ui = MainActivity.INSTANCE; if (ui != null) ui.setTextZoom(pct); }
        public void quit() { userStopped = true; shutdown(); stopSelf(); }
    };

    private void startProcess() {
        try {
            String libDir = getApplicationInfo().nativeLibraryDir;
            File bin = new File(libDir, "libmagpie.so");
            File bunso = new File(libDir, "libbun.so");
            try { bunso.setExecutable(true, false); } catch (Throwable ignored) {}
            ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath(), "web", "--addr", "127.0.0.1:" + PORT, "--no-open");
            pb.redirectErrorStream(true);
            pb.directory(getFilesDir());
            Map<String, String> env = pb.environment();
            env.put("HOME", getFilesDir().getAbsolutePath());
            env.put("TMPDIR", getCacheDir().getAbsolutePath());
            env.put("MAGPIE_WEB_KEY", MagpieAppState.webKey);
            env.put("MAGPIE_BUN", bunso.getAbsolutePath());
            if (caPath != null && caPath.length() > 0) {
                env.put("SSL_CERT_FILE", caPath);
                env.put("NODE_EXTRA_CA_CERTS", caPath);
            }
            if (MagpieAppState.proxyHost != null && MagpieAppState.proxyHost.length() > 0 && MagpieAppState.proxyPort > 0) {
                String px = "http://" + MagpieAppState.proxyHost + ":" + MagpieAppState.proxyPort;
                env.put("HTTP_PROXY", px); env.put("HTTPS_PROXY", px);
                env.put("http_proxy", px); env.put("https_proxy", px);
                env.put("NO_PROXY", "127.0.0.1,localhost"); env.put("no_proxy", "127.0.0.1,localhost");
            }
            if (bridge != null) {
                env.put("MAGPIE_ANDROID_BRIDGE", "http://127.0.0.1:" + bridge.port());
                env.put("MAGPIE_ANDROID_BRIDGE_TOKEN", MagpieAppState.webKey);
            }
            String path = System.getenv("PATH");
            env.put("PATH", libDir + ":" + (path == null ? "/system/bin" : path));
            proc = pb.start();
            Log.i(TAG, "magpie process started pid=" + proc.hashCode());
            pumpLog(proc);
            watchPort(proc);
        } catch (Throwable t) {
            Log.e(TAG, "startProcess failed", t);
        }
    }

    private void pumpLog(final Process p) {
        new Thread(new Runnable() {
            public void run() {
                FileOutputStream fout = null;
                try {
                    File lf = new File(getFilesDir(), "app.log");
                    if (lf.exists() && lf.length() > 512 * 1024) { File old = new File(getFilesDir(), "app.log.1"); if (old.exists()) old.delete(); lf.renameTo(old); }
                    fout = new FileOutputStream(lf, true);
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = r.readLine()) != null) { Log.i(TAG, line); try { fout.write((line + "\n").getBytes("UTF-8")); } catch (Throwable ignored) {} }
                } catch (Throwable ignored) {
                } finally { try { if (fout != null) fout.close(); } catch (Throwable ignored) {} }
            }
        }).start();
    }

    private void watchPort(final Process p) {
        new Thread(new Runnable() {
            public void run() {
                boolean up = false;
                for (int i = 0; i < 240; i++) {
                    if (p != proc) return;
                    if (portUp()) { up = true; break; }
                    try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                }
                MagpieAppState.running = up;
                MagpieAppState.lanIp = detectLanIp();
                if (up) {
                    crashCount = 0;
                    String text = "网关 127.0.0.1:" + PORT + (MagpieAppState.lanIp.length() > 0 ? " · LAN " + MagpieAppState.lanIp : "");
                    updateNotification(text);
                    Log.i(TAG, "server up: " + text);
                } else {
                    Log.w(TAG, "server did not come up in time");
                }
            }
        }).start();
    }

    private void startSupervisor() {
        if (supervisor != null && supervisor.isAlive()) return;
        supervisor = new Thread(new Runnable() {
            public void run() {
                while (!userStopped) {
                    Process p = proc;
                    if (p == null) { try { Thread.sleep(500); } catch (InterruptedException ignored) {} continue; }
                    int code;
                    try { code = p.waitFor(); } catch (InterruptedException e) { return; }
                    if (userStopped || p != proc) return;
                    MagpieAppState.running = false;
                    crashCount++;
                    long delay = backoffMs(crashCount);
                    Log.w(TAG, "process exited code=" + code + " crashCount=" + crashCount + " restart in " + delay + "ms");
                    updateNotification("网关异常退出，正在重启…");
                    try { Thread.sleep(delay); } catch (InterruptedException e) { return; }
                    if (userStopped) return;
                    synchronized (MagpieGatewayService.this) {
                        try { startProcess(); } catch (Throwable t) { Log.e(TAG, "restart failed", t); }
                    }
                }
            }
        });
        supervisor.start();
    }

    private long backoffMs(int n) {
        if (n <= 1) return 1000L;
        if (n == 2) return 3000L;
        if (n == 3) return 10000L;
        return 30000L;
    }

    private boolean portUp() {
        Socket so = new Socket();
        try { so.connect(new InetSocketAddress("127.0.0.1", PORT), 300); return true; }
        catch (Exception e) { return false; }
        finally { try { so.close(); } catch (Exception ignored) {} }
    }

    private String loadOrGenKey() {
        try {
            File f = new File(getFilesDir(), ".magpie_web_key");
            if (f.exists()) {
                byte[] b = readAll(f);
                String s = new String(b, "UTF-8").trim();
                if (s.length() >= 16) return s;
            }
            byte[] rnd = new byte[16];
            new SecureRandom().nextBytes(rnd);
            StringBuilder sb = new StringBuilder();
            for (byte x : rnd) sb.append(String.format("%02x", x));
            String key = sb.toString();
            FileOutputStream o = new FileOutputStream(f);
            o.write(key.getBytes("UTF-8"));
            o.close();
            try { f.setReadable(false, false); f.setReadable(true, true); } catch (Throwable ignored) {}
            return key;
        } catch (Throwable t) {
            Log.e(TAG, "key gen failed", t);
            return "magpie-android-fallback-key";
        }
    }

    private void seedSync() throws Exception {
        File marker = new File(getFilesDir(), ".magpie_offline_seeded_v3");
        if (marker.exists()) return;
        ZipInputStream zin = new ZipInputStream(getAssets().open("seed.zip"));
        byte[] buf = new byte[8192];
        int count = 0;
        ZipEntry e;
        while ((e = zin.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            File out = new File(getFilesDir(), e.getName());
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream fos = new FileOutputStream(out);
            int len;
            while ((len = zin.read(buf)) > 0) fos.write(buf, 0, len);
            fos.close();
            zin.closeEntry();
            count++;
        }
        zin.close();
        marker.createNewFile();
        Log.i(TAG, "offline seed unpacked " + count + " entries (synchronous)");
    }

    private void firstTimeMirror() throws Exception {
        File cfgDir = new File(getFilesDir(), ".config/magpie");
        if (!cfgDir.exists()) cfgDir.mkdirs();
        File sf = new File(cfgDir, "settings.json");
        if (sf.exists()) {
            Log.i(TAG, "settings.json exists, leave user config untouched");
            return; // never overwrite user changes
        }
        boolean cn = "CN".equalsIgnoreCase(Locale.getDefault().getCountry());
        String json = "{" + (cn ? "\"chinaMirror\":true" : "\"chinaMirror\":false") + "}";
        FileOutputStream o = new FileOutputStream(sf);
        o.write(json.getBytes("UTF-8"));
        o.close();
        Log.i(TAG, "first-run settings.json created, chinaMirror=" + cn);
    }

    private static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) bos.write(b, 0, n);
        in.close();
        return bos.toByteArray();
    }

    private String detectLanIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(ifs)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!a.isLoopbackAddress() && a instanceof java.net.Inet4Address) return a.getHostAddress();
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public void setWakeLock(boolean on) {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (on) {
                if (wakeLock == null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "magpie:gateway");
                    wakeLock.setReferenceCounted(false);
                }
                if (!wakeLock.isHeld()) wakeLock.acquire();
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    if (wifiLock == null) wifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "magpie:wifi");
                    if (!wifiLock.isHeld()) wifiLock.acquire();
                }
            } else {
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
            }
        } catch (Throwable t) { Log.w(TAG, "setWakeLock: " + t); }
    }

    private void stopProcess() {
        try { if (proc != null) { proc.destroy(); proc = null; } } catch (Throwable ignored) {}
        MagpieAppState.running = false;
    }

    private void shutdown() {
        userStopped = true;
        stopProcess();
        setWakeLock(false);
        if (networkMonitor != null) { networkMonitor.stop(); networkMonitor = null; }
        if (bridge != null) { bridge.stop(); bridge = null; }
        stopForeground(true);
    }

    @Override public void onDestroy() {
        shutdown();
        super.onDestroy();
    }
}
