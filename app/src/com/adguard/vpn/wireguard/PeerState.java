// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0
package com.adguard.vpn.wireguard;

/** State belongs to one live Binder registration; late replies cannot revive an old peer. */
final class PeerState {
    private Object peer;
    private boolean ready, working;
    synchronized void attach(Object value) { peer = value; ready = working = false; }
    synchronized boolean current(Object value) { return peer != null && peer.equals(value); }
    synchronized boolean detach(Object value) {
        if (!current(value)) return false;
        peer = null; ready = working = false;
        return true;
    }
    synchronized boolean update(Object value, boolean active) {
        if (!current(value)) return false;
        ready = true; working = active;
        return true;
    }
    synchronized boolean ready() { return ready; }
    synchronized boolean working() { return ready && working; }
}
