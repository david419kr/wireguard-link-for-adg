# Testing and current limits

The project includes host checks for configuration import/export, edit validation and raw-text preservation, integration-state rules, connected-tunnel identity, shortcut identifiers, and Korean/English fallback. Native tests exercise local encrypted WireGuard traffic, authenticated SOCKS TCP/UDP, split routing, and malformed input handling using synthetic configurations.

Real-device use has been checked on a Samsung SM-F968N with AdGuard 4.15.0. Integration startup, the toggle shortcut used by Modes and Routines, notification controls/icons, language fallback and the app UI have received device checks or user confirmation during development. This does not certify every OS/device combination or every latest packaging rebuild.

Version 1.0.9 was installed as a data-preserving update on an OPPO CPH2671 running Android 16 / ColorOS build CPH2671_16.0.10.600(EX01). The installed APK bytes matched the local signed APK. The disconnected notification's gray shield was checked on screen and confirmed by the tester. This is notification compatibility evidence, not a VPN throughput or long-duration test.

Version 1.0.10 keeps that ColorOS fallback and requests the stateful small icon in both connection states for One UI. Its build and host checks passed; it was installed on the same OPPO and the installed bytes were verified. The One UI connected-state presentation of this adjustment has not yet been checked on a Samsung device.

## Limits

- The connection indicator represents a running local tunnel engine. It is not a guarantee of a successful peer handshake or a working remote service.
- Only one selected tunnel runs at a time.
- Android allows a single VPN-interface owner; integration and standalone mode coordinate that ownership and may require OS VPN consent.
- The SOCKS path supports TCP CONNECT and UDP ASSOCIATE; fragmented SOCKS UDP is not supported.
- Split routing uses AllowedIPs. Domain routing uses the first DNS result; multi-address/Happy Eyeballs fallback is not implemented.
- Configured DNS servers must be reachable through the relevant tunnel routes. Network and filtering behavior also depends on the AdGuard configuration.
- Camera QR, all theme/permission variants, foreground-service behavior across vendors and long-running network transitions are not exhaustively tested.
- ColorOS 16 replaces third-party notification small icons with the application's fixed icon. A neutral gray shield is used for this fallback; the launcher activity retains its green adaptive icon. On systems that force this replacement, the notification shield stays neutral even while connected. Other systems can display the normal state-dependent small icon.
- The first release is marked as a prerelease while broader device testing continues.
