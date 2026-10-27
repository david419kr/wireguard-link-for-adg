// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package main

import (
	"context"
	"crypto/subtle"
	"encoding/binary"
	"errors"
	"io"
	"net"
	"net/netip"
	"strconv"
	"sync"
	"time"
)

type proxyDial func(context.Context, string, string, uint16) (net.Conn, error)
type socksServer struct {
	listener       net.Listener
	user, password string
	dial           proxyDial
	mu             sync.Mutex
	clients        map[net.Conn]bool
	closed         bool
}

func listenSocks(address, user, password string, dial proxyDial) (*socksServer, error) {
	l, err := net.Listen("tcp", address)
	if err != nil {
		return nil, err
	}
	s := &socksServer{listener: l, user: user, password: password, dial: dial, clients: make(map[net.Conn]bool)}
	go s.serve()
	return s, nil
}
func (s *socksServer) close() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.closed = true
	s.listener.Close()
	for c := range s.clients {
		c.Close()
	}
}
func (s *socksServer) serve() {
	for {
		c, err := s.listener.Accept()
		if err != nil {
			return
		}
		s.mu.Lock()
		if s.closed || len(s.clients) >= 256 {
			s.mu.Unlock()
			c.Close()
			continue
		}
		s.clients[c] = true
		s.mu.Unlock()
		go func() { defer func() { c.Close(); s.mu.Lock(); delete(s.clients, c); s.mu.Unlock() }(); s.handle(c) }()
	}
}

func readBytes(r io.Reader, n int) ([]byte, error) {
	b := make([]byte, n)
	_, err := io.ReadFull(r, b)
	return b, err
}
func readAddress(r io.Reader, kind byte) (string, uint16, error) {
	var size int
	switch kind {
	case 1:
		size = 4
	case 4:
		size = 16
	case 3:
		b, err := readBytes(r, 1)
		if err != nil {
			return "", 0, err
		}
		size = int(b[0])
		if size == 0 {
			return "", 0, errors.New("empty name")
		}
	default:
		return "", 0, errors.New("address type")
	}
	b, err := readBytes(r, size)
	if err != nil {
		return "", 0, err
	}
	var host string
	if kind == 3 {
		host = string(b)
	} else {
		addr, ok := netip.AddrFromSlice(b)
		if !ok {
			return "", 0, errors.New("address")
		}
		host = addr.Unmap().String()
	}
	p, err := readBytes(r, 2)
	if err != nil {
		return "", 0, err
	}
	return host, binary.BigEndian.Uint16(p), nil
}
func addressBytes(addr net.Addr) []byte {
	host, p, err := net.SplitHostPort(addr.String())
	if err != nil {
		return []byte{1, 0, 0, 0, 0, 0, 0}
	}
	ip, err := netip.ParseAddr(host)
	if err != nil {
		return []byte{1, 0, 0, 0, 0, 0, 0}
	}
	ip = ip.Unmap()
	var b []byte
	if ip.Is4() {
		b = []byte{1}
	} else {
		b = []byte{4}
	}
	b = append(b, ip.AsSlice()...)
	port, _ := strconv.Atoi(p)
	return append(b, byte(port>>8), byte(port))
}
func reply(c net.Conn, code byte, addr net.Addr) {
	b := []byte{5, code, 0}
	if addr == nil {
		b = append(b, 1, 0, 0, 0, 0, 0, 0)
	} else {
		b = append(b, addressBytes(addr)...)
	}
	c.Write(b)
}

func (s *socksServer) handle(c net.Conn) {
	c.SetDeadline(time.Now().Add(10 * time.Second))
	head, err := readBytes(c, 2)
	if err != nil || head[0] != 5 || head[1] == 0 {
		return
	}
	methods, err := readBytes(c, int(head[1]))
	if err != nil {
		return
	}
	auth := false
	for _, m := range methods {
		if m == 2 {
			auth = true
		}
	}
	if !auth {
		c.Write([]byte{5, 255})
		return
	}
	if _, err = c.Write([]byte{5, 2}); err != nil {
		return
	}
	a, err := readBytes(c, 2)
	if err != nil || a[0] != 1 || a[1] == 0 {
		return
	}
	user, err := readBytes(c, int(a[1]))
	if err != nil {
		return
	}
	p, err := readBytes(c, 1)
	if err != nil || p[0] == 0 {
		return
	}
	password, err := readBytes(c, int(p[0]))
	if err != nil {
		return
	}
	validUser := subtle.ConstantTimeCompare(user, []byte(s.user))
	validPass := subtle.ConstantTimeCompare(password, []byte(s.password))
	if validUser&validPass != 1 {
		c.Write([]byte{1, 1})
		return
	}
	if _, err = c.Write([]byte{1, 0}); err != nil {
		return
	}
	h, err := readBytes(c, 4)
	if err != nil || h[0] != 5 || h[2] != 0 {
		return
	}
	host, port, err := readAddress(c, h[3])
	if err != nil {
		reply(c, 8, nil)
		return
	}
	c.SetDeadline(time.Time{})
	switch h[1] {
	case 1:
		if port == 0 {
			reply(c, 1, nil)
			return
		}
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		remote, err := s.dial(ctx, "tcp", host, port)
		if err != nil {
			reply(c, 5, nil)
			return
		}
		defer remote.Close()
		reply(c, 0, remote.LocalAddr())
		done := make(chan struct{})
		go func() { io.Copy(idleConn{remote}, idleConn{c}); remote.Close(); c.Close(); close(done) }()
		io.Copy(idleConn{c}, idleConn{remote})
		remote.Close()
		c.Close()
		<-done
	case 3:
		s.udpAssociate(c)
	default:
		reply(c, 7, nil)
	}
}

