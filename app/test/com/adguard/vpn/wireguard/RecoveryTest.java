// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0
package com.adguard.vpn.wireguard;

import com.wireguard.config.Config;
import java.io.BufferedReader;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class RecoveryTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Recovery check " + checks); }
    public static void main(String[] args) throws Exception {
        check(RecoveryPolicy.retryDelay(0) == 1000);
        check(RecoveryPolicy.retryDelay(1) == 2000);
        check(RecoveryPolicy.retryDelay(6) == 60000);
        check(RecoveryPolicy.retryDelay(Integer.MAX_VALUE) == 60000);
        check(RecoveryPolicy.retryDelay(-1) == 1000);
        check(RecoveryPolicy.retrySession(0) && RecoveryPolicy.retrySession(5));
        check(!RecoveryPolicy.retrySession(6) && !RecoveryPolicy.retrySession(Integer.MAX_VALUE));
        check(RecoveryPolicy.waitForPeer(true, true, false));
        check(!RecoveryPolicy.waitForPeer(false, true, false));
        check(!RecoveryPolicy.waitForPeer(true, false, false));
        check(!RecoveryPolicy.waitForPeer(true, true, true));
        check(!RecoveryPolicy.keepAfterRevoke(false,true,true,true));
        check(!RecoveryPolicy.keepAfterRevoke(true,false,true,true));
        check(!RecoveryPolicy.keepAfterRevoke(true,true,true,false));
        check(RecoveryPolicy.keepAfterRevoke(true,true,true,true));
        check(RecoveryPolicy.keepAfterRevoke(true,true,false,false));
        check(RecoveryPolicy.revokeTimedOut(true,false));
        check(!RecoveryPolicy.revokeTimedOut(true,true));
        check(!RecoveryPolicy.revokeTimedOut(false,false));
        PeerState peer = new PeerState();
        Object oldPeer = new Object(), nextPeer = new Object();
        check(!peer.ready() && !peer.working());
        peer.attach(oldPeer);
        check(!peer.ready());
        check(peer.update(oldPeer,true) && peer.ready() && peer.working());
        check(peer.detach(oldPeer) && !peer.ready() && !peer.working());
        check(!peer.update(oldPeer,true));
        peer.attach(nextPeer);
        check(!peer.detach(oldPeer));
        check(!peer.update(oldPeer,false) && !peer.ready());
        check(peer.update(nextPeer,false) && peer.ready() && !peer.working());
        peer.attach(oldPeer);
        check(!peer.ready() && !peer.working());
        String key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
        Config config = Config.parse(new BufferedReader(new StringReader("[Interface]\nPrivateKey = " + key
                + "\nAddress = 10.99.0.2/32\n[Peer]\nPublicKey = " + key
                + "\nPresharedKey = " + key + "\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 15\nEndpoint = example.invalid:51820\n")));
        try { EndpointResolver.resolved(config, host -> { throw new UnknownHostException(); }); throw new AssertionError("DNS failure silently accepted"); }
        catch (UnknownHostException expected) { check(true); }
        Config first = EndpointResolver.resolved(config, host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{(byte)192,0,2,1})});
        Config second = EndpointResolver.resolved(config, host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{(byte)192,0,2,2})});
        check(first.toWgUserspaceString().contains("endpoint=192.0.2.1:51820"));
        check(second.toWgUserspaceString().contains("endpoint=192.0.2.2:51820"));
        check(config.getPeers().get(0).getEndpoint().get().getHost().equals("example.invalid"));
        check(first.getPeers().get(0).getPreSharedKey().equals(config.getPeers().get(0).getPreSharedKey()));
        check(first.getPeers().get(0).getPersistentKeepalive().get() == 15);
        check(first.getPeers().get(0).getAllowedIps().equals(config.getPeers().get(0).getAllowedIps()));
        check(first.getInterface() == config.getInterface());
        System.out.println("Network recovery, peer lifetime and endpoint checks passed: " + checks);
    }
}
