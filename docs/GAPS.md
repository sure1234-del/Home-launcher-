# Gap inventory

Status values: `FIXED`, `PARTIAL`, `NOT-A-BUG`, `NEEDS-DEVICE-TEST`,
`NEEDS-SOURCE-DOC`, `IMPOSSIBLE(reason)`.

The PART2/PART3 source documents were absent. Their known ID families are retained below; they
cannot be expanded beyond the available repository evidence.

## Part 2/3 known families

| ID(s) | Status | Notes |
|---|---|---|
| B01-B26 | PARTIAL | Known audit findings; source fixes exist, remaining device verification is open. |
| P01-P20 | NEEDS-SOURCE-DOC | Parent prompt file absent. |
| W01-W24 | NEEDS-SOURCE-DOC | Parent prompt file absent. |
| G01-G20 | NEEDS-SOURCE-DOC | Parent prompt file absent. |
| U01-U22 | NEEDS-SOURCE-DOC | Parent prompt file absent. |
| F01-F35 | NEEDS-SOURCE-DOC | Parent prompt file absent. |
| A01-L12 | NEEDS-SOURCE-DOC | Parent prompt file absent. |
| R01-R10 | NEEDS-SOURCE-DOC | Parent prompt file absent. |
| E01-E08 | NEEDS-SOURCE-DOC | Parent prompt file absent. |
| V1-V4 | NEEDS-SOURCE-DOC | Parent prompt file absent. |

## M - memory and performance

| ID | Status | Notes |
|---|---|---|
| M01 | FIXED | PSS 280 MB ceiling and debug assertion added in `MemoryBudget.kt`. |
| M02 | FIXED | UI-hidden trimming clears icon cache hooks and live-wallpaper frame. |
| M03 | FIXED | Hidden wallpaper engine releases frame after 60 seconds. |
| M04 | FIXED | Separate icon/widget cache budget constants are defined. |
| M05 | PARTIAL | First-frame deferral for Zite/weather/calendar is source-worked but needs profiling. |
| M06 | NEEDS-DEVICE-TEST | Requires Redmi Pad 90 Hz and Macrobenchmark instrumentation. |
| M07 | PARTIAL | Existing code uses view flattening but a full overdraw pass remains. |
| M08 | NEEDS-DEVICE-TEST | JankStats package and blur hooks require sustained-frame instrumentation. |
| M09 | PARTIAL | Battery saver exists; thermal-status FPS reduction remains to be wired. |
| M10 | PARTIAL | Battery saver/static mode exists; WorkManager sync is not yet added. |
| M11 | PARTIAL | Timer and widget pending state survive, remaining sheet/overlay state needs tests. |
| M12 | PARTIAL | Allocation hot spots remain to be audited with Android Studio. |

## N - Android 15 / HyperOS

| ID | Status | Notes |
|---|---|---|
| N01 | NEEDS-DEVICE-TEST | Edge-to-edge target 35 and activity insets need rendered-device verification. |
| N02 | FIXED | `POST_NOTIFICATIONS` is declared and startup requests it. |
| N03 | FIXED | Timer uses special-use FGS and API 35 timeout handling; boot restrictions documented. |
| N04 | FIXED | Exact alarm declarations and settings deep link added. Full-screen-intent use is avoided. |
| N05 | NEEDS-DEVICE-TEST | Overlay touch policy source exists; obscured-touch behavior needs device test. |
| N06 | FIXED | Application overlay uses supported window flags and not-focusable behavior. |
| N07 | FIXED | Launcher, calendar, Lens/YouTube/Gemini package visibility declarations were added. |
| N08 | NOT-A-BUG | No native libraries are shipped. |
| N09 | NEEDS-DEVICE-TEST | Predictive back needs modern ViewActivity implementation verification. |
| N10 | PARTIAL | Locale and per-app Hindi/English resources are not complete. |
| N11 | NEEDS-DEVICE-TEST | HyperOS settings deep links need device confirmation. |
| N12 | PARTIAL | No broad storage permission; photo picker needs a Settings UI path. |
| N13 | FIXED | IST uses `Asia/Kolkata` through `TimeProvider`. |

## O - security and privacy

