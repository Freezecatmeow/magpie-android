package ai.usemagpie.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** FR-22: host-side diagnostics center — aggregate status, view app.log, run loopback checks, export zip. */
public class DiagnosticsActivity extends Activity {
    private static final int REQ_SAVE = 9001;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView content;
    private File pendingZip;
    private String lastQuery = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        TextView title = new TextView(this);
        title.setText("Magpie 诊断中心");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(btn("刷新", new View.OnClickListener(){ public void onClick(View v){ refresh(); }}));
        row1.addView(btn("导出诊断包", new View.OnClickListener(){ public void onClick(View v){ exportZip(); }}));
        row1.addView(btn("打开更新页", new View.OnClickListener(){ public void onClick(View v){ startActivity(new Intent(DiagnosticsActivity.this, UpdateActivity.class)); }}));
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(btn("healthcheck", new View.OnClickListener(){ public void onClick(View v){ query("/api/healthcheck"); }}));
        row2.addView(btn("quota", new View.OnClickListener(){ public void onClick(View v){ query("/api/quota"); }}));
        row2.addView(btn("plugin list", new View.OnClickListener(){ public void onClick(View v){ query("/api/plugins"); }}));
        root.addView(row2);

        content = new TextView(this);
        content.setTextSize(12);
        content.setTextColor(Color.DKGRAY);
        content.setPadding(0, 16, 0, 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        refresh();
    }

    private Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(12);
        b.setOnClickListener(l);
        return b;
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        sb.append("网关状态: ").append(MagpieAppState.running ? "运行中" : "未运行").append("\n");
        sb.append("端口: 127.0.0.1:").append(MagpieAppState.port).append("  Bridge: 127.0.0.1:").append(MagpieAppState.bridgePort).append("\n");
        sb.append("LAN: ").append(MagpieAppState.lanIp.length() > 0 ? MagpieAppState.lanIp : "(无)").append("\n");
        sb.append("系统代理: ").append(MagpieAppState.proxyHost.length() > 0 ? (MagpieAppState.proxyHost + ":" + MagpieAppState.proxyPort) : "DIRECT").append("\n\n");
        String libDir = getApplicationInfo().nativeLibraryDir;
        sb.append(libEntry(libDir, "libmagpie.so"));
        sb.append(libEntry(libDir, "libbun.so"));
        File ca = new File(MagpieAppState.caPath.length() > 0 ? MagpieAppState.caPath : getCacheDir() + "/.cache/magpie/android-ca-bundle.pem");
        sb.append("CA PEM: ").append(ca.exists() ? ("存在 " + ca.length() + " B") : "缺失(启动后生成)").append("\n");
        File log = new File(getFilesDir(), "app.log");
        sb.append("app.log: ").append(log.exists() ? (log.length() + " B") : "无").append("\n\n");
        sb.append("最近 loopback 查询:\n").append(lastQuery.length() > 0 ? lastQuery : "(未查询)");
        content.setText(sb.toString());
    }

    private String libEntry(String dir, String name) {
        File f = new File(dir, name);
        return name + ": " + (f.exists() ? (f.length() + " B @ " + f.getAbsolutePath()) : "缺失") + "\n";
    }

    private void query(final String path) {
        lastQuery = path + " -> 查询中…";
        refresh();
        new Thread(new Runnable() { public void run() {
            final String result = httpGet(path);
            ui.post(new Runnable(){ public void run(){ lastQuery = path + " ->\n" + result; refresh(); }});
        } }).start();
    }

    /** Loopback GET against local gateway with Bearer key; degrades gracefully when gateway down.
     *  NOTE (information gap): exact CLI HTTP paths / JSON schema not supplied by the plan; attempts common /api paths. */
    private String httpGet(String path) {
        if (!MagpieAppState.running) return "网关未启动（降级：无法查询）";
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL("http://127.0.0.1:" + MagpieAppState.port + path).openConnection();
            h.setConnectTimeout(4000); h.setReadTimeout(5000);
            if (MagpieAppState.webKey.length() > 0) h.setRequestProperty("Authorization", "Bearer " + MagpieAppState.webKey);
            int sc = h.getResponseCode();
            InputStream in = sc >= 400 ? h.getErrorStream() : h.getInputStream();
            String body = in == null ? "" : readAll(in);
            if (body.length() > 2000) body = body.substring(0, 2000) + "…";
            return "HTTP " + sc + "\n" + body;
        } catch (Throwable t) {
            return "接口不可达（降级）: " + t.getMessage();
        } finally { if (h != null) h.disconnect(); }
    }

    private void exportZip() {
        try {
            File dir = new File(getCacheDir(), "diag");
            if (!dir.exists()) dir.mkdirs();
            File zip = new File(dir, "magpie-diag-" + System.currentTimeMillis() + ".zip");
            ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip));
            putEntry(z, "diagnostics.txt", collectDiagnostics().getBytes("UTF-8"));
            File log = new File(getFilesDir(), "app.log");
            if (log.exists()) putEntry(z, "app.log", readFileBytes(log));
            if (MagpieAppState.caPath.length() > 0 && new File(MagpieAppState.caPath).exists())
                putEntry(z, "android-ca-bundle.pem", readFileBytes(new File(MagpieAppState.caPath)));
            z.close();
            pendingZip = zip;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            i.putExtra(Intent.EXTRA_TITLE, zip.getName());
            startActivityForResult(i, REQ_SAVE);
        } catch (Throwable t) {
            content.setText("导出失败: " + t);
        }
    }

    private String collectDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("magpie-android diagnostics\n");
        sb.append("versionName=").append(verName()).append(" versionCode=").append(ApkUpdater.localVersionCode(this)).append("\n");
        sb.append("android=").append(android.os.Build.VERSION.SDK_INT).append(" abi=").append(android.os.Build.SUPPORTED_ABIS[0]).append("\n");
        sb.append("gateway_running=").append(MagpieAppState.running).append(" port=").append(MagpieAppState.port).append(" bridge=").append(MagpieAppState.bridgePort).append("\n");
        sb.append("lan=").append(MagpieAppState.lanIp).append(" proxy=").append(MagpieAppState.proxyHost).append(":").append(MagpieAppState.proxyPort).append("\n");
        sb.append("ca=").append(MagpieAppState.caPath).append("\n");
        return sb.toString();
    }

    private String verName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable t) { return "?"; }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SAVE && res == RESULT_OK && data != null && data.getData() != null && pendingZip != null) {
            try {
                InputStream in = new FileInputStream(pendingZip);
                java.io.OutputStream out = getContentResolver().openOutputStream(data.getData());
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                in.close(); out.close();
                content.setText("诊断包已导出: " + data.getData());
            } catch (Throwable t) { content.setText("写出失败: " + t); }
        }
    }

    private static void putEntry(ZipOutputStream z, String name, byte[] data) throws Exception {
        z.putNextEntry(new ZipEntry(name)); z.write(data); z.closeEntry();
    }
    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
        while ((n = in.read(b)) > 0) bos.write(b, 0, n); in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }
    private static byte[] readFileBytes(File f) throws Exception {
        FileInputStream in = new FileInputStream(f); ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) bos.write(b, 0, n); in.close();
        return bos.toByteArray();
    }
}
