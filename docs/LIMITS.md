# Platform and implementation limits

- A floating card requires Android's "Display over other apps" permission. A persistent water
  notification also requires notification permission on Android 13+. HyperOS may additionally
  restrict background alarms, autostart, or overlays; the app cannot silently bypass those
  controls.
- UsageStats-based study detection requires the user to grant Usage access and configure which
  installed apps count as study apps. Android does not expose a reliable semantic signal that
  distinguishes studying from entertainment inside the same app.
- Circle-to-Search cannot hand its selected image directly to Gemini in a floating window through
  a public Android API. The supported fallback is the launcher accessibility capture flow and
  system/Lens share path; automatic Gemini submission is not implemented.
- Android does not let a third-party launcher replace protected system navigation with arbitrary
  root-level back/home/recents gestures. Any accessibility gesture feature requires explicit
  user enablement and remains subject to Android/HyperOS gesture conflicts.
- Procedural wallpaper is rendered for the device display dimensions. Literal 8K textures are
  intentionally not allocated; higher-than-display render resolution would increase memory use
  without improving the panel's visible detail.
- Weather, Google Calendar OAuth/sync, Zite push/retry, and the requested 50+ custom widget
  gallery are not implemented. They must not be presented as live/working features yet.
- Local restore-point files are intentionally not pruned automatically so a reset cannot discard
  older backups without consent; storage can grow over time. A password-gated backup-retention
  control is still needed.
- Google login is not implemented. The current backup path uses Android's document picker and
  Android Auto Backup rather than an in-app Google OAuth flow.
- No Redmi Pad SE / HyperOS 2.0.207.0 device is available in this environment. Overlay, reboot,
  gesture, frame-rate, and low-memory claims require the device checklist in
  [`TESTING.md`](./TESTING.md).
