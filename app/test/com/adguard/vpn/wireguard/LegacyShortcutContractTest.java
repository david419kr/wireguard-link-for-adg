// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

public final class LegacyShortcutContractTest {
    public static void main(String[] args) {
        check("TOGGLE_CONNECTION_ID".equals(LegacyShortcutContract.ID));
        check("com.adguard.android.ACTION_TOGGLE_CONNECTION".equals(LegacyShortcutContract.ACTION));
        check("com.adguard.vpn.ui.ShortcutActivity".equals(LegacyShortcutContract.ACTIVITY));
        check("com.adguard.vpn.ui.routing_activity.SplashActivity".equals(LegacyShortcutContract.LAUNCHER));
        check(LegacyShortcutContract.INTENT_FLAGS == 1073872896);
        check(Boolean.TRUE.equals(LegacyShortcutContract.nextEnabled(LegacyShortcutContract.ACTION, false)));
        check(Boolean.FALSE.equals(LegacyShortcutContract.nextEnabled(LegacyShortcutContract.ACTION, true)));
        check(LegacyShortcutContract.nextEnabled(null, false) == null);
        check(LegacyShortcutContract.nextEnabled("unknown", true) == null);
        System.out.println("Legacy shortcut compatibility checks passed: 9");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Legacy shortcut contract mismatch"); }
}
