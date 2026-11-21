package ai.usemagpie.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (i == null || !Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        SharedPreferences sp = c.getSharedPreferences("magpie", Context.MODE_PRIVATE);
        if (!sp.getBoolean("autostart", true)) return;
        Intent s = new Intent(c, MagpieGatewayService.class);
        s.setAction(MagpieGatewayService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
        } catch (Throwable ignored) {}
    }
}
