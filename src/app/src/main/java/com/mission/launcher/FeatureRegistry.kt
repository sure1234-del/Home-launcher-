package com.mission.launcher

object FeatureRegistry {
    data class Feature(
        val id: String,
        val title: String,
        val defaultEnabled: Boolean = false,
    )

    val features = listOf(
        Feature("x01_flashcards", "Subject-linked flashcards"),
        Feature("x02_pyq_quiz", "Timed PYQ quiz"),
        Feature("x03_answer_timer", "Mains answer timer"),
        Feature("x04_affairs", "Current-affairs checklist"),
        Feature("x05_weekly_review", "Weekly review"),
        Feature("x06_smart_plan", "Smart daily plan"),
        Feature("x07_free_slots", "Calendar free-slot finder"),
        Feature("x08_slot_templates", "Study slot templates"),
        Feature("x09_focus_presets", "Focus presets"),
        Feature("x10_mock_overlay", "Mock-test overlay"),
        Feature("x11_distraction_report", "Distraction report"),
        Feature("x12_autopause_grace", "Auto-pause grace period"),
        Feature("x13_idle_detection", "Idle detection"),
        Feature("x14_streak_freeze", "Weekly streak freeze"),
        Feature("x15_goal_rings", "Goal rings"),
        Feature("x16_notes_overlay", "Notes quick-capture overlay"),
        Feature("x17_subject_notes", "Per-subject notes/PDF links"),
        Feature("x18_voice_note", "Voice notes"),
        Feature("x19_clipboard_notes", "Clipboard to notes"),
        Feature("x20_notification_digest", "Notification digest"),
        Feature("x21_dnd_automation", "DND by study slot"),
        Feature("x22_automation", "Automation engine"),
        Feature("x23_automation_templates", "Automation templates"),
        Feature("x24_qs_tiles", "Quick Settings tiles"),
        Feature("x25_launcher_shortcuts", "Launcher shortcuts"),
        Feature("x26_app_limits", "App usage limits"),
        Feature("x27_sleep_winddown", "Sleep wind-down"),
        Feature("x28_briefs", "IST morning/evening briefs"),
        Feature("x29_weather_water", "Weather-based water goals"),
        Feature("x30_air_advisory", "Air-quality advisory"),
        Feature("x31_report_share", "Weekly PDF/image report"),
        Feature("x32_home_presets", "Home presets"),
        Feature("x33_workspaces", "Saved app workspaces"),
        Feature("x34_keyboard_spotlight", "Keyboard Spotlight"),
        Feature("x35_stylus_note", "Stylus quick note"),
        Feature("x36_right_panel", "Right-edge control panel"),
        Feature("x37_hot_corners", "Hot corners"),
        Feature("x38_wake_actions", "Double-tap/safe wake actions"),
        Feature("x39_app_sort", "Folder auto-sort suggestions"),
        Feature("x40_app_lock", "App hide/lock"),
        Feature("x41_icon_pack", "Icon pack support"),
        Feature("x42_themes", "Advanced themes"),
        Feature("x43_clock_styles", "Lock-style clock styles"),
        Feature("x44_streak_heatmap", "Streak heatmap"),
        Feature("x45_greeting", "IST time-of-day greeting"),
        Feature("x46_offline_pyq", "Offline PYQ cache"),
        Feature("x47_google_signin", "Google sign-in"),
        Feature("x48_calendar_sync", "Google Calendar sync"),
        Feature("x49_today_dashboard", "Today dashboard"),
        Feature("x50_diagnostics", "Self-diagnostics wizard"),
    )

    fun enabled(feature: Feature): Boolean =
        Store.b(feature.id, feature.defaultEnabled)

    fun setEnabled(feature: Feature, enabled: Boolean) {
        Store.put(feature.id, enabled)
        Store.flushNow()
    }

    fun mainsFeaturesEnabled(): Boolean =
        Store.b("show_mains", false)
}
