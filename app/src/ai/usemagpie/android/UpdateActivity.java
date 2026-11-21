package ai.usemagpie.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/** FR-23: independent update panel — check metadata, download APK, verify SHA-256, hand to installer. */
public class UpdateActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button checkBtn, downloadBtn;
    private String remoteUrl = "";
    private String remoteSha = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        TextView t = new TextView(this);
        t.setText("Magpie 应用更新");
        t.setTextSize(20);
        t.setTextColor(Color.BLACK);
        root.addView(t);
        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, 16, 0, 16);
        status.setText("当前 versionCode=" + ApkUpdater.localVersionCode(this) + "\n（远端元数据地址为占位，接入发布规范后填入）");
        root.addView(status);

        checkBtn = new Button(this);
        checkBtn.setText("检查更新");
        checkBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doCheck(); } });
        root.addView(checkBtn);

        downloadBtn = new Button(this);
        downloadBtn.setText("下载并安装");
        downloadBtn.setEnabled(false);
        downloadBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doDownload(); } });
        root.addView(downloadBtn);

        setContentView(root);
    }

    private void doCheck() {
        status.setText("检查更新中…");
        ApkUpdater.checkForUpdate(this, ApkUpdater.DEFAULT_METADATA_URL, new ApkUpdater.CheckCb() {
            public void done(final boolean hasUpdate, final String ver, final int code, final long size, final String err) {
                ui.post(new Runnable() { public void run() {
                    if (err != null) { status.setText("检查失败: " + err); return; }
                    if (hasUpdate) { status.setText("发现新版本 " + ver + " (code " + code + ", " + size + " B)\n可下载安装"); downloadBtn.setEnabled(true); }
                    else status.setText("已是当前最新版本 (远端 code " + code + ")");
                } });
            }
        });
    }

    private void doDownload() {
        status.setText("下载中…");
        ApkUpdater.downloadAndInstall(this, remoteUrl, remoteSha, new ApkUpdater.DlCb() {
            public void progress(final int pct) { ui.post(new Runnable(){ public void run(){ status.setText("下载中… " + pct + "%"); }}); }
            public void done(final boolean ok, final String err, final File apk) {
                ui.post(new Runnable() { public void run() {
                    if (!ok) { status.setText("下载/校验失败: " + err); return; }
                    status.setText("SHA-256 校验通过，拉起系统安装器…");
                    try { ApkUpdater.install(UpdateActivity.this, apk); } catch (Throwable t) { status.setText("安装器拉起失败: " + t); }
                } });
            }
        });
    }
}
