// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.util.Base64;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;

/** App-owned selection and desired state; all native changes belong to TunnelService's worker. */
public final class TunnelController {
    static final String ACTION_SYNC = "com.adguard.vpn.wireguard.SYNC";
    public static final String ACTION_STATE = "com.adguard.vpn.wireguard.STATE";
    private static Context context;
    private static SharedPreferences preferences;
    private static ProfileStore profiles;
    private static volatile TunnelService service;
    private static volatile boolean enabled, connected;
    private static volatile String connectedProfile;
    private static volatile String state = "연결 해제됨", transport = "WireGuard VPN";
    private static String username, password;
    private static int port;

    public static synchronized void init(Context source) {
        if (context != null) return;
        context = source.getApplicationContext();
        preferences = context.getSharedPreferences("standalone-tunnel", Context.MODE_PRIVATE);
        profiles = new ProfileStore(context);
        enabled = preferences.getBoolean("enabled", false);
        username = preferences.getString("proxy_username", null);
        password = preferences.getString("proxy_password", null);
        port = preferences.getInt("proxy_port", 0);
        if (username == null || password == null || port < 1 || port > 65535) {
            username = randomToken(12);
            password = randomToken(32);
            port = availablePort();
            preferences.edit().putString("proxy_username", username).putString("proxy_password", password)
                    .putInt("proxy_port", port).commit();
        }
        if (enabled) state = "연결 준비 중";
    }

    public static void select(String id) {
        requireInit();
        String previous = profiles.selectedId();
        profiles.select(id);
        if (id == null && enabled) setEnabled(context, false);
        else if (enabled && !equal(previous, id)) requestSync();
        signal();
    }

    public static void setEnabled(Context source, boolean value) {
        init(source);
        IntegrationManager.init(context);
        if (value) IntegrationManager.ensureConnection();
        if (value && profiles.get(profiles.selectedId()) == null)
            throw new IllegalStateException(UiText.t(context, "먼저 터널을 선택해 주세요."));
        enabled = value;
        preferences.edit().putBoolean("enabled", value).commit();
        if (value) state = needsVpnPermission(context) ? "VPN 권한이 필요합니다" : "연결 중";
        requestSync();
        signal();
    }

    public static boolean isEnabled() { return enabled; }
    public static void profileEdited(String id) {
        if (enabled && id != null && id.equals(selectedId())) requestSync();
        signal();
    }
    public static boolean isConnected() { return connected; }
    public static String connectedId() { return connectedProfile; }
    public static String selectedId() { return profiles == null ? null : profiles.selectedId(); }
    public static String selectedName() {
        try {
            WireGuardProfile profile = profiles == null ? null : profiles.get(profiles.selectedId());
            return profile == null ? UiText.t(context, "터널을 선택해 주세요") : profile.name;
        } catch (RuntimeException unavailable) { return UiText.t(context, "터널 저장소 오류"); }
    }
    public static String status() { return UiText.t(context, state); }
    public static String mode() { return UiText.t(context, transport); }
    static String modeKey() { return transport; }
    static String statusKey() { return state; }
    public static boolean needsVpnPermission(Context source) {
        init(source);
        if (IntegrationManager.isEnabled() && IntegrationManager.isInstalled() && !IntegrationManager.isPeerReady()) return false;
        return !integrated() && VpnService.prepare(context) != null;
    }
    public static void onIntegrationChanged() {
        if (context == null) return;
        if (enabled) requestSync();
        signal();
    }
    public static synchronized int proxyPort() { return port; }
    public static synchronized String proxyUsername() { return username; }
    public static synchronized String proxyPassword() { return password; }
    /** Only aggregate byte counters and handshake timestamps, never configuration or key material. */
    public static String statistics() {
        TunnelService current = service;
        return current == null ? "" : current.statistics();
    }

    static boolean integrated() { return IntegrationManager.isEnabled() && IntegrationManager.isAdGuardWorking(); }
    static WireGuardProfile selectedProfile() { return profiles.get(profiles.selectedId()); }
    static synchronized void attach(TunnelService value) { service = value; }
    static synchronized void detach(TunnelService value) { if (service == value) service = null; }
    static void update(boolean active, String message, String mode) {
        boolean integrationChanged = connected != active || !equal(transport, mode);
        TunnelService owner = service;
        connectedProfile = active && owner != null ? owner.runningProfileId() : null;
        connected = active;
        state = message;
        transport = mode;
        signal();
        if (integrationChanged) IntegrationManager.notifyStateChanged();
    }
    static void failure(String message) {
        enabled = false;
        preferences.edit().putBoolean("enabled", false).commit();
        update(false, message, transport);
    }
    static synchronized void changeProxyPort() {
        port = availablePort();
        preferences.edit().putInt("proxy_port", port).commit();
    }
    private static void requestSync() {
        TunnelService current = service;
        if (current != null) { current.reconcileSoon(); return; }
        if (!enabled) { update(false, "연결 해제됨", transport); return; }
        try {
            context.startForegroundService(new Intent(context, TunnelService.class).setAction(ACTION_SYNC));
        } catch (RuntimeException unavailable) {
            state = "앱을 열어 다시 연결해 주세요";
            signal();
        }
    }
    private static void signal() {
        if (context != null) {
            context.sendBroadcast(new Intent(ACTION_STATE).setPackage(context.getPackageName()));
            CompatibilityShortcuts.refresh(context);
            try { android.service.quicksettings.TileService.requestListeningState(context,
                    new android.content.ComponentName(context, TunnelTileService.class)); }
            catch (RuntimeException unavailable) { /* The current Android user may not expose this tile. */ }
        }
    }
    private static void requireInit() { if (context == null) throw new IllegalStateException("Application not initialized"); }
    private static boolean equal(String a, String b) { return a == null ? b == null : a.equals(b); }
    private static String randomToken(int size) {
        byte[] value = new byte[size]; new SecureRandom().nextBytes(value);
        return Base64.encodeToString(value, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }
    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { return socket.getLocalPort(); }
        catch (Exception unavailable) { return 20000 + new SecureRandom().nextInt(30000); }
    }
    private TunnelController() {}
}
