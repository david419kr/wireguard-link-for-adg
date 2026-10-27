// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** A system-discoverable toggle; adding the tile is left to the OS panel editor. */
public final class TunnelTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        refresh();
    }
    @Override public void onTileAdded() {
        super.onTileAdded();
        refresh();
    }
    @Override public void onClick() {
        super.onClick();
        if (isLocked()) unlockAndRun(this::toggle);
        else toggle();
    }
    private void toggle() {
        try {
            if (TunnelController.isEnabled()) TunnelController.setEnabled(this, false);
            else if (TunnelController.selectedId() == null) openApp();
            else {
                boolean permission = TunnelController.needsVpnPermission(this);
                TunnelController.setEnabled(this, true);
                if (permission) openApp();
            }
        } catch (RuntimeException unavailable) { openApp(); }
        refresh();
    }
    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) return;
        String name = TunnelController.selectedName();
        tile.setLabel(TunnelController.selectedId() == null ? getString(com.adguard.vpn.R.string.app_name) : name);
        tile.setState(TunnelController.isEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this, com.adguard.vpn.R.drawable.ic_shield_status));
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(TunnelController.status());
        tile.setContentDescription(name + " · " + TunnelController.status());
        tile.updateTile();
    }
    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 41, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else startActivityAndCollapse(intent);
    }
}
