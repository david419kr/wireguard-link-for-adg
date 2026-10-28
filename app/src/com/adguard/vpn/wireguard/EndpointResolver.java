// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0
package com.adguard.vpn.wireguard;

import com.wireguard.config.Config;
import com.wireguard.config.InetEndpoint;
import com.wireguard.config.Peer;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.UnknownHostException;

/** Resolve afresh on the worker, and never silently serialize a missing endpoint. */
final class EndpointResolver {
    interface Lookup { InetAddress[] resolve(String host) throws UnknownHostException; }
    static String userspace(Config config) throws Exception { return resolved(config, InetAddress::getAllByName).toWgUserspaceString(); }
    static Config resolved(Config config, Lookup lookup) throws Exception {
        Config.Builder result = new Config.Builder().setInterface(config.getInterface());
        for (Peer peer : config.getPeers()) {
            Peer.Builder copy = new Peer.Builder().setPublicKey(peer.getPublicKey()).addAllowedIps(peer.getAllowedIps());
            if (peer.getPreSharedKey().isPresent()) copy.setPreSharedKey(peer.getPreSharedKey().get());
            if (peer.getPersistentKeepalive().isPresent()) copy.setPersistentKeepalive(peer.getPersistentKeepalive().get());
            if (peer.getEndpoint().isPresent()) {
                InetEndpoint endpoint = peer.getEndpoint().get();
                InetAddress[] addresses = lookup.resolve(endpoint.getHost());
                if (addresses.length == 0) throw new UnknownHostException();
                InetAddress address = addresses[0];
                for (InetAddress candidate : addresses) if (candidate instanceof Inet4Address) { address = candidate; break; }
                String host = address.getHostAddress();
                copy.setEndpoint(InetEndpoint.parse((host.contains(":") ? "[" + host + "]" : host) + ":" + endpoint.getPort()));
            }
            result.addPeer(copy.build());
        }
        return result.build();
    }
    static String endpoints(Config config) throws Exception {
        StringBuilder result = new StringBuilder();
        for (Peer peer : resolved(config, InetAddress::getAllByName).getPeers()) {
            if (peer.getEndpoint().isPresent()) result.append("public_key=").append(peer.getPublicKey().toHex())
                    .append("\nendpoint=").append(peer.getEndpoint().get()).append('\n');
        }
        return result.toString();
    }
    private EndpointResolver() {}
}
