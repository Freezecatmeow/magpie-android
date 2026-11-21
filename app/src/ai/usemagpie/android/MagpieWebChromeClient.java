package ai.usemagpie.android;

import android.net.Uri;
import android.os.Message;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MagpieWebChromeClient extends WebChromeClient {
    private final MainActivity act;
    public MagpieWebChromeClient(MainActivity a) { this.act = a; }

    @Override
    public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams params) {
        return act.onShowFileChooser(cb, params);
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        final WebView tmp = new WebView(act);
        tmp.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                act.openExternal(r.getUrl().toString());
                v.destroy();
                return true;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                act.openExternal(url);
                v.destroy();
                return true;
            }
        });
        WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
        t.setWebView(tmp);
        resultMsg.sendToTarget();
        return true;
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage m) {
        Log.i("MagpieWeb", m.message() + " @" + m.sourceId() + ":" + m.lineNumber());
        return true;
    }
}