type idleConn struct{ net.Conn }

func (c idleConn) Read(b []byte) (int, error) {
	c.SetReadDeadline(time.Now().Add(5 * time.Minute))
	return c.Conn.Read(b)
}
func (c idleConn) Write(b []byte) (int, error) {
	c.SetWriteDeadline(time.Now().Add(30 * time.Second))
	return c.Conn.Write(b)
}

func (s *socksServer) udpAssociate(control net.Conn) {
	localHost, _, err := net.SplitHostPort(control.LocalAddr().String())
	if err != nil {
		return
	}
	ip := net.ParseIP(localHost)
	relay, err := net.ListenUDP("udp", &net.UDPAddr{IP: ip})
	if err != nil {
		reply(control, 1, nil)
		return
	}
	defer relay.Close()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() { io.Copy(io.Discard, control); cancel(); relay.Close() }()
	reply(control, 0, relay.LocalAddr())
	peerHost, _, _ := net.SplitHostPort(control.RemoteAddr().String())
	peer := net.ParseIP(peerHost)
	var client *net.UDPAddr
	var mu sync.Mutex
	flows := make(map[string]net.Conn)
	defer func() {
		mu.Lock()
		for _, f := range flows {
			f.Close()
		}
		mu.Unlock()
	}()
	buffer := make([]byte, 65535)
	for {
		relay.SetReadDeadline(time.Now().Add(2 * time.Minute))
		n, source, err := relay.ReadFromUDP(buffer)
		if err != nil {
			return
		}
		if ctx.Err() != nil {
			return
		}
		if !source.IP.Equal(peer) || (client != nil && (source.Port != client.Port || !source.IP.Equal(client.IP))) {
			continue
		}
		if n < 4 || buffer[0] != 0 || buffer[1] != 0 || buffer[2] != 0 {
			continue
		}
		r := newSliceReader(buffer[4:n])
		host, port, err := readAddress(r, buffer[3])
		if err != nil || port == 0 {
			continue
		}
		if client == nil {
			client = &net.UDPAddr{IP: append(net.IP(nil), source.IP...), Port: source.Port}
		}
		key := net.JoinHostPort(host, strconv.Itoa(int(port)))
		mu.Lock()
		flow := flows[key]
		count := len(flows)
		mu.Unlock()
		if flow == nil {
			if count >= 128 {
				continue
			}
			dialCtx, stop := context.WithTimeout(ctx, 10*time.Second)
			flow, err = s.dial(dialCtx, "udp", host, port)
			stop()
			if err != nil {
				continue
			}
			mu.Lock()
			flows[key] = flow
			mu.Unlock()
			go func(f net.Conn, key string, target *net.UDPAddr) {
				defer func() {
					f.Close()
					mu.Lock()
					if flows[key] == f {
						delete(flows, key)
					}
					mu.Unlock()
				}()
				data := make([]byte, 65535)
				for {
					f.SetReadDeadline(time.Now().Add(2 * time.Minute))
					n, err := f.Read(data)
					if err != nil {
						return
					}
					packet := append([]byte{0, 0, 0}, addressBytes(f.RemoteAddr())...)
					packet = append(packet, data[:n]...)
					relay.SetWriteDeadline(time.Now().Add(10 * time.Second))
					if _, err = relay.WriteToUDP(packet, target); err != nil {
						return
					}
				}
			}(flow, key, client)
		}
		flow.SetWriteDeadline(time.Now().Add(10 * time.Second))
		if _, err = flow.Write(r.remaining()); err != nil {
			flow.Close()
			mu.Lock()
			if flows[key] == flow {
				delete(flows, key)
			}
			mu.Unlock()
		}
	}
}

type sliceReader struct {
	b   []byte
	pos int
}

func newSliceReader(b []byte) *sliceReader { return &sliceReader{b: b} }
func (r *sliceReader) Read(p []byte) (int, error) {
	if r.pos == len(r.b) {
		return 0, io.EOF
	}
	n := copy(p, r.b[r.pos:])
	r.pos += n
	return n, nil
}
func (r *sliceReader) remaining() []byte { return r.b[r.pos:] }
