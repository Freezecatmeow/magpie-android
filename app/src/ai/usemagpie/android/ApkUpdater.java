package ai.usemagpie.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/** FR-23 (host side): version check + APK download + SHA-256 verify + hand to system installer via ApkFileProvider.
 *  Never writes to nativeLibraryDir/libmagpie.so.
 *  NOTE (information gap): the real release metadata URL / JSON schema is not provided by the plan; DEFAULT_METADATA_URL is a placeholder. */
public final class ApkUpdater {
    public static final String DEFAULT_METADATA_URL = "https://example.invalid/magpie-android/latest.json";
    private ApkUpdater() {}

    public interface CheckCb { void done(boolean hasUpdate, String remoteVersion, int remoteCode, long size, String err); }
    public interface DlCb { void progress(int pct); void done(boolean ok, String err, File apk); }

    public static int localVersionCode(Context c) {
        try { PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0); return pi.versionCode; }
        catch (Throwable t) { return -1; }
    }

    public static void checkForUpdate(final Context c, final String url, final CheckCb cb) {
        new Thread(new Runnable() { public void run() {
            boolean has = false; String ver = ""; int code = -1; long size = 0; String err = null;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                h.setConnectTimeout(8000); h.setReadTimeout(8000);
                h.setRequestProperty("Accept", "application/json");
                int sc = h.getResponseCode();
                if (sc != 200) throw new Exception("HTTP " + sc);
                String body = readAll(h.getInputStream());
                org.json.JSONObject o = new org.json.JSONObject(body);
                code = o.optInt("versionCode", -1);
                ver = o.optString("versionName", "");
                size = o.optLong("size", 0);
                has = code > localVersionCode(c);
            } catch (Throwable t) { err = String.valueOf(t.getMessage()); }
            cb.done(has, ver, code, size, err);
        } }).start();
    }

    public static void downloadAndInstall(final Context c, final String url, final String expectedSha, final DlCb cb) {
        new Thread(new Runnable() { public void run() {
            File dir = new File(c.getCacheDir(), "updates");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "update.apk");
            File tmp = new File(dir, "update.apk.part");
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                h.setConnectTimeout(10000); h.setReadTimeout(20000);
                int total = h.getContentLength();
                InputStream in = h.getInputStream();
                FileOutputStream fo = new FileOutputStream(tmp);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] b = new byte[8192];
                long got = 0; int n;
                while ((n = in.read(b)) > 0) {
                    fo.write(b, 0, n); md.update(b, 0, n); got += n;
                    if (total > 0) cb.progress((int) (got * 100 / total));
                }
                fo.close(); in.close();
                String sha = hex(md.digest());
                if (expectedSha != null && expectedSha.length() > 0 && !expectedSha.equalsIgnoreCase(sha)) {
                    tmp.delete();
                    cb.done(false, "SHA-256 校验失败(期望 " + expectedSha + " 实际 " + sha + ")", null);
                    return;
                }
                if (out.exists()) out.delete();
                if (!tmp.renameTo(out)) { tmp.delete(); cb.done(false, "无法保存 APK", null); return; }
                cb.done(true, null, out);
            } catch (Throwable t) {
                try { tmp.delete(); } catch (Throwable ignored) {}
                cb.done(false, String.valueOf(t.getMessage()), null);
            }
        } }).start();
    }

    /** Hand a verified APK to the system installer through ApkFileProvider (content://), never touching jniLibs. */
    public static void install(Context c, File apk) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse("content://" + ApkFileProvider.AUTHORITY + "/" + apk.getName()),
                "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096]; int n;
        while ((n = in.read(b)) > 0) bos.write(b, 0, n);
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
