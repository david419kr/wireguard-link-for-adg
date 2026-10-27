// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import com.wireguard.config.Config;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Peer;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** A foreground owner for either a real Android TUN or an authenticated local netstack proxy. */
public final class TunnelService extends VpnService {
    private static final String ACTION_DISCONNECT = "com.adguard.vpn.wireguard.DISCONNECT";
    private static final String CHANNEL = "wireguard-tunnel";
    private static final int NOTIFICATION = 4101;
    private HandlerThread thread;
    private Handler worker;
    private volatile long handle;
    private volatile String runningProfile;
    private String runningConfig;
    private boolean proxy, foreground, destroyed;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networks;
    private final Runnable reconcile = this::reconcile;

    @Override public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("wireguard-tunnel"); thread.start();
        worker = new Handler(thread.getLooper());
        TunnelController.init(this);
        IntegrationManager.init(this);
        TunnelController.attach(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, UiText.t(this, "WireGuard 연결"), NotificationManager.IMPORTANCE_LOW));
        connectivity = getSystemService(ConnectivityManager.class);
        networks = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { rebindSoon(); }
            @Override public void onLost(Network network) { rebindSoon(); }
        };
        try {
            connectivity.registerNetworkCallback(new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), networks);
        } catch (RuntimeException unavailable) { networks = null; }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction()))
            TunnelController.setEnabled(this, false);
        showNotification();
        reconcileSoon();
        return TunnelController.isEnabled() ? START_STICKY : START_NOT_STICKY;
    }

    void reconcileSoon() {
        if (!destroyed && worker != null) { worker.removeCallbacks(reconcile); worker.post(reconcile); }
    }

    private void reconcile() {
        if (destroyed) return;
        if (!TunnelController.isEnabled()) { stopTunnel(); return; }
        try {
            WireGuardProfile profile = TunnelController.selectedProfile();
            if (profile == null) throw new IllegalStateException("No selected tunnel");
            if (IntegrationManager.isEnabled() && IntegrationManager.isInstalled() && !IntegrationManager.isPeerReady()) {
                IntegrationManager.ensureConnection();
                TunnelController.update(false, "AdG 연동 상태 확인 중", "AdG 통합");
                showNotification();
                IntegrationManager.awaitInitialState(1500);
                if (!IntegrationManager.isPeerReady()) {
                    closeEngine();
                    TunnelController.update(false, "AdG를 열거나 연동을 꺼주세요", "AdG 통합");
                    showNotification();
                    return;
                }
            }
            boolean wantedProxy = TunnelController.integrated();
            String mode = wantedProxy ? "AdG 통합" : "WireGuard VPN";
            if (handle != 0 && proxy == wantedProxy && profile.id.equals(runningProfile) && profile.config.equals(runningConfig)) {
                showNotification(); return;
            }
            closeEngine();
            TunnelController.update(false, "연결 중", mode);
            showNotification();
            if (!wantedProxy && VpnService.prepare(this) != null) {
                TunnelController.update(false, "VPN 권한이 필요합니다 · 앱을 열어 주세요", mode);
                showNotification();
                return;
            }
            Config cfg = profile.parsed();
            List<String> local = new ArrayList<>(), dns = new ArrayList<>(), routes = new ArrayList<>();
            for (InetNetwork address : cfg.getInterface().getAddresses()) local.add(address.getAddress().getHostAddress());
            for (InetAddress server : cfg.getInterface().getDnsServers()) dns.add(server.getHostAddress());
            for (Peer peer : cfg.getPeers()) for (InetNetwork route : peer.getAllowedIps()) routes.add(route.toString());
            int mtu = cfg.getInterface().getMtu().orElse(1280);
            // Load JNI before transferring ownership of an Android descriptor.
            NativeEngine.sockets(0);
            int fd = -1;
            if (!wantedProxy) {
                ParcelFileDescriptor tun = buildTun(profile, cfg, mtu);
                if (tun == null) throw new IllegalStateException("VPN interface unavailable");
                fd = tun.detachFd();
            }
            handle = NativeEngine.start(cfg.toWgUserspaceString(), String.join(",", local), String.join(",", dns), mtu, fd);
            if (handle == 0) throw new IllegalStateException("Native tunnel unavailable");
            proxy = wantedProxy;
            if (!proxy) protectSockets();
            if (proxy) {
                boolean listening = false;
                for (int attempt = 0; attempt < 3; attempt++) {
                    String error = NativeEngine.startSocks(handle, "127.0.0.1", TunnelController.proxyPort(),
                            TunnelController.proxyUsername(), TunnelController.proxyPassword(), String.join(",", routes));
                    if (error == null || error.isEmpty()) { listening = true; break; }
                    TunnelController.changeProxyPort();
                }
                if (!listening) throw new IllegalStateException("Local proxy unavailable");
            }
            runningProfile = profile.id;
            runningConfig = profile.config;
            TunnelController.update(true, "연결됨", mode);
            showNotification();
        } catch (Exception | LinkageError failure) {
            closeEngine();
            // Native/parser errors may contain configuration values; expose no exception text.
            android.util.Log.w("WireGuardTunnel", "Tunnel start failed: " + failure.getClass().getSimpleName());
            TunnelController.failure("연결하지 못했습니다 · 설정과 네트워크를 확인해 주세요");
            showNotification();
            stopForeground(STOP_FOREGROUND_DETACH);
            foreground = false;
            stopSelf();
        }
    }

    private ParcelFileDescriptor buildTun(WireGuardProfile profile, Config config, int mtu) throws Exception {
        Builder builder = new Builder().setSession(profile.name).setMtu(mtu).setMetered(false);
        for (InetNetwork address : config.getInterface().getAddresses()) builder.addAddress(address.getAddress(), address.getMask());
        for (Peer peer : config.getPeers()) for (InetNetwork route : peer.getAllowedIps()) builder.addRoute(route.getAddress(), route.getMask());
        for (InetAddress dns : config.getInterface().getDnsServers()) builder.addDnsServer(dns);
        if (config.getInterface().getIncludedApplications().isEmpty()) {
            builder.addDisallowedApplication(getPackageName());
            for (String pkg : config.getInterface().getExcludedApplications()) {
                if (getPackageName().equals(pkg)) continue;
                try { builder.addDisallowedApplication(pkg); } catch (PackageManager.NameNotFoundException removed) { }
            }
        } else {
            int included = 0;
            for (String pkg : config.getInterface().getIncludedApplications()) {
                if (getPackageName().equals(pkg)) continue;
                try { builder.addAllowedApplication(pkg); included++; } catch (PackageManager.NameNotFoundException removed) { }
            }
            if (included == 0) throw new IllegalStateException("No included applications are installed");
        }
        return builder.establish();
    }

    private void rebindSoon() {
        if (destroyed || worker == null) return;
        worker.post(() -> {
            if (handle == 0 || destroyed) return;
            try {
                if (NativeEngine.networkChanged(handle) && !proxy) protectSockets();
            } catch (RuntimeException | LinkageError failure) {
                closeEngine();
                TunnelController.update(false, "재연결 중", TunnelController.modeKey());
                reconcileSoon();
            }
        });
    }
    private void protectSockets() {
        for (int fd : NativeEngine.sockets(handle)) {
            if (fd >= 0 && !protect(fd)) throw new IllegalStateException("Socket protection unavailable");
        }
    }
    String statistics() {
        long current = handle;
        if (current == 0) return "";
        try { String value = NativeEngine.statistics(current); return value == null ? "" : value; }
        catch (RuntimeException | LinkageError stopped) { return ""; }
    }
    String runningProfileId() { return runningProfile; }
    private void closeEngine() {
        long previous = handle; handle = 0;
        if (previous != 0) try { NativeEngine.stop(previous); } catch (RuntimeException | LinkageError stopped) { }
        runningProfile = runningConfig = null;
    }
    private void stopTunnel() {
        closeEngine();
        TunnelController.update(false, "연결 해제됨", TunnelController.modeKey());
        // Keep a dismissible reconnect action without keeping the service in the foreground.
        if (foreground) { stopForeground(STOP_FOREGROUND_DETACH); foreground = false; }
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
        stopSelf();
    }
    private void showNotification() {
        if (destroyed) return;
        startForeground(NOTIFICATION, notification());
        foreground = true;
    }
    private Notification notification() {
        Intent open = new Intent().setClassName(this, "com.adguard.vpn.wireguard.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent action = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean connected = ConnectionVisuals.active(TunnelController.isConnected(),
                TunnelController.selectedId(), TunnelController.connectedId());
        int icon = getResources().getIdentifier(connected ? "ic_shield_status" : "ic_shield_inactive", "drawable", getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_lock_lock;
        boolean enabled = TunnelController.isEnabled();
        Bundle iconPolicy = new Bundle();
        // One UI otherwise substitutes the launcher icon in the notification header.
        iconPolicy.putBoolean("android.showSmallIcon", !connected);
        PendingIntent control;
        if (enabled) {
            Intent disconnect = new Intent(this, TunnelService.class).setAction(ACTION_DISCONNECT);
            control = PendingIntent.getForegroundService(this, 1, disconnect,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            control = PendingIntent.getActivity(this, 2, new Intent(this, NotificationConnectActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(icon)
                .addExtras(iconPolicy)
                .setColor(connected ? 0xff76a645 : 0xffa4a7b0)
                .setContentTitle(TunnelController.status())
                .setContentText(TunnelController.selectedName() + " · " + TunnelController.mode())
                .setContentIntent(action).setOngoing(enabled)
                .addAction(new Notification.Action.Builder(null, enabled ? UiText.t(this, "연결 해제") : UiText.t(this, "연결"), control).build())
                .setCategory(Notification.CATEGORY_SERVICE).setOnlyAlertOnce(true).build();
    }
    static void refreshPostedNotification(Context context) {
        if (TunnelController.isEnabled()) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        for (StatusBarNotification posted : manager.getActiveNotifications()) {
            if (posted.getId() != NOTIFICATION) continue;
            Bundle policy = new Bundle(); policy.putBoolean("android.showSmallIcon", true);
            Notification updated = Notification.Builder.recoverBuilder(context, posted.getNotification())
                    .setSmallIcon(com.adguard.vpn.R.drawable.ic_shield_inactive)
                    .addExtras(policy).setColor(0xffa4a7b0).setOnlyAlertOnce(true).build();
            manager.notify(NOTIFICATION, updated);
        }
    }
    @Override public void onRevoke() { TunnelController.setEnabled(this, false); }
    @Override public void onDestroy() {
        destroyed = true;
        if (networks != null) try { connectivity.unregisterNetworkCallback(networks); } catch (RuntimeException ignored) { }
        TunnelController.detach(this);
        if (worker != null) {
            worker.removeCallbacksAndMessages(null);
            worker.post(() -> { closeEngine(); thread.quitSafely(); });
        }
        TunnelController.update(false, TunnelController.isEnabled() ? "연결이 중단되었습니다" : TunnelController.statusKey(), TunnelController.modeKey());
        super.onDestroy();
    }
}
