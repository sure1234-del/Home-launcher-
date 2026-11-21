# Implementation backlog

Items are not complete merely because they are listed. “In code; verify” means source changes
exist but build/device behavior is not yet validated.

## Bugs

| ID | Status | Work |
|---|---|---|
| B01 | In code; verify | Keep the Prelims topic view open while ticking and refresh only on exit. |
| B02 | In code; verify | Require reset-password verification to untick a topic. |
| B03 | In code; verify | Manual ticks determine DONE; accuracy only affects colour. |
| B04 | In code; verify | Sessions below 30 seconds are not saved and show a clear message. |
| B05 | In code; verify | Make Stop idempotent and clear timer state atomically. |
| B06 | In code; verify | Restore both running and paused timer state after reboot. |
| B07 | In code; verify | Heartbeat recovery caps study credit at persisted activity. |
| B08 | Partial | UsageStats auto-pause exists; test permission flows and HyperOS event reliability. |
| B09 | In code; verify | Reset elapsed timestamp at each Pomodoro phase transition. |
| B10 | In code; verify | Preserve Android widgets during app-page reset. |
| B11 | In code; verify | Clean only stale files in cacheDir, never all app caches. |
| B12 | In code; verify | Water schedule controls accept HH:mm in IST. |
| B13 | In code; verify | Time picker opens at the saved value. |
| B14 | In code; verify | One configurable exam date drives settings and countdowns. |
| B15 | In code; verify | Read Zite PYQ Year and aggregate by year/subject. |
| B16 | Open | Implement a Zite push queue with retries and idempotent writes after schema/API verification. |
| B17 | Open | Show accurate live, cached, stale, and last-sync states. |
| B18 | Open | Use verified stable Zite subject identifiers to classify CSAT rows. |
| B19 | In code; verify | Require a minimum sample before showing accuracy. |
| B20 | Open | Verify dock, page dots, insets, and card bottom padding on target tablet. |
| B21 | Open | Add and device-test an auto-hiding edge handle. |
| B22 | Open | Add configurable app-grid columns and icon-size controls. |
| B23 | Open | Compute moon phase and vary star sizes/brightness. |
| B24 | In code; verify | Use timer-appropriate notification icon and state colour. |
| B25 | Open | Normalize provider labels in settings and chat. |
| B26 | In code; verify | Keep passwords user-set and hash-only; encrypt provider API keys with Keystore. |

## Features, next implementation stages

1. **Build and data safety:** resolve CI compilation, add pure time/syllabus/timer/reset tests,
   verify import/export and failure behavior, and exercise password migration/reset on device.
2. **Study workflow:** connect timer selection to verified Prelims syllabus subjects, add topic
   linking, daily/weekly goals, streak verification, session history, and reliable foreground
   auto-pause.
3. **Targets:** support five targets with multi-topic selection, end dates, daily hour/time
   reminders, countdown display, and Calendar integration only after OAuth and offline behavior
   are specified.
4. **Notifications/widgets:** build persistent color-coded reminders with snooze/quiet hours and
   a tested widget gallery. Keep it empty by default; preserve host AppWidgets and user choices.
5. **Wallpaper and insights:** add opt-in weather, calculated moon phase, richer stars, validated
   Zite subject mapping/year insights, and bounded device-resolution rendering.
6. **Multitasking/gestures:** test resize/rotation lifecycle; document limitations and add only
   public-API gesture behavior with explicit accessibility consent.
7. **Polish:** drawer sizing, font/app-size controls, graph sizing, accessibility contrast, and
   Redmi Pad performance profiling.

Google login, a 50+ widget catalog, live weather, Calendar OAuth, and Zite push are intentionally
not represented as implemented until their APIs, consent flows, failure handling, and tests exist.