| ID | Status | Notes |
|---|---|---|
| O01 | FIXED | Reset and backup surfaces set `FLAG_SECURE` where implemented. |
| O02 | FIXED | Cleartext traffic is disabled by network security config. |
| O03 | FIXED | Provider secrets use Android Keystore AES-GCM wrapper. |
| O04 | PARTIAL | Normal log paths avoid secrets; captured text audit remains. |
| O05 | NEEDS-DEVICE-TEST | Accessibility Shorts guard and disclosure require UI/device verification. |
| O06 | PARTIAL | Capture cache cleanup uses 10-minute policy where applicable. |
| O07 | PARTIAL | JSON backup checksum integrity needs restore implementation. |
| O08 | FIXED | Only required activities, services and receivers are exported. |
| O09 | FIXED | Pending intents use immutable flags unless mutation is explicitly needed. |
| O10 | FIXED | Debug-only code is guarded by `BuildConfig.DEBUG`. |

## P - wallpaper extras

| ID | Status | Notes |
|---|---|---|
| P01 | PARTIAL | Procedural sky keys use clock interpolation; solar altitude remains open. |
| P02 | NEEDS-DEVICE-TEST | Twilight-stage interpolation needs rendered validation. |
| P03 | NEEDS-DEVICE-TEST | Prayagraj moon rise/set calculation needs astronomical validation. |
| P04 | PARTIAL | Subtle glare exists; thermal throttle gate is open. |
| P05 | PARTIAL | Procedural faint stars exist; Milky Way remains optional. |
| P06 | PARTIAL | Weather haze exists behind provider cache. |
| P07 | PARTIAL | Rain weather effect exists; 30-minute ground fade remains open. |
| P08 | NEEDS-DEVICE-TEST | Wallpaper Lab scrubbing needs UI/device validation. |
| P09 | NEEDS-DEVICE-TEST | Lock-screen countdown uses live engine but needs device validation. |
| P10 | PARTIAL | Preset scenes exist; photo picker UI remains. |

## Q - multitasking

| ID | Status | Notes |
|---|---|---|
| Q01 | FIXED | Activity resizeable and orientation-free configuration exist. |
| Q02 | PARTIAL | Supported launch modes remain limited to public launch intents. |
| Q03 | PARTIAL | Existing dock and app drawer need last-8-app strip. |
| Q04 | FIXED | Launcher does not request PiP and does not reserve PiP windows. |
| Q05 | NEEDS-DEVICE-TEST | Gesture-zone and IME pass-through need Redmi test. |
| Q06 | NEEDS-DEVICE-TEST | Rapid app/window stress script not completed on device. |
| Q07 | NEEDS-DEVICE-TEST | HyperOS root cause evidence needs logs from the target device. |

## R - data and sync extras

| ID | Status | Notes |
|---|---|---|
| R11 | FIXED | Atomic JSON remains selected; no Room migration changes existing data. |
| R12 | PARTIAL | Core records do not consistently expose id, updatedAt and deviceId. |
| R13 | PARTIAL | Latest-updated conflict policy needs tombstone implementation. |
| R14 | PARTIAL | Zite status exists; pending queue view remains. |
| R15 | PARTIAL | Immediate sync exists in places; WorkManager periodic sync remains. |
| R16 | FIXED | Sync failures remain non-fatal and surfaced by status labels. |
| R17 | PARTIAL | Manual backup exists; 7-point weekly rotation remains. |
| R18 | PARTIAL | CSV export exists; JSON export remains. |

## S - UX

| ID | Status | Notes |
|---|---|---|
| S01 | PARTIAL | Welcome flow exists; full first-run wizard remains. |
| S02 | PARTIAL | Settings exists; search, breadcrumbs and two-pane mode remain. |
| S03 | PARTIAL | Existing lists do not consistently show loading/error/offline states. |
| S04 | PARTIAL | Haptic/sound controls remain. |
| S05 | PARTIAL | Compact density settings need catalog-wide wiring. |
| S06 | PARTIAL | Shape/letter cues and colour-blind palette remain. |
| S07 | PARTIAL | Changelog exists; in-app What's New remains. |
| S08 | PARTIAL | Permissions section exists; per-feature test wizard remains. |
