// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package main

import (
	"bytes"
	"context"
	"golang.zx2c4.com/wireguard/tun/netstack"
	"io"
	"net"
	"net/netip"
	"runtime"
	"strconv"
	"testing"
	"time"
)

func reviewProxy(t *testing.T) *socksServer {
	t.Helper()
	s, err := listenSocks("127.0.0.1:0", "local-test", "local-password", func(ctx context.Context, network, host string, port uint16) (net.Conn, error) {
		return (&net.Dialer{}).DialContext(ctx, network, net.JoinHostPort(host, strconv.Itoa(int(port))))
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(s.close)
	return s
}

func reviewControl(t *testing.T, s *socksServer, command byte, target net.Addr) (net.Conn, *net.UDPAddr) {
	t.Helper()
	c, err := net.Dial("tcp", s.listener.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	return reviewControlConn(t, c, s, command, target)
}
func reviewControlConn(t *testing.T, c net.Conn, s *socksServer, command byte, target net.Addr) (net.Conn, *net.UDPAddr) {
	t.Helper()
	t.Cleanup(func() { c.Close() })
	c.SetDeadline(time.Now().Add(3 * time.Second))
	expect := func(want []byte) {
		got := make([]byte, len(want))
		if _, err := io.ReadFull(c, got); err != nil {
			t.Fatal(err)
		}
		if !bytes.Equal(got, want) {
			t.Fatalf("unexpected protocol response: %v", got)
		}
	}
	c.Write([]byte{5, 1, 2})
	expect([]byte{5, 2})
	auth := append([]byte{1, byte(len(s.user))}, []byte(s.user)...)
	auth = append(auth, byte(len(s.password)))
	c.Write(append(auth, []byte(s.password)...))
	expect([]byte{1, 0})
	c.Write(append([]byte{5, command, 0}, addressBytes(target)...))
	head, err := readBytes(c, 4)
	if err != nil {
		t.Fatal(err)
	}
	if head[1] != 0 {
		t.Fatalf("SOCKS request failed: %v", head)
	}
	host, port, err := readAddress(c, head[3])
	if err != nil {
		t.Fatal(err)
	}
	return c, &net.UDPAddr{IP: net.ParseIP(host), Port: int(port)}
}

func reviewTCPServer(t *testing.T, run func(net.Conn)) net.Listener {
	t.Helper()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { l.Close() })
	go func() {
		c, err := l.Accept()
		if err == nil {
			defer c.Close()
			run(c)
		}
	}()
	return l
}

func TestSOCKSRecoveryNormalTCPEcho(t *testing.T) {
	target := reviewTCPServer(t, func(c net.Conn) { io.Copy(c, c) })
	c, _ := reviewControl(t, reviewProxy(t), 1, target.Addr())
	payload := []byte("ordinary request")
	c.Write(payload)
	got := make([]byte, len(payload))
	if _, err := io.ReadFull(c, got); err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(got, payload) {
		t.Fatal("TCP echo mismatch")
	}
}

func TestSOCKSRecoveryTCPHalfCloseMustPreserveResponse(t *testing.T) {
	response := []byte("response-after-client-FIN")
	if runtime.GOOS == "windows" {
		control := reviewTCPServer(t, func(c net.Conn) { io.ReadAll(c); c.Write(response) })
		c, err := net.Dial("tcp", control.Addr().String())
		if err != nil {
			t.Fatal(err)
		}
		c.SetDeadline(time.Now().Add(time.Second))
		c.Write([]byte("request"))
		c.(*net.TCPConn).CloseWrite()
		got, _ := io.ReadAll(c)
		c.Close()
		if !bytes.Equal(got, response) {
			t.Skip("Host direct TCP half-close loses responses; userspace test still required, Linux CI uses real TCP")
		}
	}
	target := reviewTCPServer(t, func(c net.Conn) { io.ReadAll(c); c.Write(response) })
	c, _ := reviewControl(t, reviewProxy(t), 1, target.Addr())
	c.Write([]byte("complete request"))
	if err := c.(*net.TCPConn).CloseWrite(); err != nil {
		t.Fatal(err)
	}
	got, err := io.ReadAll(c)
	if !bytes.Equal(got, response) {
		t.Fatalf("half-close lost response: received=%q error=%v expected=%q", got, err, response)
	}
}

func TestSOCKSRecoveryUserspaceHalfClose(t *testing.T) {
	for _, clientFirst := range []bool{true, false} {
		t.Run(strconv.FormatBool(clientFirst), func(t *testing.T) {
			ip := netip.MustParseAddr("192.0.2.90")
			tun, stack, err := netstack.CreateNetTUN([]netip.Addr{ip}, nil, 1280)
			if err != nil {
				t.Fatal(err)
			}
			defer tun.Close()
			target, err := stack.ListenTCPAddrPort(netip.AddrPortFrom(ip, 8080))
			if err != nil {
				t.Fatal(err)
			}
			defer target.Close()
			serverDone := make(chan string, 1)
			go func() {
				c, e := target.Accept()
				if e != nil {
					return
				}
				defer c.Close()
				if clientFirst {
					b, _ := io.ReadAll(c)
					c.Write([]byte("response"))
					serverDone <- string(b)
				} else {
					c.Write([]byte("greeting"))
					c.(interface{ CloseWrite() error }).CloseWrite()
					b, _ := io.ReadAll(c)
					serverDone <- string(b)
				}
			}()
			listener, err := stack.ListenTCPAddrPort(netip.AddrPortFrom(ip, 1080))
			if err != nil {
				t.Fatal(err)
			}
			s := &socksServer{listener: listener, user: "local-test", password: "local-password", clients: make(map[net.Conn]bool), dial: func(ctx context.Context, n, h string, p uint16) (net.Conn, error) {
				return stack.DialContext(ctx, n, net.JoinHostPort(h, strconv.Itoa(int(p))))
			}}
			go s.serve()
			defer s.close()
			c, err := stack.DialContext(context.Background(), "tcp", listener.Addr().String())
			if err != nil {
				t.Fatal(err)
			}
			reviewControlConn(t, c, s, 1, target.Addr())
			if clientFirst {
				c.Write([]byte("request"))
				c.(interface{ CloseWrite() error }).CloseWrite()
				b, e := io.ReadAll(c)
				if e != nil || string(b) != "response" {
					t.Fatalf("client half-close response=%q err=%v", b, e)
				}
			} else {
				b, e := io.ReadAll(c)
				if e != nil || string(b) != "greeting" {
					t.Fatalf("server half-close response=%q err=%v", b, e)
				}
				c.Write([]byte("upload"))
				c.(interface{ CloseWrite() error }).CloseWrite()
			}
			want := "request"
			if !clientFirst {
				want = "upload"
			}
			select {
			case got := <-serverDone:
				if got != want {
					t.Fatalf("server got %q, want %q", got, want)
				}
			case <-time.After(3 * time.Second):
				t.Fatal("half-close relay did not finish")
			}
		})
	}
}

func reviewUDP(t *testing.T) (*net.UDPConn, *socksServer) {
	t.Helper()
	echo, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { echo.Close() })
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
	return echo, reviewProxy(t)
}

func reviewUDPSender(t *testing.T) *net.UDPConn {
	t.Helper()
	c, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { c.Close() })
	return c
}

