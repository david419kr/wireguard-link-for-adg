// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import java.util.Collections;

public final class CompatibilityShortcuts {
    private static String publishedLabel;
    public static synchronized void refresh(Context context) {
        String label = TunnelController.isEnabled() ? UiText.t(context, "끄기") : UiText.t(context, "켜기");
        if (label.equals(publishedLabel)) return;
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return;
        Intent intent = new Intent(LegacyShortcutContract.ACTION)
                .setClassName(context, LegacyShortcutContract.ACTIVITY).setFlags(LegacyShortcutContract.INTENT_FLAGS);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, LegacyShortcutContract.ID)
                .setActivity(new ComponentName(context, LegacyShortcutContract.LAUNCHER))
                .setShortLabel(label).setIcon(Icon.createWithResource(context, com.adguard.vpn.R.drawable.ic_launcher_foreground))
                .setIntent(intent).build();
        try {
            // Reuse the existing ID; never delete pinned shortcuts or stored routine references.
            if (manager.setDynamicShortcuts(Collections.singletonList(shortcut))) publishedLabel = label;
        } catch (RuntimeException unavailable) {
            android.util.Log.w("WireGuardShortcuts", "Shortcut publish failed: " + unavailable.getClass().getSimpleName());
        }
    }
    private CompatibilityShortcuts() {}
}
