// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package main

import (
	"bytes"
	"encoding/hex"
	"encoding/json"
	"golang.org/x/crypto/curve25519"
	"io"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"testing"
	"time"
)

// Two local peers prove that SOCKS traffic crosses real WireGuard encryption,
// without using the user's profile, a physical VPN, or any external server.
func TestEncryptedWireGuardTCPAndUDP(t *testing.T) {
	keys := [2][]byte{bytes.Repeat([]byte{0x31}, 32), bytes.Repeat([]byte{0x72}, 32)}
	var ids [2]uint64
	var es [2]*engine
	var public [2]string
	var ports [2]string
	for i := range ids {
		pk, err := curve25519.X25519(keys[i], curve25519.Basepoint)
		if err != nil {
			t.Fatal("public key generation failed")
		}
		public[i] = hex.EncodeToString(pk)
		ids[i] = startEngine("private_key="+hex.EncodeToString(keys[i])+"\nlisten_port=0\n", "192.0.2."+strconv.Itoa(i+1)+"/32", "", 1280, -1)
		if ids[i] == 0 {
			t.Fatal("local peer failed to start")
		}
		defer stopEngine(ids[i])
		es[i] = getEngine(ids[i])
		raw, err := es[i].dev.IpcGet()
		if err != nil {
			t.Fatal("local peer status failed")
		}
		for _, line := range strings.Split(raw, "\n") {
			if value, ok := strings.CutPrefix(line, "listen_port="); ok {
				ports[i] = value
			}
		}
		if ports[i] == "" || ports[i] == "0" {
			t.Fatal("local peer has no UDP socket")
		}
	}
	for i, e := range es {
		other := 1 - i
		uapi := "public_key=" + public[other] + "\nendpoint=127.0.0.1:" + ports[other] + "\nallowed_ip=192.0.2." + strconv.Itoa(other+1) + "/32\n"
		if e.dev.IpcSet(uapi) != nil {
			t.Fatal("local peer configuration failed")
		}
	}
	listener, err := es[1].stack.ListenTCPAddrPort(netip.MustParseAddrPort("192.0.2.2:8080"))
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	go func() {
		c, err := listener.Accept()
		if err != nil {
			return
		}
		defer c.Close()
		io.Copy(c, c)
	}()
	udpEcho, err := es[1].stack.ListenUDPAddrPort(netip.MustParseAddrPort("192.0.2.2:5353"))
	if err != nil {
		t.Fatal(err)
	}
	defer udpEcho.Close()
	go func() {
		b := make([]byte, 4096)
		for {
			n, addr, err := udpEcho.ReadFrom(b)
			if err != nil {
				return
			}
			udpEcho.WriteTo(b[:n], addr)
		}
	}()
	reserve, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := reserve.Addr().(*net.TCPAddr).Port
	reserve.Close()
	if message := startProxy(ids[0], "127.0.0.1", port, "test", "secret", "192.0.2.2/32"); message != "" {
		t.Fatal(message)
	}
	s := es[0].socks
	c := authenticate(t, s, "secret")
	expect(t, c, []byte{1, 0})
	request(t, c, 1, []byte{1, 192, 0, 2, 2, 31, 144})
	readReply(t, c)
	c.Write([]byte("encrypted TCP payload"))
	expect(t, c, []byte("encrypted TCP payload"))
	c.Close()
	control := authenticate(t, s, "secret")
	expect(t, control, []byte{1, 0})
	request(t, control, 3, []byte{1, 0, 0, 0, 0, 0, 0})
	host, proxyPort := readReply(t, control)
	relay, err := net.DialUDP("udp", nil, &net.UDPAddr{IP: net.ParseIP(host), Port: int(proxyPort)})
	if err != nil {
		t.Fatal(err)
	}
	defer relay.Close()
	relay.SetDeadline(time.Now().Add(5 * time.Second))
	frame := append([]byte{0, 0, 0, 1, 192, 0, 2, 2, 20, 233}, []byte("encrypted UDP payload")...)
	relay.Write(frame)
	b := make([]byte, 1024)
	n, err := relay.Read(b)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(frame, b[:n]) {
		t.Fatal("encrypted UDP echo mismatch")
	}
	// A destination outside AllowedIPs must still work directly.
	directEcho, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer directEcho.Close()
	go func() {
		d, err := directEcho.Accept()
		if err != nil {
			return
		}
		defer d.Close()
		io.Copy(d, d)
	}()
	c = authenticate(t, s, "secret")
	expect(t, c, []byte{1, 0})
	request(t, c, 1, addressBytes(directEcho.Addr()))
	readReply(t, c)
	c.Write([]byte("direct split route"))
	expect(t, c, []byte("direct split route"))
	var counters map[string]uint64
	if json.Unmarshal([]byte(stats(ids[0])), &counters) != nil || counters["tx_bytes"] == 0 || counters["rx_bytes"] == 0 || counters["last_handshake_time_sec"] == 0 {
		t.Fatal("WireGuard counters did not record encrypted traffic")
	}
}
