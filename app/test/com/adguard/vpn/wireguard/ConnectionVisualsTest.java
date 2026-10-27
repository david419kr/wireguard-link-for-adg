// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

public final class ConnectionVisualsTest {
    public static void main(String[] args) {
        check(!ConnectionVisuals.active(false, "home", "home"));
        check(ConnectionVisuals.active(true, "home", "home"));
        check(!ConnectionVisuals.active(true, "new", "old"));
        check(!ConnectionVisuals.active(true, null, "home"));
        check(!ConnectionVisuals.active(true, "home", null));
        check(!ConnectionVisuals.active(false, null, null));
        System.out.println("Connected-shield checks passed: 6");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Wrong connected shield state"); }
}
