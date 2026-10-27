# Build from source

The current scripted build is supported on Windows x64. Android users install the release APK; these tools are only for source builds.

## Prerequisites

- JDK 21 (`java`, `javac`, `jar` on PATH)
- Python 3.11 or newer (standard library only)
- Go 1.26.3
- Android SDK platform 35
- Android SDK build-tools 36.1.0
- Android NDK 28.2.13676358

Set `ANDROID_HOME`/`ANDROID_SDK_ROOT`, or pass `-Sdk`. The NDK defaults to the pinned version under that SDK, or can be supplied with `-Ndk`.

```powershell
./build.ps1
```

The script verifies pinned Maven downloads, cross-compiles the backend for four ABIs, compiles this app's resources and Java sources, runs host checks, aligns the APK and verifies its payload. No original app APK, reverse-engineering output or personal tunnel archive is required.

An unsigned APK is written to `artifacts/`. To make an installable signed build, supply your signing files explicitly:

```powershell
./build.ps1 -Keystore C:\private\release.p12 `
    -PasswordFile C:\private\password.txt -KeyAlias release
```

Signing files are never project inputs committed to Git. Reuse an established release key for subsequent updates. CI only builds unsigned outputs and never needs the release key.

`-SkipNative` reuses locally generated `native/out` files. Use it only after a successful native build. `-SkipTests` is available for local iteration; release validation runs the tests.

## Translation maintenance

Edit `localization/messages.json`, then run:

```powershell
python tools/check_ui_translations.py --sync
```

The audit rejects missing translations and unwrapped UI literals. Internal state keys are translated only when displayed. User tunnel names and configuration text are never translated.

## Release contents

A release contains the signed app APK, SHA-256 checksums, build/dependency metadata, and a third-party notices archive. No original or modified AdGuard APK is produced by this project.

After committing the validated source, run `python tools/release_metadata.py artifacts/wireguard-link-for-adg-<version>.apk` to produce the notices archive, CycloneDX SBOM, source-linked build metadata and checksums. This requires the verified APK and native outputs from that build.
