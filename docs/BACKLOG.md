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
| B17 | In code; verify | Show live, cached, stale, syncing/error states and the last successful sync in the Zite card and settings. |
| B18 | Open | Use verified stable Zite subject identifiers to classify CSAT rows. |
| B19 | In code; verify | Require a minimum sample before showing accuracy. |
| B20 | Open | Verify dock, page dots, insets, and card bottom padding on target tablet. |
| B21 | In code; verify | Add an opt-in, default-off, auto-hiding left-edge app-drawer handle. Keep an 8 dp hidden tap strip; do not request overlay permission or system gesture exclusion. Verify system back gestures and drawer access on the Redmi Pad. |
| B22 | In code; verify | Configure drawer columns, drawer/pinned icon sizes, system-widget height, smaller text/interface scales, and study-chart height. Convert [AppWidgetProviderInfo pixel sizes](https://developer.android.com/reference/android/appwidget/AppWidgetProviderInfo#minHeight) to dp, keep host dimensions independent of launcher UI scale, honor vertical resize capability, and preserve the [AppWidgetManager dp options](https://developer.android.com/reference/android/appwidget/AppWidgetManager#OPTION_APPWIDGET_MIN_HEIGHT). |
| B23 | In code; verify | Compute a date-based moon phase and vary deterministic star sizes/brightness. |
| B24 | In code; verify | Use timer-appropriate notification icon and state colour. |
| B25 | In code; verify | Use consistent Google Gemini, OpenAI ChatGPT, Anthropic Claude, and Custom (OpenAI-compatible) labels in settings/chat without changing stored provider IDs or request behavior. |
| B26 | In code; verify | Keep passwords user-set and hash-only; encrypt provider API keys with Keystore. |
| B27 | In code; verify | Schedule wallpaper refreshes independently at the configured interval without doubling hourly network/backup work. |
| B28 | In code; verify | Fall back between water notifications and the overlay, and invalidate stale nags/cards after logging, clearing, or snoozing. |
| B29 | In code; verify | Preserve pending Android widget IDs across Activity recreation; release on picker cancellation/bind failure; place the default widget if configuration cannot launch; keep unavailable providers visible without deleting saved placements. |
| B30 | In code; verify | Search the widget catalog and add only the selected entry; keep the picker usable as the catalog grows. |

## Ordered delivery plan

Work in this order; do not mark a phase complete based on source changes alone.

1. **Visual/accessibility foundation (in code; verify):** measure text and color contrast against
   actual light/dark surfaces; separate text-safe accent colors from decorative chart fills;
   retain the warm, low-glare look. Source-level contrast checks now pass for secondary text and
   accent labels. Exit: confirm rendered small text meets WCAG AA (4.5:1) on supported surfaces,
   color is never the only state cue, and settings remain usable at 80% text/UI scale.
2. **Build, state safety, and widget platform:** the repository already has a JDK 17 Android
   build workflow; local compilation is now unblocked with JDK 21 and Android SDK 35.
   Widget picker lifecycle, missing-provider handling, and a searchable gallery are now in code;
   add focused tests for date/syllabus/timer/reset/layout behavior and verify lifecycle-safe
   widget sizing. The custom catalog still has 21 entries; grow it to at least 50 distinct,
   tested widgets with meaningful interactions,
   persistent user configuration, and no widgets auto-added. Exit: all catalog entries pass
   render/action/persistence tests; add/remove does not erase their underlying data.
3. **Weather-aware wallpaper (in code; verify):** Open-Meteo is selected for modeled current
   conditions with seven fixed city presets and Prayagraj as default; no device location is
   requested. Requests have 7-second connect/read bounds, 45-minute refresh/cache state, visible
   cached/stale/error status, and current-weather effects are omitted when data is stale. Weather
   effects are limited to procedural scenes; photo wallpapers remain untouched. Wallpaper work
   respects the existing battery-saver pause (manual apply is user-triggered). Never claim
   modeled conditions are sensor-real-time. [Open-Meteo docs](https://open-meteo.com/en/docs)
   identify current conditions as 15-minute model data; its
   [terms](https://open-meteo.com/en/terms)/[licence](https://open-meteo.com/en/licence) require
   CC-BY attribution and restrict the free API to non-commercial use. Exit remains open until
   offline/provider-error, permission, month/season transitions, and device-memory behavior are
   tested; render at display dimensions rather than allocating literal 8K bitmaps.
4. **Study targets and Google Calendar:** first make five local-first targets (multi-topic,
   subject, due date, daily duration/time, countdown/reminder) and connect them to Prelims
   tracking. Add Calendar OAuth only after OAuth client configuration, consent/scopes, token
   storage, sync direction, and conflict/offline rules are approved. Exit: targets survive
   restart/offline use and Calendar sync is verified with a configured test account.
5. **Circle-to-Search:** complete the supported capture/share flow and attach the UPSC Prelims
   2027 context. Use Gemini/Lens only through public intents and supported APIs; never promise
   automatic image submission or a floating activity if Android does not permit it. Exit: test
   on supported Android versions with graceful fallback and explicit accessibility consent.
6. **Redmi Pad / HyperOS acceptance:** profile real device rendering, overlay/notification
   permissions, background alarms, split-screen/resizing, gestures, rotation, and memory use.
   Exit: record device/OS/build, steps, logs, and pass/fail for every checklist item; document
   system restrictions rather than bypassing them.
7. **Release polish:** resolve findings from device/CI, check contrast regressions and data
   migrations, then produce a signed/reproducible release build and user-facing setup guide.

Google Calendar OAuth, live weather, a 50+ custom widget catalog, and Redmi/HyperOS behavior
remain incomplete until their credentials/provider/device tests and phase exit checks pass.
