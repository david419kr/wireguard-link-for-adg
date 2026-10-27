// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

//go:build !android

package main

import (
	"errors"
	"golang.zx2c4.com/wireguard/tun"
)

func ownedTUN(fd, mtu int) (tun.Device, error) {
	return nil, errors.New("Android TUN descriptor required")
}
func releaseFD(fd int) {}
