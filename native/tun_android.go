// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package main

import (
	"golang.org/x/sys/unix"
	"golang.zx2c4.com/wireguard/tun"
	"os"
	"sync"
)

// Android's VpnService already configured addresses, routes and MTU. A plain
// packet fd adapter avoids privileged interface ioctls and owns only the dup.
type androidTUN struct {
	file   *os.File
	mtu    int
	events chan tun.Event
	once   sync.Once
}

func releaseFD(fd int) { unix.Close(fd) }
func ownedTUN(fd, mtu int) (tun.Device, error) {
	if err := unix.SetNonblock(fd, true); err != nil {
		unix.Close(fd)
		return nil, err
	}
	f := os.NewFile(uintptr(fd), "wireguard-tun")
	t := &androidTUN{file: f, mtu: mtu, events: make(chan tun.Event, 1)}
	t.events <- tun.EventUp
	return t, nil
}
func (t *androidTUN) File() *os.File           { return t.file }
func (t *androidTUN) Name() (string, error)    { return "wireguard", nil }
func (t *androidTUN) MTU() (int, error)        { return t.mtu, nil }
func (t *androidTUN) Events() <-chan tun.Event { return t.events }
func (t *androidTUN) BatchSize() int           { return 1 }
func (t *androidTUN) Read(bufs [][]byte, sizes []int, offset int) (int, error) {
	n, err := t.file.Read(bufs[0][offset:])
	if err != nil {
		return 0, err
	}
	sizes[0] = n
	return 1, nil
}
func (t *androidTUN) Write(bufs [][]byte, offset int) (int, error) {
	for i, b := range bufs {
		if _, err := t.file.Write(b[offset:]); err != nil {
			return i, err
		}
	}
	return len(bufs), nil
}
func (t *androidTUN) Close() (err error) {
	t.once.Do(func() { err = t.file.Close(); close(t.events) })
	return err
}
