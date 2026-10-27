// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.service.megazord;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import com.adguard.vpn.wireguard.IntegrationManager;

/** The stable component and interface names are interoperability identifiers, not imported app code. */
public final class MegazordService extends Service {
    private static final String INTERFACE = "com.adguard.kit.integration.Megazord";
    private final Binder endpoint = new Binder() {
        @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(INTERFACE); return true; }
            if (code < 1 || code > 5) return super.onTransact(code, request, reply, flags);
            request.enforceInterface(INTERFACE);
            String caller = authorizedCaller();
            if (code == 1 || code == 2) {
                IBinder listener = request.readStrongBinder();
                boolean accepted = code == 1 ? IntegrationManager.register(caller, listener)
                        : IntegrationManager.unregister(caller, listener);
                reply.writeNoException();
                reply.writeInt(accepted ? 1 : 0);
            } else if (code == 3 || code == 4) {
                Bundle result = IntegrationManager.peerBundle(caller, request.readByte(), code == 4);
                reply.writeNoException();
                reply.writeInt(1);
                result.writeToParcel(reply, 1);
            } else {
                IntegrationManager.peerDataChanged(caller, request.readByte());
                reply.writeNoException();
            }
            return true;
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        IntegrationManager.init(this);
        IntegrationManager.onServerCreated();
    }

    @Override public IBinder onBind(Intent intent) { return endpoint; }

    private String authorizedCaller() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return IntegrationManager.VPN;
        String[] packages = getPackageManager().getPackagesForUid(uid);
        if (packages != null) for (String name : packages)
            if (IntegrationManager.ADGUARD.equals(name)) return name;
        throw new SecurityException("Unrecognized integration caller");
    }
}
