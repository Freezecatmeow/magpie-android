package ai.usemagpie.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** FR-21: widget pulls /api/usage/quotas off the main thread with three degraded states + manual refresh. */
public class MagpieQuotaWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "ai.usemagpie.widget.REFRESH";

    @Override public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) { refreshAsync(c, mgr, ids); }

    @Override public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(c, MagpieQuotaWidget.class));
            refreshAsync(c, mgr, ids);
        }
    }

    private void refreshAsync(final Context c, final AppWidgetManager mgr, final int[] ids) {
        boolean running = MagpieAppState.running;
        for (int id : ids) {
            RemoteViews rv = baseViews(c);
            rv.setTextViewText(R.id.widget_body, running ? "额度读取中…" : "网关未启动（降级）");
            attachRefresh(c, rv, id);
            mgr.updateAppWidget(id, rv);
        }
        if (!running) return; // degraded: no network IO when gateway down
        new Thread(new Runnable() { public void run() {
            String text = fetchQuota(c); // runs off the main thread
            for (int id : ids) {
                RemoteViews rv = baseViews(c);
                rv.setTextViewText(R.id.widget_body, text);
                attachRefresh(c, rv, id);
                mgr.updateAppWidget(id, rv);
            }
        } }).start();
    }

    private RemoteViews baseViews(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_quota);
        rv.setTextViewText(R.id.widget_title, "Magpie · " + (MagpieAppState.running ? "运行中" : "未启动"));
        return rv;
    }

    private void attachRefresh(Context c, RemoteViews rv, int id) {
        Intent i = new Intent(c, MagpieQuotaWidget.class);
        i.setAction(ACTION_REFRESH);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        rv.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(c, id, i, flags));
    }

    private String fetchQuota(Context c) {
        HttpURLConnection h = null;
        try {
            String key = readKey(c);
            h = (HttpURLConnection) new URL("http://127.0.0.1:" + MagpieAppState.port + "/api/usage/quotas").openConnection();
            h.setConnectTimeout(3000); h.setReadTimeout(4000);
            if (key != null && key.length() > 0) h.setRequestProperty("Authorization", "Bearer " + key);
            int sc = h.getResponseCode();
            if (sc != 200) return "接口异常 HTTP " + sc + "（降级）";
            return parseQuota(read(h.getInputStream()));
        } catch (Throwable t) {
            return "接口不可达（降级）";
        } finally { if (h != null) h.disconnect(); }
    }

    private String parseQuota(String body) {
        try {
            JSONObject o = new JSONObject(body);
            JSONArray arr = o.optJSONArray("quotas");
            if (arr == null) arr = o.optJSONArray("data");
            if (arr == null) return "额度数据格式未知（降级）";
            StringBuilder sb = new StringBuilder("额度：\n");
            int shown = 0;
            for (int i = 0; i < arr.length() && shown < 3; i++) {
                JSONObject q = arr.optJSONObject(i);
                if (q == null) continue;
                String name = q.optString("name", q.optString("provider", "#" + (i + 1)));
                double pct = q.optDouble("percent", q.optDouble("remainingPercent", -1));
                sb.append(name).append(": ").append(pct >= 0 ? String.format("%.0f%%", pct) : "?").append("\n");
                shown++;
            }
            if (shown == 0) sb.append("(无额度项)");
            String lan = MagpieAppState.lanIp;
            if (lan != null && lan.length() > 0) sb.append("LAN ").append(lan);
            return sb.toString().trim();
        } catch (Throwable t) { return "额度解析失败（降级）"; }
    }

    private String readKey(Context c) {
        try {
            if (MagpieAppState.webKey != null && MagpieAppState.webKey.length() > 0) return MagpieAppState.webKey;
            File f = new File(c.getFilesDir(), ".magpie_web_key");
            if (f.exists()) {
                FileInputStream in = new FileInputStream(f);
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[256]; int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                return new String(b.toByteArray(), "UTF-8").trim();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return new String(b.toByteArray(), "UTF-8");
    }
}