func TestSOCKSRecoveryNormalUDPEcho(t *testing.T) {
	echo, s := reviewUDP(t)
	sender := reviewUDPSender(t)
	_, relay := reviewControl(t, s, 3, sender.LocalAddr())
	frame := append(append([]byte{0, 0, 0}, addressBytes(echo.LocalAddr())...), []byte("ordinary UDP")...)
	sender.SetDeadline(time.Now().Add(time.Second))
	sender.WriteToUDP(frame, relay)
	got := make([]byte, 1024)
	n, _, err := sender.ReadFromUDP(got)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(got[:n], frame) {
		t.Fatal("UDP echo mismatch")
	}
}

func TestSOCKSRecoveryUDPExplicitSenderMustBeHonored(t *testing.T) {
	echo, s := reviewUDP(t)
	declared, other := reviewUDPSender(t), reviewUDPSender(t)
	_, relay := reviewControl(t, s, 3, declared.LocalAddr())
	frame := append(append([]byte{0, 0, 0}, addressBytes(echo.LocalAddr())...), []byte("loopback-race")...)
	other.SetDeadline(time.Now().Add(300 * time.Millisecond))
	other.WriteToUDP(frame, relay)
	got := make([]byte, 1024)
	n, _, err := other.ReadFromUDP(got)
	if err == nil {
		t.Errorf("undeclared local sender was forwarded and received %d bytes", n)
	}
	declared.SetDeadline(time.Now().Add(300 * time.Millisecond))
	declared.WriteToUDP(frame, relay)
	if _, _, err := declared.ReadFromUDP(got); err != nil {
		t.Errorf("declared sender blocked after competing packet: %v", err)
	}
}
