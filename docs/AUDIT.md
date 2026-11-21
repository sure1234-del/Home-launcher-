# Source audit

## Scope and build baseline

The maintained Android project is in `src/`. `.github/workflows/build.yml` was inspected and
now selects this tree before the legacy ZIP. The workflow provisions Java 17 and builds the
debug APK with the Gradle wrapper.

Local Kotlin/Android compilation was attempted with
`gradle -p src :app:compileDebugKotlin --no-daemon`, but stopped before compilation because no
Android SDK is installed or configured in this environment. XML parsing of the manifest and
backup rules succeeds. Do not treat compilation or device behavior as verified until the
workflow/device checklist passes.

## Kotlin and manifest inventory

| File | Lines |
|---|---:|
| `app/src/main/AndroidManifest.xml` | 93 |
| `app/src/main/java/com/mission/launcher/Ai.kt` | 170 |
| `app/src/main/java/com/mission/launcher/Apps.kt` | 202 |
| `app/src/main/java/com/mission/launcher/Backup.kt` | 80 |
| `app/src/main/java/com/mission/launcher/Capture.kt` | 48 |
| `app/src/main/java/com/mission/launcher/Cards.kt` | 459 |
| `app/src/main/java/com/mission/launcher/ChatActivity.kt` | 109 |
| `app/src/main/java/com/mission/launcher/CircleService.kt` | 155 |
| `app/src/main/java/com/mission/launcher/Data.kt` | 325 |
| `app/src/main/java/com/mission/launcher/Dialogs.kt` | 54 |
| `app/src/main/java/com/mission/launcher/Extras.kt` | 88 |
| `app/src/main/java/com/mission/launcher/Focus.kt` | 33 |
| `app/src/main/java/com/mission/launcher/Icons.kt` | 103 |
| `app/src/main/java/com/mission/launcher/MainActivity.kt` | 353 |
| `app/src/main/java/com/mission/launcher/Migrate.kt` | 39 |
| `app/src/main/java/com/mission/launcher/MissionApplication.kt` | 44 |
| `app/src/main/java/com/mission/launcher/Notifs.kt` | 35 |
| `app/src/main/java/com/mission/launcher/Pager.kt` | 64 |
| `app/src/main/java/com/mission/launcher/Pin.kt` | 80 |
| `app/src/main/java/com/mission/launcher/Platform.kt` | 139 |
| `app/src/main/java/com/mission/launcher/ResetGate.kt` | 155 |
| `app/src/main/java/com/mission/launcher/SecureStore.kt` | 115 |
| `app/src/main/java/com/mission/launcher/SettingsActivity.kt` | 539 |
| `app/src/main/java/com/mission/launcher/Store.kt` | 183 |
| `app/src/main/java/com/mission/launcher/Theme.kt` | 104 |
| `app/src/main/java/com/mission/launcher/Timer.kt` | 243 |
| `app/src/main/java/com/mission/launcher/UI.kt` | 309 |
| `app/src/main/java/com/mission/launcher/Wallpaper.kt` | 240 |
| `app/src/main/java/com/mission/launcher/Water.kt` | 170 |

## XML inventory

