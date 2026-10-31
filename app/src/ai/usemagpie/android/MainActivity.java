package ai.usemagpie.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * magpie Android shell.
 *
 * Ships the headless magpie Go binary as lib/arm64-v8a/libmagpie.so. On start
 * it launches "magpie web --addr 127.0.0.1:3430 --no-open" with HOME pointed
 * at the app private dir and a fixed MAGPIE_WEB_KEY, then loads the web UI in
 * a WebView. The upstream binary serves its UI over http://127.0.0.1:3430
 * with a ?k=<key> login token.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Magpie";
    private static final int PORT = 3430;
    private static final String WEB_KEY = "magpie-android-local-key";

    private Process proc;
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyEdgeToEdge();
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.setWebViewClient(new WebViewClient());
        setContentView(web);

        configureChinaMirror();
        startServer();
        seedOfflineOnce();
        waitThenLoad();
    }

    /**
     * Draw the system bars in the page's own white instead of the default
     * black, so the strip above the web UI is not a black band. The page is
     * light, so the status/navigation icons go dark (LIGHT_* flags).
     */
    private void applyEdgeToEdge() {
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                getWindow().setNavigationBarColor(Color.WHITE);
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable t) {
            Log.e(TAG, "applyEdgeToEdge failed", t);
        }
    }

    private void startServer() {
        try {
            String libDir = getApplicationInfo().nativeLibraryDir;
            File bin = new File(libDir, "libmagpie.so");
            ProcessBuilder pb = new ProcessBuilder(
                    bin.getAbsolutePath(),
                    "web",
                    "--addr", "127.0.0.1:" + PORT,
                    "--no-open");
            pb.redirectErrorStream(true);
            pb.directory(getFilesDir());
            pb.environment().put("HOME", getFilesDir().getAbsolutePath());
            pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
            pb.environment().put("MAGPIE_WEB_KEY", WEB_KEY);
            File bunso = new File(libDir, "libbun.so");
            bunso.setExecutable(true, false);
            pb.environment().put("MAGPIE_BUN", bunso.getAbsolutePath());
            String path = System.getenv("PATH");
            pb.environment().put("PATH", libDir + ":" + (path == null ? "/system/bin" : path));
            proc = pb.start();
            Log.i(TAG, "magpie started from " + bin.getAbsolutePath());
            final Process p = proc;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        InputStream in = p.getInputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            Log.i(TAG, new String(buf, 0, n).trim());
                        }
                    } catch (Exception ignored) {
                    }
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "failed to start magpie binary", e);
        }
    }

    private boolean portUp() {
        Socket so = new Socket();
        try {
            so.connect(new InetSocketAddress("127.0.0.1", PORT), 300);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { so.close(); } catch (Exception ignored) {}
        }
    }

    private void waitThenLoad() {
        new Thread(new Runnable() {
            public void run() {
                boolean up = false;
                for (int i = 0; i < 160; i++) {
                    if (portUp()) { up = true; break; }
                    try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                }
                final boolean ok = up;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (ok) {
                            web.loadUrl("http://127.0.0.1:" + PORT + "/?k=" + WEB_KEY);
                        } else {
                            web.loadData(
                                "<html><body style='font:16px sans-serif;padding:24px'>"
                              + "<h3>magpie 本地服务未就绪</h3>"
                              + "<p>Go 服务未能在超时内监听 127.0.0.1:" + PORT + "。</p>"
                              + "</body></html>", "text/html", "utf-8");
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * Point magpie's plugin marketplace at the China mirrors before the
     * server starts.
     *
     * The in-app market resolves a plugin's latest version from npm at
     * https://registry.npmjs.org. On a phone in China that lookup times out,
     * so every community plugin (WorkBuddy, Qoder, ...) renders as
     * "即将上线 / Coming soon" and cannot be installed. magpie already has a
     * 「国内镜像」 switch (settings.chinaMirror) that asks npmmirror / jsDelivr
     * first for the plugin list, the plugins and the Bun runtime; and a
     * settings.npmRegistry that is asked for npm versions before the switch.
     * This pre-seeds both so the mobile app works out of the box on a
     * mainland network without the user flipping the switch by hand.
     */
    private void configureChinaMirror() {
        try {
            File cfgDir = new File(getFilesDir(), ".config/magpie");
            if (!cfgDir.exists() && !cfgDir.mkdirs()) {
                Log.w(TAG, "cannot create config dir " + cfgDir);
            }
            File sf = new File(cfgDir, "settings.json");
            JSONObject o = new JSONObject();
            if (sf.exists()) {
                String txt = readFile(sf);
                if (txt != null && txt.trim().length() > 0) {
                    try { o = new JSONObject(txt); } catch (Exception ignored) { o = new JSONObject(); }
                }
            }
            o.put("chinaMirror", true);
            o.put("npmRegistry", "https://registry.npmmirror.com");
            writeFile(sf, o.toString());
            Log.i(TAG, "chinaMirror=true, npmRegistry=npmmirror -> " + sf.getAbsolutePath());
        } catch (Throwable t) {
            Log.e(TAG, "configureChinaMirror failed", t);
        }
    }

    private static String readFile(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static void writeFile(File f, String s) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(s.getBytes("UTF-8"));
        out.close();
    }

    /**
     * First-run OFFLINE seeding of the WorkBuddy and Qoder community plugins.
     *
     * The plugin project (plugins/ + plugins.json) was built on the build
     * machine by the real CLI ("plugin add <pkg>") and shipped in
     * assets/seed.zip; the Bun runtime ships as native library
     * lib/arm64-v8a/libbun.so (Android 10+ refuses to exec a file written
     * into app data, but nativeLibraryDir is executable, and magpie reads
     * $MAGPIE_BUN). This unpacks seed.zip into the app home so magpie sees
     * the plugins installed and needs no network to add them.
     */
    private void seedOfflineOnce() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    File marker = new File(getFilesDir(), ".magpie_offline_seeded_v2");
                    if (marker.exists()) {
                        Log.i(TAG, "offline seed: already done");
                        return;
                    }
                    int n = unzipAsset("seed.zip", getFilesDir());
                    marker.createNewFile();
                    Log.i(TAG, "offline seed: unpacked " + n + " entries");
                } catch (Throwable t) {
                    Log.e(TAG, "offline seed error", t);
                }
            }
        }).start();
    }

    /** Unzip an asset into destDir (creating parents); returns entry count. */
    private int unzipAsset(String asset, File destDir) throws Exception {
        java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new java.io.BufferedInputStream(getAssets().open(asset)));
        byte[] buf = new byte[8192];
        int count = 0;
        java.util.zip.ZipEntry e;
        while ((e = zin.getNextEntry()) != null) {
            if (e.isDirectory()) {
                continue;
            }
            File out = new File(destDir, e.getName());
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileOutputStream fos = new FileOutputStream(out);
            int len;
            while ((len = zin.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            fos.close();
            zin.closeEntry();
            count++;
        }
        zin.close();
        return count;
    }

    /**
     * Back goes to the previous page in the web UI, not out of the app.
     * magpie's SPA keeps its own history; only when the WebView has no page
     * to return to does Back leave the app, as Android expects.
     */
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (proc != null) {
            proc.destroy();
            proc = null;
        }
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
