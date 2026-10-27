// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package main

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/json"
	"io"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"testing"
	"time"
)

func expect(t *testing.T, c net.Conn, want []byte) {
	t.Helper()
	got := make([]byte, len(want))
	if _, err := io.ReadFull(c, got); err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(got, want) {
		t.Fatalf("protocol reply %v, want %v", got, want)
	}
}
func authenticate(t *testing.T, s *socksServer, password string) net.Conn {
	t.Helper()
	c, err := net.Dial("tcp", s.listener.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { c.Close() })
	c.SetDeadline(time.Now().Add(5 * time.Second))
	c.Write([]byte{5, 1, 2})
	expect(t, c, []byte{5, 2})
	c.Write(append([]byte{1, 4, 't', 'e', 's', 't', byte(len(password))}, []byte(password)...))
	return c
}
func request(t *testing.T, c net.Conn, command byte, address []byte) {
	t.Helper()
	c.Write(append([]byte{5, command, 0}, address...))
}
func readReply(t *testing.T, c net.Conn) (string, uint16) {
	t.Helper()
	h, err := readBytes(c, 4)
	if err != nil {
		t.Fatal(err)
	}
	if h[0] != 5 || h[1] != 0 || h[2] != 0 {
		t.Fatalf("bad reply %v", h)
	}
	host, port, err := readAddress(c, h[3])
	if err != nil {
		t.Fatal(err)
	}
	return host, port
}
func direct(ctx context.Context, network, host string, port uint16) (net.Conn, error) {
	return (&net.Dialer{}).DialContext(ctx, network, net.JoinHostPort(host, strconv.Itoa(int(port))))
}

