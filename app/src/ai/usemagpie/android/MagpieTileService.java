package ai.usemagpie.android;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class MagpieTileService extends TileService {
    @Override public void onStartListening() { update(); }

    private void update() {
        if (Build.VERSION.SDK_INT < 24) return;
        Tile t = getQsTile();
        if (t == null) return;
        boolean r = MagpieAppState.running;
        t.setState(r ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Magpie");
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(r ? "网关运行中" : "已停止");
        t.updateTile();
    }

    @Override public void onClick() {
        if (MagpieAppState.running) {
            Intent s = new Intent(this, MagpieGatewayService.class);
            s.setAction(MagpieGatewayService.ACTION_STOP);
            startService(s);
        } else {
            Intent s = new Intent(this, MagpieGatewayService.class);
            s.setAction(MagpieGatewayService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        }
        update();
    }
}
