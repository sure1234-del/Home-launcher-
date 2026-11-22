# Source audit

## Scope and build baseline

The maintained Android project is in `src/`. `.github/workflows/build.yml` was inspected and
now selects this tree before the legacy ZIP. The workflow provisions Java 17 and builds the
debug APK with the Gradle wrapper.

Local Kotlin/Android compilation was verified with JDK 21 and `ANDROID_HOME=/opt/android-sdk`
after identifying that the default JDK 25 was unsuitable for this Gradle build. Unit tests and
the debug APK assembly pass. XML parsing of the manifest and backup rules succeeds. Device,
emulator, and GitHub Actions behavior remains unverified until their checklists pass.

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
| B17 | `Data.kt`, `Cards.kt`, `SettingsActivity.kt` | Sync labels could imply live data when using a snapshot, and status was incomplete. | In code; verify: distinguish live, stale, cached, syncing, error, and snapshot states; show the last successful sync in IST and state that Zite is pull-only. |
| B18 | `Data.kt:45-48, 147-151` | Display-text subject matching can misclassify CSAT quantitative questions. | Open: normalize using verified stable Zite subject identifiers. |
| B19 | `Cards.kt:333-346` | Accuracy based on a tiny response sample looked authoritative. | Fixed in code: display subject accuracy only after five responses and label the threshold. |
| B20 | `MainActivity.kt` | Dock/page spacing and card bottom padding may obstruct content. | Open: measure and verify insets/spacing on the Redmi Pad SE; no device result is available. |
| B21 | `MainActivity.kt`, `SettingsActivity.kt` | The app drawer needed an optional edge affordance without taking over system navigation. | In code; verify: added a default-off, auto-hiding drawer handle with a narrow 8 dp hidden tap strip; it uses no overlay permission or gesture-exclusion zone. System-back and Redmi Pad behavior still need device testing. |
| B22 | `MainActivity.kt`, `Apps.kt`, `SettingsActivity.kt` | App-grid density and icon sizing were hard to tune; pinned icons/widgets had fixed sizes. | In code; verify: drawer columns, drawer/pinned icon sizes, system-widget height, reduced font/UI scales, and chart height are configurable. AppWidgetProviderInfo pixel limits are converted to dp; host dimensions ignore launcher UI scaling; resize is offered only for vertically resizable providers and existing widget options are preserved. |
| B23 | `Wallpaper.kt` | Moon phase was not computed and stars had limited variation. | In code; verify: date-based lunar phase, crescent orientation, and deterministic star variation. |
| B27 | `Wallpaper.kt`, `Platform.kt`, manifest | Wallpaper interval settings were checked only by the hourly maintenance alarm. | In code; verify: a dedicated configurable wallpaper alarm is restored on boot and kept separate from network/backup work. Android may defer it. |
| B28 | `Water.kt`, `Platform.kt` | Floating reminders could be skipped when one delivery channel or permission was unavailable, and stale nags could revive cleared cards. | In code; verify: use the available notification/overlay path, validate scheduled nag times, and invalidate pending cards on clear/snooze. Device permission flows remain untested. |
| B24 | `Timer.kt:151-161` | Timer notification icon was water-themed. | Fixed in code: use an alarm/timer system icon and colour by timer state. |
| B25 | `Ai.kt`, `SettingsActivity.kt`, `ChatActivity.kt` | AI provider labels were inconsistent across settings and chat. | In code; verify: use Google Gemini, OpenAI ChatGPT, Anthropic Claude, and Custom (OpenAI-compatible) consistently; stored provider IDs and request behavior are unchanged. |
| B26 | `ResetGate.kt`, `SecureStore.kt`, backup XML | Credentials/reset password must never be embedded or backed up in plaintext. | Fixed in code: reset password is user-set and PBKDF2-hashed; provider keys use Android Keystore AES-GCM; config and encrypted secrets are excluded from Android backup. Validate migration and exports on device. |
| AUD-02 | Build environment; no source line | The default Java 25 runtime and unset SDK environment initially blocked local validation. | Resolved locally with JDK 21 and Android SDK 35; unit tests and debug APK pass. Keep CI and device tests open. |
| UX-01 | `MainActivity.kt` | New installs automatically populated all home pages with cards. | In code; verify: new default pages are empty; saved layouts remain unchanged, and users can add/remove cards themselves. |
| UX-02 | `Cards.kt`, `Timer.kt`, `Data.kt` | The selected next topic could not be edited or linked to a timer session. | In code; verify: choose a Zite Prelims topic, start a subject/topic-tagged session, and retain it in history and CSV export. |
| UX-03 | `Theme.kt`, `UI.kt`, `Cards.kt`, `SettingsActivity.kt` | Secondary labels and muted accent text fell below WCAG AA contrast on light surfaces. | In code; verify: secondary text now measures 4.53:1 on the light page and 5.10:1 on white cards; colored text accents are adjusted to at least 4.5:1 on light and dark surfaces; filled pills and chat bubbles select a contrasting foreground. Recheck on-device rendering. |
| B29 | `MainActivity.kt` | Android widget picker IDs could leak on launch/cancel, be lost during Activity recreation, or leave saved placements invisible when their provider disappears. | In code; verify: save the pending ID across recreation, release it on cancellation/bind failure, avoid duplicate placement, and show a recoverable placeholder without deleting the user's saved placement. A configuration-launch failure adds the provider's default layout instead of orphaning the ID. |
| UX-04 | `WidgetCatalogDialog.kt`, `MainActivity.kt` | A growing widget list would be difficult to navigate, and selecting a card should never silently add other cards. | In code; verify: searchable title/subtitle picker, clear no-results state, selection closes the dialog and adds only the chosen item; there are still 21 custom cards, not 50+. |
| WX-01 | `Weather.kt`, `Wallpaper.kt`, `SettingsActivity.kt` | Procedural scenes did not use fetched local weather, and stale data could be mistaken for current conditions. | In code; verify: Open-Meteo conditions for a selectable fixed city (Prayagraj default) are cached, refreshes are bounded, status and attribution are shown, and weather effects are skipped when data is stale. Current conditions are 15-minute model data, not sensor-live. Network, visual, and device behavior remain unverified. |

This audit records source-level fixes, not proof of runtime behavior. Outstanding work is tracked in
[`BACKLOG.md`](./BACKLOG.md); Android/HyperOS limitations are in [`LIMITS.md`](./LIMITS.md).
