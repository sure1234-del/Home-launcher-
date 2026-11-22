# Testing checklist

## Validation status

## T1/T2 source validation
- [ ] Live wallpaper: Settings > Wallpaper > Set live wallpaper opens the chooser and the selected
      service reports as active. HyperOS fallback: static wallpaper must remain usable.
- [ ] Wait for or set the next IST :00/:30 boundary; verify static fallback and live weather refresh.
- [ ] Disable weather/network and confirm the wallpaper remains on cached/time-only sky and Settings
      reports offline/stale status.
- [ ] Open Settings > About and Diagnostics; verify crash log, memory class, trim, low-RAM and
      historical process exit information.

The manifest and backup-rule XML files parse successfully. With JDK 21 and Android SDK 35,
`./gradlew testDebugUnitTest --no-daemon` and `./gradlew assembleDebug --no-daemon` pass locally.
The debug APK is generated at `src/app/build/outputs/apk/debug/app-debug.apk`. No emulator,
Redmi Pad SE, or GitHub Actions run has verified this work yet.

## Build and static checks

- [x] Run `./gradlew assembleDebug` from the Android project with JDK 21 and Android SDK 35.
- [ ] Check light and dark themes on page/card surfaces: secondary text and colored text accents
      must retain at least 4.5:1 contrast; filled pills and chat bubbles must keep readable text.
- [ ] Verify text remains legible at 80% scale and that status is communicated with labels/icons,
      not color alone.
- [ ] Resize a system widget and confirm its requested dp height is stable at 80% and 100% app
      UI scale; fixed-height providers should not show a resize control, and provider constraints
      plus other widget options must be preserved.
- [ ] Search the widget catalog by title/subtitle, verify no-results feedback and dismissal,
      then select one entry and confirm only that card is added to the current page.
- [ ] Run unit tests for Asia/Kolkata date boundaries, half-hour wallpaper rotation,
      syllabus state transitions, timer save/discard and phase transitions, and reset-password
      hashing/lockout.
- [ ] Verify CI uploads the `Mission-Launcher-APK` artifact.
- [ ] Install the APK and inspect startup, logcat, and the private `files/crash.log` after a
      controlled test crash in a debug-only test build.

## Data safety and reset gate

- [ ] On first launch, set and confirm a 6-12 digit reset password; confirm no password value is
      present in source, settings backup, local JSON backup, or exported backup.
- [ ] Verify the same password is required before syllabus untick, cache deletion, restore, and
      Erase all. Verify wrong attempts lock out and do not modify data.
- [ ] Verify the automatic backup succeeds before any gated operation; force a backup failure
      and confirm the deletion/restore does not proceed.
- [ ] Delete each listed cache separately and confirm unrelated collections and Android
      AppWidgets remain.
- [ ] Verify Erase all clears local launcher data and secrets while preserving the just-created
      backup, previous backups, and crash log.
- [ ] Enter Zite/AI keys in Settings; confirm they work after process restart and that encrypted
      preferences are not included in Android backup or manual JSON exports.
- [ ] Upgrade from an install with legacy plaintext API keys; confirm keys migrate to Keystore
      storage and legacy config/preferences values are removed.

## Syllabus, Zite, and timers

- [ ] Tick several Prelims micro-topics in succession; verify the topic view stays open and each
      state survives app restart.
- [ ] Verify accuracy changes colour only; it never marks a topic done. Verify untick requires
      the reset password.
- [ ] Sync the configured Zite database and compare year/subject PYQ totals against source
      records; verify sample sizes below five do not show accuracy percentages.
- [ ] Test Stop and save at under 30 seconds, at 30 seconds, and after several minutes; stop
      twice and confirm there is at most one saved session.
- [ ] Test Pomodoro work-to-break and break-to-work transitions; verify breaks do not add work
      time.
- [ ] Pause, reboot, and resume the timer; verify paused state is retained and power-off time is
      not credited beyond the last persisted heartbeat.
- [ ] Grant Usage access, designate reading apps, then switch between a reading app, YouTube,
      music, and the launcher; verify auto-pause/resume and manual pause behavior.

## Water, timezone, and wallpaper

- [ ] Grant notification and display-over-other-apps access. Send a test reminder and verify
      both notification controls and the floating card appear over another app.
- [ ] Log water from the overlay, snooze it, dismiss the notification, and verify pending state
      and scheduled nag behavior.
- [ ] Set start/end values with non-zero minutes and verify scheduling across midnight in
      `Asia/Kolkata`.
- [ ] Change Android's device timezone and confirm dates, countdowns, schedule, and reminder
      times still follow `Asia/Kolkata`.
- [ ] Test month/year boundary behavior and the configured exam date.
- [ ] Verify procedural wallpaper rendering uses display dimensions (not an 8K bitmap), schedules
      a dedicated refresh at the configured interval (30 minutes by default), reschedules after
      reboot, and does not retain more than the intended small bitmap cache. Android power
      management may defer alarms.
- [ ] Choose Prayagraj and another preset city; verify the status, last-fetched time, and
      procedural wallpaper conditions match the selected city without requesting device location.
- [ ] Test weather refresh with internet disabled, provider errors, a successful response, and
      cached data older than 45 minutes. Confirm stale conditions are not rendered as current and
      the settings disclose Open-Meteo attribution and modeled-data limitations.
- [ ] Verify fog, overcast, rain, snow, and clear conditions on procedural scenes; confirm photo
      wallpapers are not altered by weather effects.
- [ ] Render night scenes at new, quarter, full, and three-quarter moon dates; verify phase,
      crescent direction, and star visibility across the time-of-day transitions.
- [ ] Verify low-memory behavior, rotation, multi-window resize, overlay teardown, and that the
      previous full-screen app remains alive.

## Personalization and topic-linked study

- [ ] Start adding an Android widget, recreate the launcher while the picker/bind/configuration
      screen is open, and verify the allocation is completed or released exactly once.
- [ ] Cancel each widget-picker stage and verify no orphan widget ID or home placement remains.
- [ ] Disable/uninstall a widget provider after placement; verify the saved placement shows a
      recovery message, returns when the provider is restored, and is removed only by explicit
      user action.
- [ ] Set drawer columns, drawer icon size, pinned-app icon size, font/UI scale (including below
      100%), and history-chart height; close/reopen the drawer and home screen to verify each
      saved value takes effect.
- [ ] Choose a custom next topic from the Zite Prelims syllabus, restart the launcher, and verify
      its subject, PYQ count, and topic details remain correct.
- [ ] Start a study session from the chosen topic, stop and save it, and verify subject/topic
      appear in session history and CSV export without changing older session records.

## Redmi Pad SE / HyperOS manual acceptance

- [ ] Enable the launcher as Home; launch and switch between several apps repeatedly.
- [ ] Check Android widgets: add from the gallery, resize, open details, restart, and confirm
      they persist until explicitly removed.
- [ ] Exercise water/timer overlays with screen on/off, battery saver, and HyperOS background
      restrictions. Record required per-app autostart/battery settings.
- [ ] Test screen-edge gestures and verify conflicts with system navigation are documented.
- [ ] Enable the optional edge handle, wait for it to hide, then tap the narrow edge strip and
      confirm the drawer opens; verify left/right system-back gestures still work and the handle
      stays out of edit/drawer states. Disable the option and confirm the strip is removed.
- [ ] Measure scrolling, rotation, and repeated wallpaper rendering for jank, memory pressure,
      or crashes; do not infer a 60fps pass without profiling.
