// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import com.wireguard.config.Config;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Secrets stay in memory or the encrypted profile store; never stringify a profile. */
public final class WireGuardProfile {
    public final String id;
    public final String name;
    public final String config;

    public WireGuardProfile(String id, String name, String config) {
        this.id = id;
        this.name = name;
        this.config = config;
    }

    public Config parsed() throws IOException {
        try {
            return Config.parse(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            throw new IOException(UiText.t("유효한 WireGuard 설정이 아닙니다."));
        }
    }
}
