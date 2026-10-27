// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

/** Host-JVM checks for the independently observed synchronization rules. */
public final class IntegrationStateTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Integration state check " + checks);
    }
    public static void main(String[] args) {
        for (boolean ag : new boolean[]{false, true}) for (boolean vpn : new boolean[]{false, true})
            check(IntegrationManager.workState(false, ag, vpn) == 0);
        check(IntegrationManager.workState(true, false, false) == 1);
        check(IntegrationManager.workState(true, true, false) == 2);
        check(IntegrationManager.workState(true, false, true) == 3);
        check(IntegrationManager.workState(true, true, true) == 4);
        check(!IntegrationManager.mergeEnabled(true, 10, false, 20));
        check(IntegrationManager.mergeEnabled(false, 10, true, 20));
        check(IntegrationManager.mergeEnabled(true, 20, false, 10));
        check(!IntegrationManager.mergeEnabled(false, 20, true, 10));
        check(IntegrationManager.mergeEnabled(false, 10, true, 10));
        check(IntegrationManager.mergeEnabled(true, 10, false, 10));
        check(!IntegrationManager.mergeEnabled(false, 10, false, 10));
        System.out.println("Integration state checks passed: " + checks);
    }
}
