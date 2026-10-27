// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import java.io.IOException;

public final class ProfileEditTest {
    private static final String CONFIG = "[Interface]\nPrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAE=\nAddress = 10.10.0.2/32\n\n[Peer]\nPublicKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAI=\nAllowedIPs = 0.0.0.0/0\nEndpoint = 192.0.2.1:51820\n";
    private static int checks;
    public static void main(String[] args) throws Exception {
        WireGuardProfile previous = new WireGuardProfile("stable-id", "Old", CONFIG);
        WireGuardProfile renamed = ProfileStore.edited(previous, "  Home.conf  ", CONFIG);
        check(renamed.id.equals(previous.id));
        check(renamed.name.equals("Home.conf"));
        check(renamed.config.equals(CONFIG));
        check(previous.name.equals("Old") && previous.config.equals(CONFIG));
        String raw = "# retained comment\n" + CONFIG + "\n# end\n";
        WireGuardProfile edited = ProfileStore.edited(previous, "Home", raw);
        check(edited.id.equals(previous.id));
        check(edited.config.equals(raw));
        check(ProfileStore.edited(previous, "Home", "\uFEFF" + CONFIG).config.equals(CONFIG));
        rejected(() -> ProfileStore.edited(previous, "", CONFIG));
        rejected(() -> ProfileStore.edited(previous, " ", CONFIG));
        rejected(() -> ProfileStore.edited(previous, null, CONFIG));
        rejected(() -> ProfileStore.edited(previous, "x".repeat(81), CONFIG));
        rejected(() -> ProfileStore.edited(previous, "a\nb\nc", CONFIG));
        rejected(() -> ProfileStore.edited(previous, "a\tb", CONFIG));
        rejected(() -> ProfileStore.edited(previous, "Home", ""));
        rejected(() -> ProfileStore.edited(previous, "Home", null));
        rejected(() -> ProfileStore.edited(previous, "Home", "x".repeat(256 * 1024 + 1)));
        rejected(() -> ProfileStore.edited(previous, "Home", "한".repeat(100000)));
        rejected(() -> ProfileStore.edited(previous, "Home", "[Interface]\nPrivateKey = sensitive-invalid-value"));
        check(previous.id.equals("stable-id") && previous.config.equals(CONFIG));
        System.out.println("Profile edit checks passed: " + checks + " (configuration omitted)");
    }
    private interface Edit { void run() throws Exception; }
    private static void rejected(Edit edit) throws Exception {
        try { edit.run(); throw new AssertionError("Invalid edit accepted"); }
        catch (IOException expected) { check(!expected.getMessage().contains("sensitive-invalid-value")); }
    }
    private static void check(boolean pass) { checks++; if (!pass) throw new AssertionError("Invalid profile edit"); }
}
