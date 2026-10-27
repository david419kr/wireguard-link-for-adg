// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

final class ConnectionVisuals {
    static boolean active(boolean connected, String selected, String running) {
        return connected && selected != null && selected.equals(running);
    }
    private ConnectionVisuals() {}
}
