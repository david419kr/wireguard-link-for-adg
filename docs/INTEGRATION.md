# Interoperability

This is an independent implementation of an observed local Binder protocol. Component and interface names are compatibility identifiers; no proprietary client or server classes are included.

- Android package: `com.adguard.vpn`
- Exported broker component: `com.adguard.vpn.service.megazord.MegazordService`
- Metadata: `com.adguard.integration.version=3`
- Server/listener descriptors: `com.adguard.kit.integration.Megazord` and `com.adguard.kit.integration.MegazordListener`
- Startup: bind the local broker, then send a package-scoped `synchronize` broadcast to the installed AdGuard ad blocker. Each wake-up burst is bounded; an enabled tunnel retries missing peer state with capped backoff.
- Queries exchange integration preference/timestamp, local protection state, authenticated proxy information and work state. Newer timestamps win; the enabled preference wins a timestamp tie.
- Sensitive Binder calls accept only the app's own UID or the UID belonging to `com.adguard.android`. No credentials are included in synchronization broadcasts or logs.

When AdGuard protection is active and integration is enabled, this app supplies an authenticated loopback SOCKS proxy backed by WireGuard and a user-space network stack. Otherwise it uses its own Android TUN interface. Unknown startup peer state is not treated as proof that AdGuard is off.

The Android shortcut keeps ID `TOGGLE_CONNECTION_ID`, action `com.adguard.android.ACTION_TOGGLE_CONNECTION`, and component `com.adguard.vpn.ui.ShortcutActivity`. This is a toggle, not separate absolute ON and OFF commands. The launcher alias preserves the associated component address. No Samsung private SDK is included.

Peer readiness and protection state belong to one Binder registration. Binder loss or replacement clears readiness, and replies from an old registration cannot restore it. Unknown peer state never selects standalone mode by itself. Transient tunnel/network failures retain the requested connection state and retry with a delay capped at 60 seconds; invalid configurations stop the request. During VPN revocation, an enabled integration with a working or not-yet-known peer can wait for handoff; if working AdGuard protection is not confirmed within 10 seconds, the request stops. A known inactive peer or disabled integration stops immediately rather than reclaiming VPN ownership automatically.

One UI notification cards may substitute the application icon for a notification's small icon. A per-notification `android.showSmallIcon` extra requests the stateful small shield in both connection states on compatible One UI versions. ColorOS 16 forces third-party notifications to use the application's fixed icon, so that icon is a neutral gray shield; the launcher activity has a separate green adaptive icon. On systems that force this replacement, the notification icon remains gray while connected too. No private vendor SDK or system setting changes are required.