func TestAuthenticationRequired(t *testing.T) {
	s, err := listenSocks("127.0.0.1:0", "test", "secret", direct)
	if err != nil {
		t.Fatal(err)
	}
	defer s.close()
	c := authenticate(t, s, "wrong")
	expect(t, c, []byte{1, 1})
	if _, err = c.Read(make([]byte, 1)); err == nil {
		t.Fatal("wrong password connection still open")
	}
	c, err = net.Dial("tcp", s.listener.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	c.SetDeadline(time.Now().Add(time.Second))
	c.Write([]byte{5, 1, 0})
	expect(t, c, []byte{5, 255})
}

func TestTCPConnectAddressFormsAndStop(t *testing.T) {
	echo, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer echo.Close()
	go func() {
		for {
			c, err := echo.Accept()
			if err != nil {
				return
			}
			go func() { defer c.Close(); io.Copy(c, c) }()
		}
	}()
	for _, kind := range []byte{1, 3, 4} {
		t.Run(strconv.Itoa(int(kind)), func(t *testing.T) {
			called := make(chan string, 1)
			s, err := listenSocks("127.0.0.1:0", "test", "secret", func(ctx context.Context, n, h string, p uint16) (net.Conn, error) {
				called <- h
				return (&net.Dialer{}).DialContext(ctx, n, echo.Addr().String())
			})
			if err != nil {
				t.Fatal(err)
			}
			defer s.close()
			c := authenticate(t, s, "secret")
			expect(t, c, []byte{1, 0})
			var addr []byte
			var want string
			switch kind {
			case 1:
				addr = []byte{1, 192, 0, 2, 1}
				want = "192.0.2.1"
			case 3:
				addr = append([]byte{3, 12}, []byte("example.test")...)
				want = "example.test"
			case 4:
				ip := netip.MustParseAddr("2001:db8::1")
				addr = append([]byte{4}, ip.AsSlice()...)
				want = ip.String()
			}
			addr = append(addr, 0, 80)
			request(t, c, 1, addr)
			readReply(t, c)
			if got := <-called; got != want {
				t.Fatalf("address parsed %s want %s", got, want)
			}
			c.Write([]byte("payload"))
			expect(t, c, []byte("payload"))
			s.close()
			if _, err = c.Read(make([]byte, 1)); err == nil {
				t.Fatal("stop did not close connection")
			}
		})
	}
}

func TestUDPAssociateAndFragmentRejection(t *testing.T) {
	echo, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	defer echo.Close()
	go func() {
		b := make([]byte, 4096)
		for {
			n, a, err := echo.ReadFromUDP(b)
			if err != nil {
				return
			}
			echo.WriteToUDP(b[:n], a)
		}
	}()
	s, err := listenSocks("127.0.0.1:0", "test", "secret", direct)
	if err != nil {
		t.Fatal(err)
	}
	defer s.close()
	c := authenticate(t, s, "secret")
	expect(t, c, []byte{1, 0})
	request(t, c, 3, []byte{1, 0, 0, 0, 0, 0, 0})
	host, port := readReply(t, c)
	udp, err := net.DialUDP("udp", nil, &net.UDPAddr{IP: net.ParseIP(host), Port: int(port)})
	if err != nil {
		t.Fatal(err)
	}
	defer udp.Close()
	frame := append([]byte{0, 0, 0}, addressBytes(echo.LocalAddr())...)
	frame = append(frame, []byte("datagram")...)
	udp.SetDeadline(time.Now().Add(2 * time.Second))
	udp.Write(frame)
	b := make([]byte, 1024)
	n, err := udp.Read(b)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(b[:n], frame) {
		t.Fatalf("UDP reply mismatch")
	}
	frame[2] = 1
	udp.SetReadDeadline(time.Now().Add(100 * time.Millisecond))
	udp.Write(frame)
	if _, err = udp.Read(b); err == nil {
		t.Fatal("fragment unexpectedly relayed")
	}
	c.Close()
	frame[2] = 0
	// TCP FIN and UDP datagrams can arrive in either order; require prompt cleanup.
	deadline := time.Now().Add(time.Second)
	for {
		udp.SetReadDeadline(time.Now().Add(100 * time.Millisecond))
		udp.Write(frame)
		if _, err = udp.Read(b); err != nil {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("UDP association survived control close")
		}
	}
}

func TestAddressParserRejectsTruncatedAndInvalid(t *testing.T) {
	for _, tc := range []struct {
		kind byte
		b    []byte
	}{{1, []byte{1, 2}}, {4, make([]byte, 15)}, {3, []byte{0}}, {3, []byte{4, 'a'}}, {9, nil}} {
		if _, _, err := readAddress(bytes.NewReader(tc.b), tc.kind); err == nil {
			t.Fatalf("accepted invalid address type %d", tc.kind)
		}
	}
	p := make([]byte, 2)
	binary.BigEndian.PutUint16(p, 443)
	host, port, err := readAddress(bytes.NewReader(append([]byte{127, 0, 0, 1}, p...)), 1)
	if err != nil || host != "127.0.0.1" || port != 443 {
		t.Fatal("valid address rejected")
	}
}

func TestEngineLifecycleAndStatisticsRedaction(t *testing.T) {
	id := startEngine("private_key="+strings.Repeat("1", 64)+"\n", "192.0.2.2/32", "", 1280, -1)
	if id == 0 {
		t.Fatal("synthetic netstack could not start")
	}
	defer stopEngine(id)
	raw := stats(id)
	var counters map[string]uint64
	if json.Unmarshal([]byte(raw), &counters) != nil {
		t.Fatal("statistics are not JSON")
	}
	if len(counters) != 4 || strings.Contains(raw, "private_key") || strings.Contains(raw, "11111111") {
		t.Fatal("statistics leaked sensitive fields")
	}
	if message := startProxy(id, "0.0.0.0", 1080, "test", "secret", "0.0.0.0/0"); message == "" {
		t.Fatal("non-loopback listener accepted")
	}
	stopEngine(id)
	if stats(id) != "" {
		t.Fatal("stopped handle still active")
	}
	stopEngine(id)
}