| File | Lines |
|---|---:|
| `app/src/main/res/drawable/ic_launcher_bg.xml` | 1 |
| `app/src/main/res/drawable/ic_launcher_fg.xml` | 5 |
| `app/src/main/res/drawable/ic_stat_drop.xml` | 3 |
| `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | 5 |
| `app/src/main/res/values/strings.xml` | 1 |
| `app/src/main/res/values/styles.xml` | 19 |
| `app/src/main/res/xml/accessibility_config.xml` | 9 |
| `app/src/main/res/xml/backup_rules.xml` | 12 |
| `app/src/main/res/xml/data_rules.xml` | 25 |
| `app/src/main/res/xml/shortcuts.xml` | 5 |

## Findings and disposition

| ID | File/line | Symptom | Fix or status |
|---|---|---|---|
| AUD-01 | `.github/workflows/build.yml:19` | CI could choose the old source ZIP instead of the maintained source tree. | Fixed: prefer `src/`, retain ZIP fallback, run the wrapper and upload `Mission-Launcher-APK`. |
| B01 | `Cards.kt:288-315` | Syllabus tick refreshed the whole home view and closed the topic view. | Fixed: update the selected row locally; refresh after leaving the topic view. |
| B02 | `Cards.kt:314-321` | A completed syllabus topic could be unticked without authorization. | Fixed: untick goes through `ResetGate`. |
| B03 | `Data.kt:233-238` | Response accuracy could be interpreted as a completed topic. | Fixed: only the manual done set produces `DONE`; response data colours an incomplete topic. |
| B04/B05 | `Timer.kt:74-95` | Very short sessions were saved as zero minutes and repeated Stop could duplicate a session. | Fixed in code: under 30 seconds is not saved; finish is guarded and clears active session state. |
| B06/B07/B09 | `Timer.kt:35-70, 201-205` | Reboot/power-off recovery and Pomodoro phase boundaries could miscount time or lose a paused timer. | Fixed in code with persisted active/paused state, 15-second heartbeat recovery, and phase timestamp reset. Requires device verification. |
| B08 | `Apps.kt:83-103`, `Timer.kt:183-200` | Timer could continue in non-study apps. | Partial: UsageStats foreground detection and auto-pause exist. Usage access and user-selected study apps are required; validate HyperOS behavior. |
| B10 | `MainActivity.kt:50-63` | Resetting pages could discard host Android widgets. | Fixed in code: preserve widget IDs and reattach them to the reset pages. |
| B11 | `Platform.kt:104-118` | Hourly cleanup could delete unrelated cache contents. | Fixed: only stale direct files in `cacheDir` older than 24 hours are considered. |
| B12/B13 | `Water.kt:31-47`, `SettingsActivity.kt:164-177`, `Dialogs.kt:38-40` | Water times had hour-only controls and time pickers did not honor current values. | Fixed in code: IST `HH:mm` values and initialized time-picker values. |
| B14 | `Data.kt:271-275`, `SettingsActivity.kt:211-216` | Countdown date could differ between display and settings. | Fixed in code: one stored setting with the same default and display path; user can change it. |
| B15 | `Data.kt:41-45, 154-180` | PYQ records lacked year breakdown. | Fixed in code: map the Zite Year field and aggregate PYQ counts by year/subject. |
| B16 | `Data.kt:118-210` | Zite integration is pull-only. | Open: no push queue/retry implementation. |
| B17 | `Cards.kt:373-382`, `SettingsActivity.kt:130-141` | Sync labels can imply live data when using a snapshot, and status is incomplete. | Open: improve live/cache/stale status and expose last successful sync consistently. |
| B18 | `Data.kt:45-48, 147-151` | Display-text subject matching can misclassify CSAT quantitative questions. | Open: normalize using verified stable Zite subject identifiers. |
| B19 | `Cards.kt:333-346` | Accuracy based on a tiny response sample looked authoritative. | Fixed in code: display subject accuracy only after five responses and label the threshold. |
| B20/B21/B22 | `MainActivity.kt`, `Apps.kt` | Dock/page spacing, left-edge handle, and drawer density may obstruct content or be hard to tune. | Open: measure and verify on the Redmi Pad SE; no device result is available. |
| B23 | `Wallpaper.kt:91-103` | Moon is rendered as a fixed cut disc and stars have limited variation. | Open: computed lunar phase and richer star field remain. |
| B24 | `Timer.kt:151-161` | Timer notification icon was water-themed. | Fixed in code: use an alarm/timer system icon and colour by timer state. |
| B25 | `Ai.kt`, `SettingsActivity.kt` | AI provider labels may be inconsistent. | Open: audit all provider labels and consolidate the setting. |
| B26 | `ResetGate.kt`, `SecureStore.kt`, backup XML | Credentials/reset password must never be embedded or backed up in plaintext. | Fixed in code: reset password is user-set and PBKDF2-hashed; provider keys use Android Keystore AES-GCM; config and encrypted secrets are excluded from Android backup. Validate migration and exports on device. |
| AUD-02 | Build environment; no source line | Local build stops before compilation because Android SDK is unavailable. | Open validation limitation: run CI build with Java 17/SDK and device tests. |

This audit records source-level fixes, not proof of runtime behavior. Outstanding work is tracked in
[`BACKLOG.md`](./BACKLOG.md); Android/HyperOS limitations are in [`LIMITS.md`](./LIMITS.md).
