package ai.usemagpie.android;

import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/** Local loopback micro HTTP server with one-time Bearer token guarding Go -> Android callbacks (T05). */
public class AndroidHostBridge {
    private static final String TAG = "MagpieBridge";

    public interface Actions {
        void openUrl(String url);
        void copy(String text);
        void notify(String channel, String title, String body, String view);
        void wakeLock(boolean on);
        void theme(String color, boolean dark);
        void textSize(int pct);
        void quit();
    }

    private final Actions actions;
    private final String token;
    private volatile boolean running = false;
    private ServerSocket server;
    private Thread thread;
    private int port;

    public AndroidHostBridge(Actions a, String token) { this.actions = a; this.token = token; }

    public int start() throws Exception {
        server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        port = server.getLocalPort();
        running = true;
        thread = new Thread(new Runnable() {
            public void run() {
                while (running) {
                    try { Socket s = server.accept(); handle(s); }
                    catch (Throwable t) { if (running) Log.w(TAG, "accept: " + t); }
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return port;
    }

    public int port() { return port; }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Throwable ignored) {}
    }

    private void handle(Socket s) {
        try {
            InputStream in = s.getInputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int prev = 0, cur;
            while ((cur = in.read()) != -1) {
                head.write(cur);
                byte[] a = head.toByteArray();
                int n = a.length;
                if (n >= 4 && a[n - 4] == '\r' && a[n - 3] == '\n' && a[n - 2] == '\r' && a[n - 1] == '\n') break;
                prev = cur;
            }
            String headStr = new String(head.toByteArray(), "UTF-8");
            String[] lines = headStr.split("\r\n");
            String reqLine = lines.length > 0 ? lines[0] : "";
            String[] parts = reqLine.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            int contentLength = 0;
            String auth = null;
            for (int i = 1; i < lines.length; i++) {
                int c = lines[i].indexOf(':');
                if (c <= 0) continue;
                String k = lines[i].substring(0, c).trim();
                String v = lines[i].substring(c + 1).trim();
                if (k.equalsIgnoreCase("Content-Length")) { try { contentLength = Integer.parseInt(v); } catch (Exception ignored) {} }
                if (k.equalsIgnoreCase("Authorization")) auth = v;
            }
            byte[] body = new byte[contentLength];
            int read = 0;
            while (read < contentLength) { int r = in.read(body, read, contentLength - read); if (r < 0) break; read += r; }
            String bodyStr = new String(body, "UTF-8");

            if (auth == null || !auth.equals("Bearer " + token)) { respond(s, 401, "{\"error\":\"unauthorized\"}"); return; }
            dispatch(path, bodyStr);
            respond(s, 200, "{\"ok\":true}");
        } catch (Throwable t) {
            Log.w(TAG, "handle: " + t);
        } finally {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    private void dispatch(String path, String body) {
        JSONObject jo;
        try { jo = body == null || body.length() == 0 ? new JSONObject() : new JSONObject(body); }
        catch (Exception e) { jo = new JSONObject(); }
        if (path.startsWith("/host/open-url")) actions.openUrl(jo.optString("url", ""));
        else if (path.startsWith("/host/copy")) actions.copy(jo.optString("text", ""));
        else if (path.startsWith("/host/notify")) actions.notify(jo.optString("channel", NotificationHelper.CH_QUOTA), jo.optString("title", "Magpie"), jo.optString("body", ""), jo.optString("view", null));
        else if (path.startsWith("/host/wake-lock")) actions.wakeLock(jo.optBoolean("on", false));
        else if (path.startsWith("/host/theme")) actions.theme(jo.optString("color", "#FFFFFF"), jo.optBoolean("dark", false));
        else if (path.startsWith("/host/text-size")) actions.textSize(jo.optInt("pct", 100));
        else if (path.startsWith("/host/quit")) actions.quit();
    }

    private void respond(Socket s, int code, String json) {
        try {
            OutputStream o = s.getOutputStream();
            byte[] b = json.getBytes("UTF-8");
            String header = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Error") + "\r\n"
                    + "Content-Type: application/json\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
            o.write(header.getBytes("UTF-8"));
            o.write(b);
            o.flush();
        } catch (Throwable ignored) {}
    }
}
