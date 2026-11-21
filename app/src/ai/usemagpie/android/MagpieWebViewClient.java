package ai.usemagpie.android;

import android.net.Uri;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MagpieWebViewClient extends WebViewClient {
    private final MainActivity act;
    public MagpieWebViewClient(MainActivity a) { this.act = a; }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
        Uri u = req.getUrl();
        return act.handleUrl(u == null ? null : u.toString());
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return act.handleUrl(url);
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        act.recoverWebView();
        return true;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        act.onPageFinished(url);
    }
}
