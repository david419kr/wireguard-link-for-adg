// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.app.Application;

public final class LinkApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        TunnelController.init(this);
        IntegrationManager.init(this);
        CompatibilityShortcuts.refresh(this);
        TunnelService.refreshPostedNotification(this);
    }
}
