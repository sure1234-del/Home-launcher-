# Changelog

## Stability and live wallpaper

### T0 repository and baseline
- Kept the maintained Android project under `src/` and moved the legacy ZIP to `releases/`.
- Added the Gradle version catalog, ktlint/detekt plugins, JDK 17 CI setup, Dependabot and
  Gitleaks secret scanning.
- The baseline debug APK, unit tests and lint pass with JDK 21 and Android SDK 35.
- Added Android Keystore HMAC reset verification, lockout doubling, secure reset dialogs,
  cleartext-disabled network policy and package-visibility queries.
- Added the 280 MB PSS debug assertion and live-wallpaper hidden-frame release policy.

### M-S, T11 and T12 infrastructure
- Added 56 addable Mission widget registry entries and generic detail previews.
- Added 50 optional feature toggles to Settings; Mains answer timer is hidden when Show Mains is OFF.
- Added 15-minute WorkManager Zite sync, immediate sync after timer save, and backup checksum
  integrity with weekly local backup rotation.
- Added IST solar-altitude/twilight calculations, thermal-aware wallpaper FPS and per-app locale
  declarations for English and Hindi.

### Done
- Added a single Asia/Kolkata `TimeProvider` for half-hour scheduling and IST date/time helpers.
- Added `MissionWallpaperService` with a hardware-canvas engine, device-sized bitmap reuse,
  battery-saver static mode, page parallax offsets and the `:00/:30` refresh boundary.
- Added live-wallpaper launcher/detection in Settings and prevented static
  `WallpaperManager.setBitmap` calls while the Mission live wallpaper is active.
- Extended Open-Meteo requests and cache to cloud, visibility, wind, precipitation, is-day,
  sunrise and sunset fields with a three-hour cache fallback.
- Added WMO scene mapping, moon-phase math, crash diagnostics, memory/process-exit diagnostics,
  trim logging, safe-mode crash counting and R8 release minification.
- Added JUnit unit coverage for IST half-hour boundaries, year boundary context, WMO mapping and
  moon illumination.

### Partial
- Live moon/sun positions, fog/rain overlays, 300-600 stars and Wallpaper Lab need expanded
  visual implementation and Redmi device verification.
- Baseline Profile, Macrobenchmark, JankStats, WorkManager sync and a full app-gesture layer remain
  partial or not added.
- The room vs JSON migration decision remains JSON-first; existing collections already use
  atomic temp-file/backup writes.

### Impossible on this environment
- No Redmi Pad SE or HyperOS 2.0.207.0 device is available. Live-wallpaper chooser behavior,
  4 GB low-memory frame budget, multi-window lifetime and HyperOS navigation cannot be verified.
