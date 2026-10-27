// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package main

import (
	"context"
	"encoding/json"
	"errors"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"sync"

	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/tun"
	"golang.zx2c4.com/wireguard/tun/netstack"
)

type engine struct {
	mu     sync.Mutex
	dev    *device.Device
	bind   *conn.StdNetBind
	stack  *netstack.Net
	socks  *socksServer
	dns    bool
	closed bool
}

var registry = struct {
	sync.Mutex
	next    uint64
	engines map[uint64]*engine
	last    string
}{engines: make(map[uint64]*engine)}

func fail(message string) uint64 {
	registry.Lock()
	registry.last = message
	registry.Unlock()
	return 0
}

func addresses(csv string) ([]netip.Addr, error) {
	var result []netip.Addr
	for _, item := range strings.Split(csv, ",") {
		item = strings.TrimSpace(item)
		if item == "" {
			continue
		}
		var addr netip.Addr
		var err error
		if strings.Contains(item, "/") {
			var prefix netip.Prefix
			prefix, err = netip.ParsePrefix(item)
			addr = prefix.Addr()
		} else {
			addr, err = netip.ParseAddr(item)
		}
		if err != nil || !addr.IsValid() {
			return nil, errors.New("invalid address")
		}
		result = append(result, addr.Unmap())
	}
	return result, nil
}

func startEngine(uapi, local, dns string, mtu, fd int) uint64 {
	defer func() {
		if fd >= 0 {
			releaseFD(fd)
		}
	}()
	if mtu < 576 || mtu > 65535 {
		return fail("Invalid tunnel MTU")
	}
	ips, err := addresses(local)
	if err != nil || len(ips) == 0 {
		return fail("Invalid tunnel addresses")
	}
	servers, err := addresses(dns)
	if err != nil {
		return fail("Invalid DNS addresses")
	}
	var t tun.Device
	var network *netstack.Net
	if fd < 0 {
		t, network, err = netstack.CreateNetTUN(ips, servers, mtu)
	} else {
		t, err = ownedTUN(fd, mtu)
		fd = -1 // ownedTUN consumes the descriptor even when construction fails.
	}
	if err != nil {
		return fail("Cannot create tunnel interface")
	}
	bind := conn.NewStdNetBind().(*conn.StdNetBind)
	d := device.NewDevice(t, bind, device.NewLogger(device.LogLevelSilent, ""))
	if d.IpcSet(uapi) != nil {
		d.Close()
		return fail("WireGuard configuration rejected")
	}
	if d.Up() != nil {
		d.Close()
		return fail("Cannot activate WireGuard")
	}
	e := &engine{dev: d, bind: bind, stack: network, dns: len(servers) > 0}
	registry.Lock()
	registry.next++
	id := registry.next
	registry.engines[id] = e
	registry.last = ""
	registry.Unlock()
	return id
}

func getEngine(id uint64) *engine {
	registry.Lock()
	defer registry.Unlock()
	return registry.engines[id]
}
func stopEngine(id uint64) {
	registry.Lock()
	e := registry.engines[id]
	delete(registry.engines, id)
	registry.Unlock()
	if e == nil {
		return
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	e.closed = true
	if e.socks != nil {
		e.socks.close()
	}
	e.dev.Close()
}

func startProxy(id uint64, host string, port int, user, password, allowed string) string {
	e := getEngine(id)
	if e == nil {
		return "Tunnel is not running"
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.closed || e.stack == nil {
		return "SOCKS requires integrated tunnel mode"
	}
	if e.socks != nil {
		return "SOCKS is already running"
	}
	ip, err := netip.ParseAddr(host)
	if err != nil || !ip.IsLoopback() || port < 1 || port > 65535 || len(user) == 0 || len(user) > 255 || len(password) == 0 || len(password) > 255 {
		return "Invalid local proxy settings"
	}
	var prefixes []netip.Prefix
	for _, item := range strings.Split(allowed, ",") {
		if strings.TrimSpace(item) == "" {
			continue
		}
		p, err := netip.ParsePrefix(strings.TrimSpace(item))
		if err != nil {
			return "Invalid tunnel routes"
		}
		prefixes = append(prefixes, p.Masked())
	}
	if len(prefixes) == 0 {
		return "Tunnel has no routes"
	}
	s, err := listenSocks(net.JoinHostPort(host, strconv.Itoa(port)), user, password, func(ctx context.Context, network, host string, port uint16) (net.Conn, error) {
		ip, err := netip.ParseAddr(host)
		if err != nil {
			var ips []string
			if e.dns {
				ips, err = e.stack.LookupContextHost(ctx, host)
			} else {
				ips, err = net.DefaultResolver.LookupHost(ctx, host)
			}
			if err != nil || len(ips) == 0 {
				return nil, errors.New("DNS failed")
			}
			ip, err = netip.ParseAddr(ips[0])
			if err != nil {
				return nil, errors.New("DNS failed")
			}
		}
		ip = ip.Unmap()
		target := netip.AddrPortFrom(ip, port)
		for _, p := range prefixes {
			if p.Contains(ip) {
				return e.stack.DialContext(ctx, network, target.String())
			}
		}
		return (&net.Dialer{}).DialContext(ctx, network, target.String())
	})
	if err != nil {
		return "Cannot bind local SOCKS port"
	}
	e.socks = s
	return ""
}

func stats(id uint64) string {
	e := getEngine(id)
	if e == nil {
		return ""
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.closed {
		return ""
	}
	raw, err := e.dev.IpcGet()
	if err != nil {
		return ""
	}
	// IpcGet includes private keys and endpoints: return only non-identifying counters.
	result := map[string]uint64{"rx_bytes": 0, "tx_bytes": 0, "last_handshake_time_sec": 0, "last_handshake_time_nsec": 0}
	for _, line := range strings.Split(raw, "\n") {
		key, value, ok := strings.Cut(line, "=")
		if ok && (key == "rx_bytes" || key == "tx_bytes" || key == "last_handshake_time_sec" || key == "last_handshake_time_nsec") {
			n, err := strconv.ParseUint(value, 10, 64)
			if err != nil {
				continue
			}
			if key == "rx_bytes" || key == "tx_bytes" {
				result[key] += n
			} else if n > result[key] {
				result[key] = n
			}
		}
	}
	encoded, _ := json.Marshal(result)
	return string(encoded)
}

func main() {}
