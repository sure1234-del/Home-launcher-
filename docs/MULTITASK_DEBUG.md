# Multitasking debug

## Script

1. Start Mission in a full-size window.
2. Launch five apps from the drawer.
3. Recreate the launcher in split-screen.
4. Resize 10 times between 540 and 1920 dp widths.
5. Open an existing PiP app and switch back to Mission.
6. Record logcat exit reasons, Android `ActivityManager` memory information and wall-clock gaps.

## Root-cause recording

For every close or resize failure, save device build, app version, action sequence,
`am crash`, logcat, and the `ActivityManager` exit reason. Android/HyperOS restrictions must not
be bypassed with root or hidden APIs. Until evidence is collected on the target tablet, the
Q06/Q07 statuses remain `NEEDS-DEVICE-TEST`.
