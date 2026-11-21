package ai.usemagpie.android;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/** Best-effort aggregation of the Android system CA store into a PEM for Go/Bun (SSL_CERT_FILE/NODE_EXTRA_CA_CERTS). */
public final class CaBundleExporter {
    private CaBundleExporter() {}

    public static String export(Context c) {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidCAStore");
            ks.load(null, null);
            StringBuilder sb = new StringBuilder();
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                Certificate cert = ks.getCertificate(aliases.nextElement());
                if (cert instanceof X509Certificate) sb.append(pem((X509Certificate) cert));
            }
            File dir = new File(c.getCacheDir(), ".cache/magpie");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "android-ca-bundle.pem");
            FileOutputStream o = new FileOutputStream(out);
            o.write(sb.toString().getBytes("UTF-8"));
            o.close();
            return out.getAbsolutePath();
        } catch (Throwable t) {
            Log.w("MagpieCa", "ca export failed: " + t);
            return "";
        }
    }

    private static String pem(X509Certificate cert) throws Exception {
        String b64 = Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP);
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN CERTIFICATE-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64.substring(i, Math.min(i + 64, b64.length()))).append("\n");
        }
        sb.append("-----END CERTIFICATE-----\n");
        return sb.toString();
    }
}
