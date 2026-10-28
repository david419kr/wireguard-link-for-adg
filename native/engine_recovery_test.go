// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0
package main

import (
	"errors"
	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/tun/netstack"
	"net/netip"
	"strings"
	"sync/atomic"
	"testing"
)

type recoveryBind struct {
	conn.Bind
	fail          atomic.Bool
	opens, closes atomic.Int32
}

func (b *recoveryBind) Open(port uint16) ([]conn.ReceiveFunc, uint16, error) {
	b.opens.Add(1)
	if b.fail.Load() {
		return nil, 0, errors.New("synthetic socket failure")
	}
	return b.Bind.Open(port)
}
func (b *recoveryBind) Close() error { b.closes.Add(1); return b.Bind.Close() }

func TestEngineRebindFailureAndRecovery(t *testing.T) {
	tun, _, err := netstack.CreateNetTUN([]netip.Addr{netip.MustParseAddr("192.0.2.91")}, nil, 1280)
	if err != nil {
		t.Fatal(err)
	}
	bind := &recoveryBind{Bind: conn.NewStdNetBind()}
	dev := device.NewDevice(tun, bind, device.NewLogger(device.LogLevelSilent, ""))
	if err := dev.Up(); err != nil {
		dev.Close()
		t.Fatal(err)
	}
	registry.Lock()
	registry.next++
	id := registry.next
	registry.engines[id] = &engine{dev: dev}
	registry.Unlock()
	defer stopEngine(id)
	previous := bind.closes.Load()
	bind.fail.Store(true)
	if rebindEngine(id) {
		t.Fatal("failed socket reopen reported success")
	}
	if bind.closes.Load() <= previous {
		t.Fatal("failure did not exercise old-socket close")
	}
	bind.fail.Store(false)
	if !rebindEngine(id) {
		t.Fatal("socket reopen did not recover")
	}
	first, second := strings.Repeat("2", 64), strings.Repeat("3", 64)
	if err := dev.IpcSet("private_key=" + strings.Repeat("1", 64) + "\npublic_key=" + first + "\nendpoint=127.0.0.1:10001\npublic_key=" + second + "\nendpoint=127.0.0.1:10002\n"); err != nil {
		t.Fatal(err)
	}
	if !updateEngineEndpoints(id, "public_key="+first+"\nendpoint=127.0.0.1:10003\n") {
		t.Fatal("endpoint refresh rejected")
	}
	data, err := dev.IpcGet()
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(data, "endpoint=127.0.0.1:10003") || !strings.Contains(data, "endpoint=127.0.0.1:10002") {
		t.Fatal("endpoint refresh lost a peer")
	}
	stopEngine(id)
	if rebindEngine(id) || updateEngineEndpoints(id, "") {
		t.Fatal("stopped engine accepted update")
	}
}

func TestUDPRejectsDeclaredSourceIPMismatch(t *testing.T) {
	s, err := listenSocks("127.0.0.1:0", "test", "secret", direct)
	if err != nil {
		t.Fatal(err)
	}
	defer s.close()
	c := authenticate(t, s, "secret")
	expect(t, c, []byte{1, 0})
	request(t, c, 3, []byte{1, 127, 0, 0, 2, 1, 2})
	expect(t, c, []byte{5, 2, 0})
}
