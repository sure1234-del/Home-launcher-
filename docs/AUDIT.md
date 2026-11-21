# Source audit

## Scope and build baseline

The Android source is extracted in `src/`; the repository also contains `Mission-L-Source.zip`.
Initially, the workflow selected a ZIP before looking for a source directory, so edits made only
under `src/` were not included in its build. The workflow now prefers `src/`. This audit is of the
extracted source tree.

`gradle -p src assembleDebug --no-daemon` was attempted. Gradle configuration completed, but
Android compilation could not start because this environment has no Android SDK configured
(`ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset and no standard SDK directory was present).
A Gradle 8.9 wrapper has since been generated. Gradle project configuration succeeds; the
workflow provisions Gradle 8.9 and builds from the maintained source tree. Its CI result remains
to be verified.

## Kotlin and manifest inventory

| File | Lines |
|---|---:|
| `app/src/main/AndroidManifest.xml` | 92 |
| `app/src/main/java/com/mission/launcher/Ai.kt` | 170 |
| `app/src/main/java/com/mission/launcher/Apps.kt` | 179 |
| `app/src/main/java/com/mission/launcher/Backup.kt` | 74 |
| `app/src/main/java/com/mission/launcher/Capture.kt` | 48 |
| `app/src/main/java/com/mission/launcher/Cards.kt` | 445 |
| `app/src/main/java/com/mission/launcher/ChatActivity.kt` | 110 |
| `app/src/main/java/com/mission/launcher/CircleService.kt` | 155 |
| `app/src/main/java/com/mission/launcher/Data.kt` | 252 |
| `app/src/main/java/com/mission/launcher/Dialogs.kt` | 52 |
| `app/src/main/java/com/mission/launcher/Extras.kt` | 86 |
| `app/src/main/java/com/mission/launcher/Focus.kt` | 33 |
| `app/src/main/java/com/mission/launcher/Icons.kt` | 103 |
| `app/src/main/java/com/mission/launcher/MainActivity.kt` | 315 |
| `app/src/main/java/com/mission/launcher/Migrate.kt` | 29 |
| `app/src/main/java/com/mission/launcher/Notifs.kt` | 35 |
| `app/src/main/java/com/mission/launcher/Pager.kt` | 64 |
| `app/src/main/java/com/mission/launcher/Pin.kt` | 80 |
| `app/src/main/java/com/mission/launcher/Platform.kt` | 129 |
| `app/src/main/java/com/mission/launcher/SettingsActivity.kt` | 407 |
| `app/src/main/java/com/mission/launcher/Store.kt` | 149 |
| `app/src/main/java/com/mission/launcher/Theme.kt` | 104 |
| `app/src/main/java/com/mission/launcher/Timer.kt` | 153 |
| `app/src/main/java/com/mission/launcher/UI.kt` | 309 |
| `app/src/main/java/com/mission/launcher/Wallpaper.kt` | 230 |
| `app/src/main/java/com/mission/launcher/Water.kt` | 149 |

## XML resource inventory

| File | Lines |
|---|---:|
| `app/src/main/res/drawable/ic_launcher_bg.xml` | 1 |
| `app/src/main/res/drawable/ic_launcher_fg.xml` | 5 |
| `app/src/main/res/drawable/ic_stat_drop.xml` | 3 |
| `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | 5 |
| `app/src/main/res/values/strings.xml` | 1 |
| `app/src/main/res/values/styles.xml` | 19 |
| `app/src/main/res/xml/accessibility_config.xml` | 9 |
| `app/src/main/res/xml/backup_rules.xml` | 7 |
| `app/src/main/res/xml/data_rules.xml` | 15 |
| `app/src/main/res/xml/shortcuts.xml` | 5 |

## Confirmed findings

| ID | File and line | Symptom | Fix |
|---|---|---|---|
| AUD-01 | `.github/workflows/build.yml`, source-preparation step | CI chooses `Mission-L-Source.zip` before the extracted `src/` tree, so source fixes made in `src/` are not built or published. | Prefer the maintained `src/` project when present and retain ZIP extraction only as a fallback. |
| B11 | `app/src/main/java/com/mission/launcher/Platform.kt`, `TickReceiver` cache cleanup | Hourly cleanup recursively deletes every item in `cacheDir`, including unrelated wallpaper and screenshot caches. | Remove only cache files older than 24 hours, without traversing unrelated persistent directories. |
| AUD-02 | Build environment; no source line | Local `assembleDebug` is blocked before compilation because no Android SDK is installed/configured. | Verify compilation in the provisioned GitHub Actions environment; configure a local SDK before treating local build validation as available. |

The remaining user-reported bugs and requested features require device-level validation and
are not marked complete by this baseline audit.
