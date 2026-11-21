package ai.usemagpie.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.ProxyInfo;
import android.util.Log;

/** FR-02 / BUG-06 (host side): watch default network + system HTTP proxy and re-export CA on change. */
public class NetworkMonitor {
    public interface Callback {
        void onProxyChanged(String host, int port);
        void onNetworkChanged();
    }
    private static final String TAG = "MagpieNet";
    private final Context ctx;
    private final Callback cb;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback ncb;
    private volatile boolean registered = false;

    public NetworkMonitor(Context c, Callback cb) { this.ctx = c.getApplicationContext(); this.cb = cb; }

    public void start() {
        if (registered) return;
        try {
            cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            ncb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { refresh(n); }
                @Override public void onLinkPropertiesChanged(Network n, LinkProperties lp) { refresh(n); }
                @Override public void onLost(Network n) {
                    MagpieAppState.proxyHost = "";
                    MagpieAppState.proxyPort = 0;
                    cb.onProxyChanged("", 0);
                    cb.onNetworkChanged();
                }
            };
            cm.registerDefaultNetworkCallback(ncb);
            registered = true;
            Log.i(TAG, "network monitor registered");
        } catch (Throwable t) { Log.w(TAG, "start: " + t); }
    }

    private void refresh(Network n) {
        try {
            LinkProperties lp = cm.getLinkProperties(n);
            ProxyInfo p = lp == null ? null : lp.getHttpProxy();
            String h = "";
            int port = 0;
            if (p != null) { h = p.getHost() == null ? "" : p.getHost(); port = p.getPort(); }
            MagpieAppState.proxyHost = h;
            MagpieAppState.proxyPort = port;
            cb.onProxyChanged(h, port);
            cb.onNetworkChanged();
            Log.i(TAG, "proxy=" + (h.length() == 0 ? "DIRECT" : h + ":" + port));
        } catch (Throwable t) { Log.w(TAG, "refresh: " + t); }
    }

    public void stop() {
        try { if (cm != null && ncb != null && registered) cm.unregisterNetworkCallback(ncb); } catch (Throwable ignored) {}
        registered = false;
    }
}
