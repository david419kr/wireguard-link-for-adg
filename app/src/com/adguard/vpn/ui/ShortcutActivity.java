// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.adguard.vpn.wireguard.LegacyShortcutContract;
import com.adguard.vpn.wireguard.MainActivity;
import com.adguard.vpn.wireguard.NotificationConnectActivity;
import com.adguard.vpn.wireguard.TunnelController;

/** Independent entry point at the original app-shortcut component address. */
public final class ShortcutActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved == null) {
            Boolean enabled = LegacyShortcutContract.nextEnabled(getIntent().getAction(), TunnelController.isEnabled());
            if (enabled != null) {
                try {
                    if (enabled && (TunnelController.selectedId() == null || TunnelController.needsVpnPermission(this)))
                        startActivity(new Intent(this, NotificationConnectActivity.class));
                    else TunnelController.setEnabled(this, enabled);
                } catch (RuntimeException unavailable) { startActivity(new Intent(this, MainActivity.class)); }
            }
        }
        finish();
    }
}
