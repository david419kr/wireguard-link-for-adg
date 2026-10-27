// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;

/** A private, direct notification destination for the optional Android VPN consent flow. */
public final class NotificationConnectActivity extends Activity {
    private static final int VPN = 1;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) return;
        try {
            if (TunnelController.selectedId() == null) { openHome(); return; }
            if (TunnelController.needsVpnPermission(this)) {
                Intent consent = VpnService.prepare(this);
                if (consent != null) { startActivityForResult(consent, VPN); return; }
            }
            connect();
        } catch (RuntimeException unavailable) { openHome(); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == VPN) {
            if (result == RESULT_OK) connect();
            else finish();
        }
    }
    private void connect() {
        try { TunnelController.setEnabled(this, true); finish(); }
        catch (RuntimeException unavailable) { openHome(); }
    }
    private void openHome() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
}
