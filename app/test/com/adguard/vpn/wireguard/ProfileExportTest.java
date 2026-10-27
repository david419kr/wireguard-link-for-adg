// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Tests use synthetic keys and never print configuration material or real tunnel names. */
public final class ProfileExportTest {
    private static final String CONFIG = "[Interface]\nPrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAE=\nAddress = 10.10.0.2/32\nDNS = 1.1.1.1\n\n[Peer]\nPublicKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAI=\nAllowedIPs = 0.0.0.0/0, ::/0\nEndpoint = 192.0.2.1:51820\nPersistentKeepalive = 25\n";
    private static int checks;

    public static void main(String[] args) throws Exception {
        List<WireGuardProfile> normal = Arrays.asList(profile("first"), profile("두 번째"));
        List<WireGuardProfile> roundtrip = ProfileStore.decodeImport(ProfileStore.encodeExport(normal), "export.zip");
        check(roundtrip.size() == normal.size(), "roundtrip count");
        for (int i = 0; i < normal.size(); i++) {
            check(roundtrip.get(i).name.equals(normal.get(i).name), "roundtrip name");
            check(roundtrip.get(i).config.equals(normal.get(i).parsed().toWgQuickString()), "roundtrip configuration");
        }
        List<WireGuardProfile> unsafe = Arrays.asList(profile("../same"), profile("SAME"), profile("same"),
                profile("folder\\unsafe:name?"), profile("..."));
        Set<String> names = new HashSet<>(); int entries = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(ProfileStore.encodeExport(unsafe)), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                check(name.endsWith(".conf") && !name.contains("/") && !name.contains("\\")
                        && !name.contains(":") && !name.contains("?") && !name.startsWith("."), "safe basename");
                check(names.add(name.toLowerCase(Locale.ROOT)), "case-insensitive unique basename"); entries++;
            }
        }
        check(entries == unsafe.size(), "all unsafe names preserved as files");
        rejected(() -> ProfileStore.encodeExport(Collections.emptyList()));
        rejected(() -> ProfileStore.encodeExport(Collections.singletonList(new WireGuardProfile("bad", "bad", "not a config"))));
        List<WireGuardProfile> maximum = new ArrayList<>();
        for (int i = 0; i < 256; i++) maximum.add(profile("tunnel-" + i));
        check(ProfileStore.decodeImport(ProfileStore.encodeExport(maximum), "export.zip").size() == 256, "maximum count reimports");
        maximum.add(profile("one-too-many"));
        rejected(() -> ProfileStore.encodeExport(maximum));
        if (args.length == 1) {
            List<WireGuardProfile> real = ProfileStore.decodeImport(Files.readAllBytes(Path.of(args[0])), "supplied.zip");
            List<WireGuardProfile> restored = ProfileStore.decodeImport(ProfileStore.encodeExport(real), "restored.zip");
            check(real.size() == restored.size(), "supplied count roundtrip");
            for (int i = 0; i < real.size(); i++) {
                check(real.get(i).name.equals(restored.get(i).name), "supplied name roundtrip");
                check(real.get(i).config.equals(restored.get(i).config), "supplied config roundtrip");
            }
            System.out.println("Supplied profiles roundtripped: " + real.size() + " (secrets omitted).");
        }
        System.out.println("Profile export checks passed: " + checks);
    }

    private static WireGuardProfile profile(String name) { return new WireGuardProfile(name, name, CONFIG); }
    private interface Checked { void run() throws Exception; }
    private static void rejected(Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Invalid export accepted"); }
        catch (IOException expected) { checks++; }
    }
    private static void check(boolean pass, String label) { if (!pass) throw new AssertionError(label); checks++; }
}
