package ai.usemagpie.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Enhanced WebView host: Custom Tabs, SAF file chooser, dark bars, deep link, service binding. */
public class MainActivity extends Activity {
    private static final String TAG = "Magpie";
    private static final int REQ_FILE = 1001;
    private static final int REQ_CREATE_DOC = 1002;
    public static volatile MainActivity INSTANCE;

    private WebView web;
    private ValueCallback<Uri[]> mFilePathCallback;
    private String lastUrl;
    private String pendingView;
    private int insTop, insBottom, insLeft, insRight;
    private boolean cssInjected = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        INSTANCE = this;
        NotificationHelper.ensureChannels(this);
        requestNotificationPermission();
        buildWebView();
        startGatewayService();
        handleIntent(getIntent(), false);
        pollAndLoad();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7001); } catch (Throwable ignored) {}
        }
    }

    private void buildWebView() {
        web = new WebView(this);
        web.setBackgroundColor(isNight() ? Color.parseColor("#101114") : Color.WHITE);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        web.setWebViewClient(new MagpieWebViewClient(this));
        web.setWebChromeClient(new MagpieWebChromeClient(this));
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        setContentView(web);
        applySystemBars(isNight() ? "#101114" : "#FFFFFF", !isNight());
        cssInjected = false;
        setupInsets();
    }

    /** FR-17: capture status/nav/IME safe insets and expose them to the web layer as CSS variables. */
    private void setupInsets() {
        if (Build.VERSION.SDK_INT < 20 || web == null) return;
        web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                insTop = insets.getSystemWindowInsetTop();
                insBottom = insets.getSystemWindowInsetBottom();
                insLeft = insets.getSystemWindowInsetLeft();
                insRight = insets.getSystemWindowInsetRight();
                applyInsetsCss();
                return insets;
            }
        });
    }

    private void applyInsetsCss() {
        if (web == null) return;
        String js = "try{var r=document.documentElement.style;"
                + "r.setProperty('--magpie-safe-top','" + insTop + "px');"
                + "r.setProperty('--magpie-safe-bottom','" + insBottom + "px');"
                + "r.setProperty('--magpie-safe-left','" + insLeft + "px');"
                + "r.setProperty('--magpie-safe-right','" + insRight + "px');}catch(e){}";
        web.evaluateJavascript(js, null);
    }

    /** Called by MagpieWebViewClient.onPageFinished: inject android.css once + reapply insets. */
    public void onPageFinished(String url) {
        injectAndroidCss();
        applyInsetsCss();
    }

    private void injectAndroidCss() {
        if (web == null) return;
        if (cssInjected) { applyInsetsCss(); return; }
        try {
            InputStream in = getAssets().open("android.css");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) bos.write(b, 0, n);
            in.close();
            String css = new String(bos.toByteArray(), "UTF-8");
            String js = "(function(){if(document.getElementById('magpie-android-css'))return;"
                    + "var s=document.createElement('style');s.id='magpie-android-css';"
                    + "s.textContent=" + org.json.JSONObject.quote(css) + ";"
                    + "(document.head||document.documentElement).appendChild(s);})();";
            web.evaluateJavascript(js, null);
            cssInjected = true;
        } catch (Throwable t) { Log.w(TAG, "inject css: " + t); }
    }

    private boolean isNight() {
        int mode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void startGatewayService() {
        Intent svc = new Intent(this, MagpieGatewayService.class);
        svc.setAction(MagpieGatewayService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        } catch (Throwable t) { Log.e(TAG, "start service failed", t); }
    }

    private void pollAndLoad() {
        final Handler h = new Handler(Looper.getMainLooper());
        final int[] tries = {0};
        h.postDelayed(new Runnable() {
            public void run() {
                if (MagpieAppState.running && MagpieAppState.webKey != null && MagpieAppState.webKey.length() > 0) {
                    loadUi();
                    return;
                }
                tries[0]++;
                if (tries[0] < 240) h.postDelayed(this, 250);
                else showError();
            }
        }, 250);
    }

    private void loadUi() {
        String base = "http://127.0.0.1:" + MagpieAppState.port + "/?k=" + MagpieAppState.webKey;
        if (pendingView != null) base += "&view=" + pendingView;
        lastUrl = base;
        web.loadUrl(base);
    }

    private void showError() {
        web.loadData("<html><body style='font:16px sans-serif;padding:24px'>"
                + "<h3>magpie 本地服务未就绪</h3><p>Go 服务未能在超时内监听 127.0.0.1:" + MagpieAppState.port + "。</p>"
                + "</body></html>", "text/html", "utf-8");
    }

    /** Return true if we handled (consumed) the URL ourselves. */
    public boolean handleUrl(String url) {
        if (url == null) return false;
        Uri u = Uri.parse(url);
        String scheme = u.getScheme() == null ? "" : u.getScheme();
        if ("magpie".equals(scheme)) { handleDeepLink(url); return true; }
        String host = u.getHost();
        if (host != null && (host.equals("127.0.0.1") || host.equals("localhost"))) return false;
        if ("http".equals(scheme) || "https".equals(scheme)) { openExternal(url); return true; }
        if ("about".equals(scheme) || "data".equals(scheme) || "blob".equals(scheme) || "javascript".equals(scheme)) return false;
        openExternal(url);
        return true;
    }

    /** Custom Tabs preferred, falls back to any browser. */
    public void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("android.support.customtabs.extra.EXTRA_ENABLE_INSTANT_APPS", true);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开外部链接", Toast.LENGTH_SHORT).show();
        }
    }

    public void doCopy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && text != null) {
                cm.setPrimaryClip(ClipData.newPlainText("magpie", text));
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) { Log.w(TAG, "copy: " + t); }
    }

    public void setTextZoom(int pct) {
        try { if (web != null) web.getSettings().setTextZoom(pct); } catch (Throwable ignored) {}
    }

    public void applySystemBars(String colorHex, boolean lightIcons) {
        try {
            int color = Color.parseColor(colorHex);
            getWindow().setStatusBarColor(color);
            if (Build.VERSION.SDK_INT >= 26) getWindow().setNavigationBarColor(color);
            int flags = 0;
            if (Build.VERSION.SDK_INT >= 23) flags |= lightIcons ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
            if (Build.VERSION.SDK_INT >= 26) flags |= lightIcons ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable t) { Log.w(TAG, "systemBars: " + t); }
    }

    public boolean onShowFileChooser(ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams params) {
        if (mFilePathCallback != null) { try { mFilePathCallback.onReceiveValue(null); } catch (Throwable ignored) {} }
        mFilePathCallback = cb;
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            String[] accept = params != null ? params.getAcceptTypes() : null;
            if (accept != null) {
                ArrayList<String> mimes = new ArrayList<String>();
                for (String a : accept) if (a != null && a.length() > 0) mimes.add(a);
                if (!mimes.isEmpty()) i.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
            }
            startActivityForResult(Intent.createChooser(i, "选择文件"), REQ_FILE);
            return true;
        } catch (Throwable t) {
            mFilePathCallback = null;
            return false;
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE) {
            Uri[] result = null;
            if (res == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    result = new Uri[cd.getItemCount()];
                    for (int k = 0; k < cd.getItemCount(); k++) result[k] = cd.getItemAt(k).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            if (mFilePathCallback != null) { mFilePathCallback.onReceiveValue(result); mFilePathCallback = null; }
        }
    }

    public void recoverWebView() {
        try {
            if (web != null) { web.destroy(); web = null; }
            buildWebView();
            if (lastUrl != null) web.loadUrl(lastUrl);
        } catch (Throwable t) { Log.e(TAG, "recoverWebView", t); }
    }

    private void handleIntent(Intent intent, boolean onNew) {
        if (intent == null) return;
        if (intent.hasExtra("magpie.view")) pendingView = intent.getStringExtra("magpie.view");
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            handleDeepLink(intent.getData().toString());
        }
    }

    public void handleDeepLink(String url) {
        try {
            Uri u = Uri.parse(url);
            String importData = u.getQueryParameter("import");
            if (importData != null) {
                pendingView = "providers";
                if (MagpieAppState.running && MagpieAppState.webKey.length() > 0) {
                    String base = "http://127.0.0.1:" + MagpieAppState.port + "/?k=" + MagpieAppState.webKey
                            + "&view=providers&import=" + Uri.encode(importData);
                    if (web != null) web.loadUrl(base);
                }
            }
        } catch (Throwable t) { Log.w(TAG, "deepLink: " + t); }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, true);
        if (pendingView != null && MagpieAppState.running && web != null) loadUi();
    }

    /** Open the diagnostics center (used by launcher shortcut / future menu). */
    public void openDiagnostics() {
        try { startActivity(new Intent(this, DiagnosticsActivity.class)); } catch (Throwable t) { Log.w(TAG, "diag: " + t); }
    }

    @Override public void onBackPressed() {
        if (web != null) {
            web.evaluateJavascript(
                "window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',keyCode:27,which:27,bubbles:true}));",
                null);
            if (web.canGoBack()) { web.goBack(); return; }
        }
        moveTaskToBack(true); // keep the gateway service alive
    }

    @Override protected void onDestroy() {
        if (INSTANCE == this) INSTANCE = null;
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
