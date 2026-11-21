# Testing checklist

## Validation status

The manifest and backup-rule XML files parse successfully. An Android Kotlin compile was
attempted but could not start because this environment has no Android SDK configured. No unit
test suite, emulator, Redmi Pad SE, or GitHub Actions run has verified this work yet.

## Build and static checks

- [ ] Run `./gradlew assembleDebug` from the Android project with JDK 17 and Android SDK 35.
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
- [ ] Verify procedural wallpaper rendering uses display dimensions (not an 8K bitmap), refreshes
      on the configured 30-minute interval, and does not retain more than the intended small
      bitmap cache.
- [ ] Verify low-memory behavior, rotation, multi-window resize, overlay teardown, and that the
      previous full-screen app remains alive.

## Redmi Pad SE / HyperOS manual acceptance

- [ ] Enable the launcher as Home; launch and switch between several apps repeatedly.
- [ ] Check Android widgets: add from the gallery, resize, open details, restart, and confirm
      they persist until explicitly removed.
- [ ] Exercise water/timer overlays with screen on/off, battery saver, and HyperOS background
      restrictions. Record required per-app autostart/battery settings.
- [ ] Test screen-edge gestures and verify conflicts with system navigation are documented.
- [ ] Measure scrolling, rotation, and repeated wallpaper rendering for jank, memory pressure,
      or crashes; do not infer a 60fps pass without profiling.
