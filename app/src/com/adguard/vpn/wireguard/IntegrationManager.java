// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent implementation of the observed version-3 AdGuard Binder wire contract. */
public final class IntegrationManager {
    public static final String ADGUARD = "com.adguard.android";
    public static final String VPN = "com.adguard.vpn";
    private static final String LISTENER = "com.adguard.kit.integration.MegazordListener";
    private static final ExecutorService EVENTS = Executors.newSingleThreadExecutor();
    private static final Object READY = new Object();
    private static Context context;
    private static SharedPreferences preferences;
    private static IBinder adGuardListener;
    private static IBinder.DeathRecipient listenerDeath;
    private static volatile boolean adGuardWorking;
    private static volatile boolean peerStateReceived;
    private static Handler STARTUP;
    private static boolean serverBinding, startupPending;
    private static final ServiceConnection SERVER = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            android.util.Log.i("WireGuardIntegration", "Local integration service ready");
            notifyAdGuard();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (IntegrationManager.class) { serverBinding = false; }
            ensureConnection();
        }
        @Override public void onBindingDied(ComponentName name) {
            try { context.unbindService(this); } catch (RuntimeException ignored) {}
            synchronized (IntegrationManager.class) { serverBinding = false; }
            ensureConnection();
        }
    };

    private IntegrationManager() {}

    public static synchronized void init(Context ctx) {
        if (context != null) return;
        context = ctx.getApplicationContext();
        STARTUP = new Handler(Looper.getMainLooper());
        preferences = context.getSharedPreferences("adguard-integration", Context.MODE_PRIVATE);
        BroadcastReceiver synchronizeReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (intent != null && "synchronize".equals(intent.getAction())) {
                    ensureConnection();
                    EVENTS.execute(() -> { synchronize((byte) 0); synchronize((byte) 1); });
                }
            }
        };
        IntentFilter filter = new IntentFilter("synchronize");
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(synchronizeReceiver, filter, Context.RECEIVER_EXPORTED);
        else context.registerReceiver(synchronizeReceiver, filter);
        ensureConnection();
    }

    /** Start the broker before asking AdG to bind; the original app performs the same initialization. */
    public static void ensureConnection() {
        synchronized (IntegrationManager.class) {
            if (context == null || !isEnabled() || !installed() || startupPending) return;
            startupPending = true;
        }
        STARTUP.post(() -> {
            try {
                if (!serverBinding) {
                    Intent service = new Intent("com.adguard.kit.integration.Megazord")
                            .setClassName(VPN, "com.adguard.vpn.service.megazord.MegazordService");
                    serverBinding = context.bindService(service, SERVER, Context.BIND_AUTO_CREATE);
                }
                // A package-scoped, data-free wake-up. Credentials stay inside authenticated Binder calls.
                notifyAdGuard();
                STARTUP.postDelayed(() -> { if (listener() == null) notifyAdGuard(); }, 700);
                STARTUP.postDelayed(() -> { if (listener() == null) notifyAdGuard(); }, 2000);
            } catch (RuntimeException unavailable) {
                android.util.Log.w("WireGuardIntegration", "Integration startup failed: " + unavailable.getClass().getSimpleName());
            }
            STARTUP.postDelayed(() -> { synchronized (IntegrationManager.class) { startupPending = false; } }, 3000);
        });
    }

    public static void onServerCreated() { STARTUP.post(IntegrationManager::notifyAdGuard); }

    private static void notifyAdGuard() {
        if (context == null || !isEnabled() || listener() != null) return;
        context.sendBroadcast(new Intent("synchronize").setPackage(ADGUARD));
        android.util.Log.i("WireGuardIntegration", "Requested AdG integration synchronization");
    }

    public static boolean isEnabled() {
        return preferences != null && preferences.getBoolean("enabled", true);
    }

    public static boolean isAdGuardWorking() {
        return adGuardWorking;
    }

    public static boolean isInstalled() { return installed(); }

    /** Missing initial peer data is unknown, not proof that AdGuard protection is off. */
    public static boolean isPeerReady() {
        return !isEnabled() || !installed() || peerStateReceived;
    }

    /** Controller worker only; never call while holding a Binder/UI lock. */
    public static boolean awaitInitialState(long timeoutMs) {
        long end = android.os.SystemClock.uptimeMillis() + Math.max(0, Math.min(5000, timeoutMs));
        synchronized (READY) {
            while (!isPeerReady()) {
                long remaining = end - android.os.SystemClock.uptimeMillis();
                if (remaining <= 0) return false;
                try { READY.wait(remaining); }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); return false; }
            }
        }
        return true;
    }

    public static void setEnabled(boolean enabled) {
        if (preferences == null) return;
        preferences.edit().putBoolean("enabled", enabled).putLong("changed", System.currentTimeMillis()).apply();
        if (enabled) ensureConnection();
        synchronized (READY) { READY.notifyAll(); }
        TunnelController.onIntegrationChanged();
        EVENTS.execute(() -> {
            synchronize((byte) 0);
            signalPeer((byte) 1);
        });
    }

    public static String status() {
        if (!installed()) return UiText.t(context, "AdG 미설치");
        if (!isEnabled()) return UiText.t(context, "AdG 연동 꺼짐");
        if (listener() == null) return UiText.t(context, "AdG 연결 대기");
        return adGuardWorking ? UiText.t(context, "AdG 통합") : UiText.t(context, "AdG 대기 · 단독 VPN");
    }

    public static void notifyStateChanged() {
        if (context == null) return;
        EVENTS.execute(() -> signalPeer((byte) 1));
    }

    /** Called only after MegazordService has checked the caller UID. */
    public static boolean register(String caller, IBinder binder) {
        if (binder == null) return false;
        if (VPN.equals(caller)) return true; // This process supplies its own bundle directly.
        if (!ADGUARD.equals(caller)) return false;
        final IBinder.DeathRecipient death = () -> peerGone(binder);
        synchronized (IntegrationManager.class) {
            if (binder.equals(adGuardListener)) return true;
            clearListener();
            try { binder.linkToDeath(death, 0); }
            catch (RemoteException gone) { return false; }
            adGuardListener = binder;
            listenerDeath = death;
        }
        android.util.Log.i("WireGuardIntegration", "AdG listener registered");
        EVENTS.execute(() -> {
            synchronize((byte) 0);
            synchronize((byte) 1);
        });
        return true;
    }

    public static boolean unregister(String caller, IBinder binder) {
        if (VPN.equals(caller)) return true;
        synchronized (IntegrationManager.class) {
            if (!ADGUARD.equals(caller) || binder == null || !binder.equals(adGuardListener)) return false;
            clearListener();
        }
        updateWorking(false);
        return true;
    }

    public static Bundle peerBundle(String caller, byte query, boolean synchronizedQuery) {
        if (!known(query)) return new Bundle();
        if (ADGUARD.equals(caller) && !synchronizedQuery) return localBundle(query);
        Bundle remote = synchronizedQuery ? synchronize(query) : requestPeer(query);
        return VPN.equals(caller) ? remote : localBundle(query);
    }

    public static void peerDataChanged(String caller, byte query) {
        if (!known(query)) return;
        if (VPN.equals(caller)) {
            EVENTS.execute(() -> signalPeer(query));
        } else if (ADGUARD.equals(caller)) {
            EVENTS.execute(() -> synchronize(query));
        }
    }

    private static boolean known(byte query) { return query == 0 || query == 1; }

    private static synchronized IBinder listener() { return adGuardListener; }

    private static boolean installed() {
        if (context == null) return false;
        try {
            return context.getPackageManager().getApplicationInfo(ADGUARD, 128).metaData
                    .getInt("com.adguard.integration.version", -1) == 3;
        } catch (Exception missing) { return false; }
    }

    private static void peerGone(IBinder expected) {
        synchronized (IntegrationManager.class) {
            if (!expected.equals(adGuardListener)) return;
            clearListener();
        }
        updateWorking(false);
    }

    private static void clearListener() {
        if (adGuardListener != null && listenerDeath != null) {
            try { adGuardListener.unlinkToDeath(listenerDeath, 0); } catch (Exception ignored) {}
        }
        adGuardListener = null;
        listenerDeath = null;
    }

    private static void updateWorking(boolean working) {
        boolean changed = adGuardWorking != working;
        adGuardWorking = working;
        if (changed) TunnelController.onIntegrationChanged();
    }

    private static Bundle localBundle(byte query) {
        Bundle result = new Bundle();
        result.putString("package_name", VPN);
        result.putBoolean("integration_enabled", isEnabled());
        result.putLong("integration_sync_last_time", preferences == null ? 0 : preferences.getLong("changed", 0));
        if (query == 1) {
            result.putBoolean("is_core_functionality_working", TunnelController.isConnected());
            result.putInt("proxy_port", TunnelController.proxyPort());
            result.putString("proxy_username", TunnelController.proxyUsername());
            result.putString("proxy_password", TunnelController.proxyPassword());
            result.putInt("work_state", workState(isEnabled(), adGuardWorking, TunnelController.isConnected()));
        }
        return result;
    }

    static int workState(boolean integrated, boolean adGuard, boolean vpn) {
        if (!integrated) return 0;
        if (adGuard && vpn) return 4;
        if (adGuard) return 2;
        return vpn ? 3 : 1;
    }

    static boolean mergeEnabled(boolean local, long localTime, boolean remote, long remoteTime) {
        return remoteTime > localTime ? remote : localTime > remoteTime ? local : local || remote;
    }

    /** No manager monitor is held across a remote callback: AdGuard may synchronously call back. */
    private static Bundle synchronize(byte query) {
        Bundle remote = requestPeer(query);
        if (remote.isEmpty()) return remote;
        Bundle local = localBundle(query);
        if (remote.containsKey("integration_sync_last_time")) {
            long remoteTime = remote.getLong("integration_sync_last_time", 0);
            long localTime = local.getLong("integration_sync_last_time", 0);
            boolean enabled = mergeEnabled(local.getBoolean("integration_enabled", true), localTime,
                    remote.getBoolean("integration_enabled", true), remoteTime);
            long stamp = Math.max(remoteTime, localTime);
            if (stamp == 0) stamp = System.currentTimeMillis();
            boolean changed = isEnabled() != enabled;
            preferences.edit().putBoolean("enabled", enabled).putLong("changed", stamp).apply();
            local.putBoolean("integration_enabled", enabled);
            local.putLong("integration_sync_last_time", stamp);
            remote.putBoolean("integration_enabled", enabled);
            remote.putLong("integration_sync_last_time", stamp);
            if (changed) TunnelController.onIntegrationChanged();
        }
        if (query == 1 && remote.containsKey("is_core_functionality_working")) {
            boolean working = remote.getBoolean("is_core_functionality_working", false);
            int state = workState(isEnabled(), working, TunnelController.isConnected());
            local.putInt("work_state", state);
            remote.putInt("work_state", state);
            boolean first = !peerStateReceived, unchanged = adGuardWorking == working;
            peerStateReceived = true;
            if (first) android.util.Log.i("WireGuardIntegration", "Initial AdG state received; working=" + working);
            synchronized (READY) { READY.notifyAll(); }
            updateWorking(working);
            if (first && unchanged) TunnelController.onIntegrationChanged();
        }
        sendPeerBundle(query, local);
        return remote;
    }

    private static Bundle requestPeer(byte query) {
        IBinder peer = listener();
        if (peer == null) return new Bundle();
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(LISTENER);
            request.writeByte(query);
            if (!peer.transact(1, request, reply, 0)) return new Bundle();
            reply.readException();
            Bundle result = reply.readInt() != 0 ? Bundle.CREATOR.createFromParcel(reply) : new Bundle();
            result.setClassLoader(IntegrationManager.class.getClassLoader());
            return result;
        } catch (Exception failed) {
            peerGone(peer);
            return new Bundle();
        } finally { request.recycle(); reply.recycle(); }
    }

    private static void signalPeer(byte query) {
        IBinder peer = listener();
        if (peer == null) return;
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(LISTENER);
            request.writeByte(query);
            peer.transact(2, request, reply, 0);
            reply.readException();
        } catch (Exception failed) { peerGone(peer); }
        finally { request.recycle(); reply.recycle(); }
    }

    private static void sendPeerBundle(byte query, Bundle local) {
        IBinder peer = listener();
        if (peer == null) return;
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(LISTENER);
            request.writeByte(query);
            request.writeInt(1);
            local.writeToParcel(request, 0);
            peer.transact(3, request, reply, 0);
            reply.readException();
            if (reply.readInt() != 0) local.readFromParcel(reply);
        } catch (Exception failed) { peerGone(peer); }
        finally { request.recycle(); reply.recycle(); }
    }
}
