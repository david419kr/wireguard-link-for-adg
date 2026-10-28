// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0
package com.adguard.vpn.wireguard;

final class RecoveryPolicy {
    static long retryDelay(int failures) { return Math.min(60000L, 1000L << Math.min(6, Math.max(0, failures))); }
    static boolean retrySession(int failures) { return failures < 6; }
    static boolean waitForPeer(boolean enabled, boolean installed, boolean ready) { return enabled && installed && !ready; }
    static boolean keepAfterRevoke(boolean integration, boolean installed, boolean ready, boolean working) {
        return integration && installed && (!ready || working);
    }
    static boolean revokeTimedOut(boolean revoked, boolean working) { return revoked && !working; }
    private RecoveryPolicy() {}
}
