// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

/** Observed identifiers retained so OS shortcuts and saved routine references keep their addresses. */
public final class LegacyShortcutContract {
    public static final String ID = "TOGGLE_CONNECTION_ID";
    public static final String ACTION = "com.adguard.android.ACTION_TOGGLE_CONNECTION";
    public static final String ACTIVITY = "com.adguard.vpn.ui.ShortcutActivity";
    public static final String LAUNCHER = "com.adguard.vpn.ui.routing_activity.SplashActivity";
    public static final int INTENT_FLAGS = 0x40020000;
    public static Boolean nextEnabled(String action, boolean enabled) {
        return ACTION.equals(action) ? !enabled : null;
    }
    private LegacyShortcutContract() {}
}
