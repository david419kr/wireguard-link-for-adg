// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

final class NativeEngine {
    static { System.loadLibrary("wgbridge"); }
    static native long start(String uapi, String addressesCsv, String dnsCsv, int mtu, int tunFd);
    static native String startSocks(long handle, String host, int port, String username, String password, String allowedIpsCsv);
    static native void stop(long handle);
    static native String statistics(long handle);
    static native String lastError();
    static native int[] sockets(long handle);
    static native boolean networkChanged(long handle);
    private NativeEngine() {}
}
