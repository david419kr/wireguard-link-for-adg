# Third-party notices

These licenses apply to the listed components, not to proprietary AdGuard software. No AdGuard VPN APK, artwork, resources or engine is distributed by this project.

| Component | Pinned version | License | Packaged use |
|---|---|---|---|
| WireGuard Android configuration/crypto classes | 1.0.20260102 | Apache-2.0 | Configuration parsing and Java crypto |
| ZXing core | 3.5.4 | Apache-2.0 | QR decoding |
| WireGuard Go | v0.0.0-20261006164505-2631ce99a06f | MIT | WireGuard backend |
| gVisor | v0.0.0-20250503011706-39ed1f5ac29c | Apache-2.0 | User-space network stack |
| google/btree | v1.1.2 | Apache-2.0 | Native transitive dependency |
| golang.org/x/crypto | v0.37.0 | BSD-3-Clause | Native transitive dependency |
| golang.org/x/net | v0.39.0 | BSD-3-Clause | Native transitive dependency |
| golang.org/x/sys | v0.32.0 | BSD-3-Clause | Native transitive dependency |
| golang.org/x/time | v0.7.0 | BSD-3-Clause | Native transitive dependency |
| Go runtime/standard library | 1.26.3 | BSD-3-Clause and included notices | Native runtime |

WireGuard Go: Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.

WireGuard Android code credits WireGuard LLC and contributors, including Southern Storm Software, Pty Ltd. for the Curve25519 implementation. ZXing credits the ZXing authors. gVisor credits the gVisor authors. Google Go and x packages credit The Go Authors; btree credits Google and contributors. Preserve the accompanying upstream notices and copyright statements.

Full license texts and applicable upstream NOTICE files are in [third-party/licenses](third-party/licenses). Java artifact URLs and SHA-256 checksums are locked in [java-dependencies.json](third-party/java-dependencies.json); Go versions and checksums are locked in `native/go.mod` and `native/go.sum`.

`golang.zx2c4.com/wintun` is a Windows-only upstream dependency retained in Go module metadata. It is not linked into the Android backend. Its MIT notice is retained for completeness.

## Sources

- https://git.zx2c4.com/wireguard-android/ (tag 1.0.20260102)
- https://github.com/zxing/zxing (tag zxing-3.5.4)
- https://git.zx2c4.com/wireguard-go/ (commit 2631ce99a06f)
- https://github.com/google/gvisor (commit 39ed1f5ac29c)
- https://github.com/google/btree (tag v1.1.2)
- https://go.dev/LICENSE and https://go.googlesource.com/

Android SDK, JDK, Python and Go build tools are development prerequisites and are not bundled in the APK. The complete WireGuard Android AAR's GoBackend and its native libraries are not packaged; the build selects only the necessary Java classes.
