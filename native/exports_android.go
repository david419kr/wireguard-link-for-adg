// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package main

/*
#include <stdlib.h>
*/
import "C"

//export wgStart
func wgStart(uapi, addresses, dns *C.char, mtu, fd C.int) C.ulonglong {
	return C.ulonglong(startEngine(C.GoString(uapi), C.GoString(addresses), C.GoString(dns), int(mtu), int(fd)))
}

//export wgStartSocks
func wgStartSocks(id C.ulonglong, host *C.char, port C.int, user, password, allowed *C.char) *C.char {
	message := startProxy(uint64(id), C.GoString(host), int(port), C.GoString(user), C.GoString(password), C.GoString(allowed))
	if message == "" {
		return nil
	}
	return C.CString(message)
}

//export wgStop
func wgStop(id C.ulonglong) { stopEngine(uint64(id)) }

//export wgStats
func wgStats(id C.ulonglong) *C.char { return C.CString(stats(uint64(id))) }

//export wgLastError
func wgLastError() *C.char { registry.Lock(); defer registry.Unlock(); return C.CString(registry.last) }

//export wgSocket
func wgSocket(id C.ulonglong, family C.int) C.int {
	e := getEngine(uint64(id))
	if e == nil {
		return -1
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.closed {
		return -1
	}
	return C.int(safeSocket(e, int(family)))
}
func safeSocket(e *engine, family int) (fd int) {
	fd = -1
	// StdNetBind's Android accessor panics when that address family is unavailable.
	defer func() {
		if recover() != nil {
			fd = -1
		}
	}()
	var err error
	if family == 4 {
		fd, err = e.bind.PeekLookAtSocketFd4()
	} else {
		fd, err = e.bind.PeekLookAtSocketFd6()
	}
	if err != nil {
		return -1
	}
	return fd
}

//export wgNetworkChanged
func wgNetworkChanged(id C.ulonglong) C.int {
	e := getEngine(uint64(id))
	if e == nil {
		return 0
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.closed || e.dev.BindUpdate() != nil {
		return 0
	}
	return 1
}
