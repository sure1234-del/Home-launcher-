package com.mission.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.time.LocalDate

/** Settings as a real folder tree: each node opens a full-screen opaque page, folders may contain folders. */
class SettingsActivity : Activity() {
    private class Page(val title: String, val build: LinearLayout.() -> Unit)
    private val stack = ArrayList<Page>()
    private lateinit var title: TextView
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var back: View
    private val REQ_FILE = 85; private val REQ_CSV = 86; private val REQ_TREE = 81; private val REQ_EXPORT = 82; private val REQ_IMPORT = 83; private val REQ_PHOTO = 84
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Boot.ensure(this)
        systemBars(T.page)
        val root = vbox(); root.setBackgroundColor(T.page)
        root.setOnApplyWindowInsetsListener { v, i -> val s = i.getInsets(WindowInsets.Type.systemBars()); v.setPadding(0, s.top, 0, s.bottom); i }
        val bar = hbox(); bar.padDp(8, 8, 16, 8)
        back = iconBtn("chevl", T.text, 44) { goBack() }; bar.addView(back)
        title = text("Settings", 22f, 3); bar.addView(title, lpm(0, -2, 4, 0, 0, 0, 1f))
        root.addView(bar)
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        body = vbox(); body.setPadding(T.dp(16), T.dp(4), T.dp(16), T.dp(40)); scroll.addView(body)
        val wide = resources.displayMetrics.widthPixels / resources.displayMetrics.density > 700
        val holder = FrameLayout(this); holder.addView(scroll, FrameLayout.LayoutParams(if (wide) T.dp(720) else -1, -1, if (wide) Gravity.CENTER_HORIZONTAL else Gravity.START))
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        push(Page("Settings") { home() })
        intent.getStringExtra("path")?.let { p -> when (p) { "zite" -> push(Page("Zite and data") { zite() }) ; "ai" -> push(Page("AI") { ai() }); "setup" -> push(Page("Setup checklist") { setup() }); "reset" -> push(Page("Reset and data") { resetData() }) } }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() = goBack()
    private fun goBack() { if (stack.size > 1) { stack.removeAt(stack.size - 1); show() } else finish() }
    private fun push(p: Page) { stack.add(p); show() }
    private fun show() { val p = stack.last(); title.text = p.title; back.visibility = View.VISIBLE; val y = 0; body.removeAllViews(); p.build(body); scroll.scrollTo(0, y) }
    private fun redraw() { val y = scroll.scrollY; body.removeAllViews(); stack.last().build(body); scroll.post { scroll.scrollTo(0, y) } }

    // ---------- row helpers ----------
    private fun LinearLayout.group(vararg rows: View) { val c = Card(this@SettingsActivity, 20); c.padDp(6, 4, 6, 4); rows.forEachIndexed { i, r -> if (i > 0) c.addView(divider(), lpm(-1, 1, 14, 0, 0, 0)); c.addView(r) }; addView(c, lpm(-1, -2, 0, 0, 0, 14)) }
    private fun LinearLayout.label(s: String) = addView(text(s.uppercase(), 11.5f, 3, T.text3).apply { letterSpacing = 0.06f; padDp(10, 10, 0, 6) })
    private fun LinearLayout.note(s: String) = addView(text(s, 12f, 0, T.text3).apply { padDp(10, 0, 10, 12) })
    private fun rowBase(ic: String?, tint: Int, t: String, sub: String?, right: View?, click: (() -> Unit)?): View = hbox().apply {
        padDp(12, 13, 12, 13); minimumHeight = T.dp(52)
        if (ic != null) addView(FrameLayout(this@SettingsActivity).apply { background = T.round(T.withAlpha(tint, 0.16f), 10); addView(icon(ic, tint, 18, 1.9f), FrameLayout.LayoutParams(T.dp(18), T.dp(18), Gravity.CENTER)) }, lpm(T.dp(34), T.dp(34), 0, 0, 12, 0))
        val col = vbox(); col.addView(text(t, 15f, 1, T.text)); if (!sub.isNullOrBlank()) col.addView(text(sub, 12f, 0, T.text3), lp(-2, -2, 2))
        addView(col, LinearLayout.LayoutParams(0, -2, 1f)); if (right != null) addView(right, lpm(-2, -2, 8, 0, 0, 0))
        if (click != null) tap(false) { click() }
    }
    private fun folder(ic: String, tint: Int, t: String, sub: String, build: LinearLayout.() -> Unit): View = rowBase(ic, tint, t, sub, icon("chev", T.text3, 16, 2f)) { push(Page(t, build)) }
    private fun action(ic: String?, tint: Int, t: String, sub: String? = null, f: () -> Unit): View = rowBase(ic, tint, t, sub, null, f)
    private fun value(t: String, sub: String? = null, v: String, f: () -> Unit): View = rowBase(null, 0, t, sub, text(v, 13.5f, 1, T.text2, 1).apply { maxWidth = T.dp(170) }, f)
    private fun sw(t: String, sub: String?, key: String, def: Boolean, after: ((Boolean) -> Unit)? = null): View {
        val s = Switch(this).apply { isChecked = Store.b(key, def); thumbTintList = ColorStateList.valueOf(Color.WHITE); trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(T.sage, T.withAlpha(T.text3, 0.45f))) }
        s.setOnCheckedChangeListener { _, on -> Store.put(key, on); after?.invoke(on) }
        return rowBase(null, 0, t, sub, s) { s.toggle() }
    }
    private fun choice(t: String, key: String, def: String, opts: List<Pair<String, String>>, sub: String? = null, after: (() -> Unit)? = null): View {
        val cur = Store.s(key, def); return value(t, sub, opts.firstOrNull { it.first == cur }?.second ?: cur) { Dlg.choose(this, t, opts.map { it.second }) { i -> Store.put(key, opts[i].first); after?.invoke(); redraw() } }
    }
    private fun num(t: String, key: String, def: Int, lo: Int, hi: Int, unit: String, sub: String? = null, after: (() -> Unit)? = null): View =
        value(t, sub, "${Store.i(key, def)} $unit") { Dlg.input(this, t, "$lo - $hi", Store.i(key, def).toString(), true) { s -> s.toIntOrNull()?.let { Store.put(key, it.coerceIn(lo, hi)); after?.invoke(); redraw() } } }
    private fun slider(t: String, key: String, def: Int, max: Int, suffix: String = "%", after: (() -> Unit)? = null): View = vbox().apply {
        padDp(14, 12, 14, 8); val v = text("${Store.i(key, def)}$suffix", 13.5f, 1, T.text2)
        val r = hbox(); r.addView(text(t, 15f, 1), LinearLayout.LayoutParams(0, -2, 1f)); r.addView(v); addView(r)
        addView(SeekBar(this@SettingsActivity).apply { this.max = max; progress = Store.i(key, def); progressTintList = ColorStateList.valueOf(T.sage); thumbTintList = ColorStateList.valueOf(T.sage)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { v.text = "$p$suffix" }; override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) { Store.put(key, s!!.progress); after?.invoke() } }) }, lp(-1, -2, 2))
    }
    private fun secret(t: String, get: () -> String, set: (String) -> Unit, hint: String): View {
        val k = get()
        val detail = SecureStore.lastError?.let { "$hint $it" } ?: hint
        return value(t, detail, if (k.isBlank()) "Not set" else "••••" + k.takeLast(4)) {
            Dlg.input(this, t, "Paste here", "", false, false, "Save", true) { s ->
                try { set(s.trim()); redraw() } catch (e: Exception) { Log.e("secret-save", e); toast("Could not save this key securely") }
            }
        }
    }
    private fun multiPick(titleS: String, key: String, defaults: Set<String> = emptySet(), max: Int = 0) {
        val apps = Apps.list; val cur = if (Store.has(key)) Store.stringList(key).toMutableSet() else defaults.toMutableSet()
        val names = apps.map { it.label }.toTypedArray(); val checked = BooleanArray(apps.size) { apps[it].pkg in cur }
        Dlg.builder(this).setTitle(titleS).setMultiChoiceItems(names, checked) { d, i, on -> if (on) { if (max > 0 && checked.count { it } > max) { checked[i] = false; (d as android.app.AlertDialog).listView.setItemChecked(i, false); toast("Max $max") } } }
            .setPositiveButton("Save") { _, _ -> val sel = apps.filterIndexed { i, _ -> checked[i] }.map { it.pkg }; val keep = cur.filter { p -> apps.none { it.pkg == p } }; Store.setStringList(key, keep + sel); Store.changed(); redraw() }.setNegativeButton("Cancel", null).show()
    }

    // ---------- root ----------
    private fun LinearLayout.home() {
        val z = Zite.data(this@SettingsActivity)
        group(folder("checkc", T.sage, "Setup checklist", "${setupItems().count { it.ok }} of ${setupItems().size} ready") { setup() })
        group(folder("shield", T.danger, "Reset and data", "Password-protected cache deletion and restore") { resetData() })
        group(folder("refresh", T.sage, "Zite and data", if (Zite.hasKey()) "Hourly sync on" else "Add API key for live sync") { zite() },
            folder("sparkle", T.plum, "AI assistants", Ai.provider().name + " - " + Ai.model()) { ai() })
        group(folder("drop", T.teal, "Water", "${Water.goal()} ml goal, reminders ${if (Water.enabled()) "on" else "off"}") { water() },
            folder("timer", T.amber, "Study, timer and focus", "Floating timer, pomodoro, distractions") { study() },
            folder("calendar", T.terra, "Exam", Store.s("exam_date", "2027-05-23")) { exam() })
        group(folder("layers", T.plum, "Gestures and multitasking", "Double tap, swipe down, floating apps") { gestures() },
            folder("search", T.plum, "Circle to Search", if (CircleService.running()) "Ready" else "Needs accessibility service") { circle() },
            folder("shield", T.rose, "Limits and mindfulness", "${Guard.limits().length()} app limits") { limits() },
            folder("bell", T.amber, "Daily briefs", "Morning, evening, weekly") { briefs() })
        group(folder("image", T.slate, "Wallpaper", Wallpaper.scene() + (if (Wallpaper.enabled()) ", auto" else ", off")) { wallpaper() },
            folder("grid", T.olive, "Home screen", "Pages, widgets, layout") { homeScreen() },
            folder("grid", T.clay, "Apps and dock", "${Apps.pins().size} pinned, ${Apps.hidden().size} hidden") { apps() },
            folder("sliders", T.rose, "Appearance", Store.s("theme", "auto")) { appearance() })
        group(folder("lock", T.plum, "Security and PIN", if (Pin.isSet()) "PIN on" else "No PIN") { security() },
            folder("cloud", T.teal, "Backup and restore", if (Store.s("backup_tree").isBlank()) "Device only" else "Device + folder") { backup() },
            folder("shield", T.slate, "Permissions", "Overlay, usage, notifications") { permissions() })
        group(folder("info", T.text2, "About and diagnostics", "Mission 2027 launcher") { about() })
    }

    // ---------- Zite ----------
    private fun LinearLayout.zite() {
        val c = this@SettingsActivity
        label("Connection")
        group(secret("API key", { SecureStore.get("zite") }, { SecureStore.put("zite", it); Zite.invalidate() }, "Zite > Settings > API keys. Encrypted with Android Keystore."),
            value("Database id", null, Zite.db().take(10) + "...") { Dlg.input(c, "Database id", Zite.DEFAULT_DB, Zite.db()) { s -> Store.put("zite_db", s.ifBlank { Zite.DEFAULT_DB }); redraw() } },
            value("Portal link", "Opened by Quick links", Store.s("zite_portal", Zite.PORTAL).removePrefix("https://").take(26) + "...") { Dlg.input(c, "Portal URL", "https://", Store.s("zite_portal", Zite.PORTAL)) { s -> Store.put("zite_portal", s.ifBlank { Zite.PORTAL }); redraw() } })
        label("Sync")
        val last = Store.l("zite_last_ok", 0)
        group(action("refresh", T.sage, "Sync now", if (last > 0) "Last: " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.ENGLISH).format(java.util.Date(last)) else "Never synced") {
            if (!Zite.hasKey()) toast("Add the API key first") else { toast("Syncing..."); Thread { val m = Zite.sync(c, true); runOnUiThread { toast(m); redraw() } }.start() } })
        note("Updates automatically every hour while the phone is on (also when the launcher is closed). Syllabus, mocks, attempts and responses come from your Zite database. Only study-relevant events are shown in Upcoming.")
        if (Store.s("zite_last_err").isNotBlank()) { label("Last error"); note(Store.s("zite_last_err")) }
    }

    // ---------- AI ----------
    private fun LinearLayout.ai() {
        val c = this@SettingsActivity; val p = Ai.provider()
        label("Provider")
        group(value("Active assistant", null, p.name) { Dlg.choose(c, "Provider", Ai.providers.map { it.name }) { i -> Store.put("ai_provider", Ai.providers[i].id); redraw() } },
            secret(p.name + " API key", { Ai.key(p.id) }, { Ai.setKey(p.id, it) }, if (p.id == "gemini") "Free key: aistudio.google.com/apikey" else "Stored only on this device"),
            value("Model", "Pick from your key's live list", Ai.model(p).take(26)) {
                if (!Ai.ready(p)) toast("Add the key first") else { toast("Loading models..."); Thread { try { val m = Ai.fetchModels(p); runOnUiThread { Dlg.choose(c, "Model", m) { i -> Store.put("ai_model_${p.id}", m[i]); redraw() } } } catch (e: Exception) { runOnUiThread { toast(e.message ?: "Failed") } } }.start() } },
            action(null, 0, "Type a model name manually", Ai.model(p)) { Dlg.input(c, "Model id", p.model, Ai.model(p)) { s -> Store.put("ai_model_${p.id}", s); redraw() } })
        if (p.needsBase) group(value("Base URL", "OpenAI-compatible endpoint", Ai.base(p).removePrefix("https://").take(24)) { Dlg.input(c, "Base URL", p.base, Ai.base(p)) { s -> Store.put("ai_base_${p.id}", s); redraw() } })
        label("Behaviour")
        group(sw("Gemini can act on your data", "Add tasks, notes, water, events, start timer from chat", "ai_tools", true))
        label("Other assistants on this phone")
        group(action("sparkle", T.plum, "Open Xiaomi / system assistant", "Hyper AI / Google Assistant, whichever is the default") { if (!Ai.assist(c)) toast("No assistant found") },
            *Ai.installedAiApps(c).map { a -> action(null, 0, a.label, "Open app") { if (!Ai.openApp(c, a.pkg)) toast("Cannot open") } }.toTypedArray().ifEmpty { arrayOf(note0("No other AI apps detected")) })
        note("Xiaomi's Hyper AI has no public API, so it is used through the system assistant button and the share sheet. Keys for Gemini, ChatGPT, Claude or any OpenAI-compatible service work inside the launcher.")
    }
    private fun note0(s: String): View = text(s, 13f, 0, T.text3).apply { padDp(14, 12, 14, 12) }

    // ---------- Water ----------
    private fun LinearLayout.water() {
        val c = this@SettingsActivity
        label("Reminders")
        group(sw("Water reminders", "Persistent notification until you act on it", "water_on", true) { Water.arm(c); if (!it) Water.clear(c) },
            sw("Insist until I drink", "If dismissed it returns, getting firmer each time", "water_insist", true),
            num("Daily goal", "water_goal", 3000, 500, 8000, "ml") { Water.arm(c) },
            value("Start time", "IST (HH:mm)", "%02d:%02d".format(Water.startTime().hour, Water.startTime().minute)) {
                Dlg.time(c, Water.startTime()) { Store.put("water_start_time", it); Water.arm(c); redraw() }
            },
            value("End time", "IST (HH:mm)", "%02d:%02d".format(Water.endTime().hour, Water.endTime().minute)) {
                Dlg.time(c, Water.endTime()) { Store.put("water_end_time", it); Water.arm(c); redraw() }
            },
            action("layers", T.teal, "Floating water card", if (c.canOverlay()) "Allowed" else "Allow display over other apps") {
                try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                catch (e: Exception) { Log.e("water-overlay-settings", e); toast("Could not open overlay settings") }
            },
            num("Remind every", "water_interval", 90, 20, 240, "min") { Water.arm(c) },
            num("Nag again after", "water_nag", 8, 2, 30, "min"))
        label("Quick amounts")
        group(value("Cup sizes", "Shown as buttons in the notification and card", Water.cups().joinToString(", ") + " ml") { Dlg.input(c, "Cup sizes (ml, comma separated)", "150,250,500", Water.cups().joinToString(",")) { s -> Store.put("water_cups", s); redraw() } })
        label("Test")
        group(action("bell", T.teal, "Send a reminder now", "Check the notification, buttons and floating card") { Water.remind(c, false) })
        note("Water history is part of the daily backup (device and your chosen Drive folder).")
    }

    // ---------- Study ----------
    private fun LinearLayout.study() {
        val c = this@SettingsActivity
        label("Daily goal")
        group(num("Study goal", "study_goal_min", 360, 30, 1000, "min"))
        label("Timer")
        group(num("Pomodoro focus", "pomo_work", 25, 5, 120, "min"), num("Pomodoro break", "pomo_break", 5, 1, 60, "min"),
            sw("Auto-pause outside Study apps", "Requires Usage access; add reading apps under Apps and dock", "timer_auto_pause", true) {
                if (it && !Usage.granted(c)) Dlg.confirm(c, "Allow Usage access", "Usage access is needed to tell study apps from video and music apps.", "Open settings") { Usage.openSettings(c) }
            },
            action("layers", T.teal, "Floating timer permission", if (c.canOverlay()) "Allowed" else "Needed to float over other apps") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        label("Focus mode")
        group(sw("Do Not Disturb during focus", "Needs Do Not Disturb access", "focus_dnd", true) { if (it) try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (e: Exception) { } },
            action("grid", T.amber, "Apps allowed during focus", "${Apps.study().size} study apps") { multiPick("Study apps", "study_apps") })
        label("Distractions")
        group(action("bell", T.rose, "Muted notification apps", "${Notifs.block().size} apps muted (saved to Important log, not deleted)") { multiPick("Mute these apps", "block_apps", Notifs.block()) },
            action("bell", T.sage, "Always-allowed apps", "${Notifs.keep().size} apps") { multiPick("Never mute these", "keep_apps", Notifs.keep()) },
            action("shield", T.slate, "Notification access", "Required for muting") { try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) { } },
            action("clock", T.clay, "Usage access", if (Usage.granted(c)) "Granted" else "Needed for screen-time card") { Usage.openSettings(c) })
    }

    // ---------- Exam ----------
    private fun LinearLayout.exam() {
        val c = this@SettingsActivity
        group(value("Prelims date", null, Store.s("exam_date", "2027-05-23")) { Dlg.date(c, Repo.examDate()) { d -> Store.put("exam_date", d.toString()); redraw() } },
            action("sparkle", T.sage, "Add balanced weekly template", "Five study blocks a day") { Cards.template(c) })
        note("The countdown, lock-screen text and AI context use this date.")
    }

    // ---------- Wallpaper ----------
    private fun LinearLayout.wallpaper() {
        val c = this@SettingsActivity
        label("Automation")
        group(sw("Auto wallpaper", "Sky, light and tint follow the time of day", "wp_sys", true),
            sw("Follow the clock", "Off = freeze at the hour below", "wp_auto", true) { redraw() },
            if (!Store.b("wp_auto", true)) num("Frozen hour", "wp_fixed_hour", 12, 0, 23, "h") else note0("Smooth change, never abrupt"),
            num("Update every", "wp_interval", 30, 15, 240, "min", "Longer = fewer changes, saves battery"),
            action("image", T.slate, "Apply now", "Renders at full resolution") { toast("Rendering..."); Thread { val ok = Wallpaper.apply(c, true); runOnUiThread { toast(if (ok) "Wallpaper applied" else "Could not set wallpaper") } }.start() })
        label("Scene")
        group(choice("Scene", "wp_scene", "hills", listOf("hills" to "Misty hills", "dunes" to "Soft dunes", "forest" to "Pine forest", "ocean" to "Ocean horizon", "waves" to "Calm waves", "gradient" to "Aurora gradient", "paper" to "Warm paper", "amoled" to "Pure dark (OLED)", "photo" to "My photos")) { },
            choice("Apply to", "wp_target", "both", listOf("both" to "Home and lock", "home" to "Home only", "lock" to "Lock only")))
        label("Eye comfort")
        group(sw("Warm tint at night", "Reduces blue light in the evening", "wp_comfort", true), sw("Film grain", "Prevents colour banding on gradients", "wp_grain", true), sw("Seasonal touches", "Gentle changes through the year", "wp_season", true),
            slider("Dim", "wp_dim", 6, 40), sw("Parallax with pages", "Wallpaper drifts as you swipe", "wp_parallax", true))
        label("Smart pausing")
        group(sw("Pause while focusing or timing", "Nothing changes during study", "wp_pause_focus", true), sw("Pause in battery saver", null, "wp_battery_pause", true),
            sw("Countdown on lock screen", "Shows days to Prelims", "wp_lock_text", true))
        label("My photos")
        group(action("plus", T.sage, "Add photo", "${Wallpaper.photos(c).size} saved") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_PHOTO) },
            choice("Rotation", "wp_photo_mode", "daily", listOf("daily" to "New photo daily", "hourly" to "New photo hourly", "single" to "First photo only")),
            slider("Blur", "wp_blur", 0, 24, ""))
    }

    // ---------- Home screen ----------
    private fun LinearLayout.homeScreen() {
        val c = this@SettingsActivity
        group(action("sliders", T.olive, "Edit home layout", "Move, add, remove cards and pages") { setResult(RESULT_OK); startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("edit", true).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)); finish() })
        note("Tip: tap the sliders icon on the home header or long-press the page dots to edit. Long-press on an app never creates a pin; pins are set under Apps and dock.")
    }

    // ---------- Apps ----------
    private fun LinearLayout.apps() {
        group(action("grid", T.clay, "Dock apps", Apps.pins().mapNotNull { Apps.byPkg(it)?.label }.joinToString(", ").ifBlank { "None" }) { multiPick("Dock apps (max 5)", "pins", max = 5) },
            action("close", T.text2, "Hidden apps", "${Apps.hidden().size} hidden. Shown under the Hidden tab in the drawer") { multiPick("Hide from drawer", "hidden_apps") },
            action("book", T.sage, "Study apps", "${Apps.study().size} apps in Study tab and allowed in focus") { multiPick("Study apps", "study_apps") })
        note("Recent apps are never shown on the home screen.")
    }

    // ---------- Appearance ----------
    private fun LinearLayout.appearance() {
        val c = this@SettingsActivity
        group(choice("Theme", "theme", "auto", listOf("auto" to "Follow system", "light" to "Light (warm paper)", "dark" to "Dark (soft)", "schedule" to "Dark on a schedule")) { T.init(c.applicationContext); recreate() },
            num("Dark from", "dark_from", 19, 0, 23, "h", "Used when theme is a schedule") { T.init(c.applicationContext) }, num("Light from", "dark_to", 6, 0, 23, "h") { T.init(c.applicationContext) },
            num("Drawer columns", "drawer_cols", 0, 0, 9, "", "0 = automatic"), num("Drawer icon size", "drawer_icon", 54, 40, 80, "dp"), sw("Drawer app names", null, "drawer_labels", true),
            slider("Text size", "text_scale", 100, 130) { T.init(c.applicationContext); recreate() }, slider("Interface size", "ui_scale", 100, 130) { T.init(c.applicationContext); recreate() })
        note("Soft warm neutrals with sage and clay accents; no strong blues. Inter is bundled so text renders the same on every device.")
    }

    // ---------- Security ----------
    private fun LinearLayout.security() {
        val c = this@SettingsActivity
        label("PIN")
        group(action("lock", T.plum, if (Pin.isSet()) "Change PIN" else "Set PIN", "4 to 8 digits") { setPin(false) },
            if (Pin.isSet()) action("trash", T.danger, "Remove PIN", null) { verifyThen { Pin.clear(); redraw() } } else note0("No PIN set"))
        if (Pin.isSet()) { label("Where it applies")
            group(sw("Ask when opening the launcher", "Otherwise only for Settings", "pin_home", false), sw("Allow fingerprint / face", null, "pin_bio", true), num("Stay unlocked for", "pin_window", 10, 0, 120, "min", "0 = ask every time"))
            note("Five wrong tries lock the keypad for 30 seconds, growing with each further miss.") }
    }
    private fun verifyThen(f: () -> Unit) {
        if (!Pin.isSet() || Pin.unlocked()) { f(); return }
        val root = window.decorView as android.view.ViewGroup; lateinit var pad: PinPad
        pad = PinPad(this, "Enter current PIN", Pin.len(), true, { code, p -> if (Pin.check(code)) { root.removeView(p); f() } else p.error(if (Pin.lockLeft() > 0) "Locked ${Pin.lockLeft()}s" else "Wrong PIN") }, { root.removeView(pad) })
        root.addView(pad, android.view.ViewGroup.LayoutParams(-1, -1))
    }
    private fun setPin(confirmed: Boolean) {
        verifyThen { Dlg.choose(this, "PIN length", listOf("4 digits", "6 digits", "8 digits")) { i ->
            val len = intArrayOf(4, 6, 8)[i]; val root = window.decorView as android.view.ViewGroup; var first = ""; lateinit var pad: PinPad
            pad = PinPad(this, "Choose a $len-digit PIN", len, true, { code, p ->
                if (first.isEmpty()) { first = code; p.reset("Repeat the PIN") } else if (first == code) { Pin.set(code); root.removeView(p); toast("PIN saved"); redraw() } else { first = ""; p.error("Did not match. Start again"); p.reset("Choose a $len-digit PIN") } }, { root.removeView(pad) })
            root.addView(pad, android.view.ViewGroup.LayoutParams(-1, -1)) } }
    }

    // ---------- Backup ----------
    private fun LinearLayout.backup() {
        val c = this@SettingsActivity; val last = Store.l("backup_last_ms")
        label("Automatic")
        group(sw("Daily backup", "Everything: tasks, notes, water, sessions, settings", "backup_on", true),
            action("cloud", T.teal, "Back up now", if (last > 0) "Last: " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.ENGLISH).format(java.util.Date(last)) + " (" + Store.s("backup_where") + ")" else "Never") { Thread { val m = Backup.runNow(c); runOnUiThread { toast(m); redraw() } }.start() })
        label("Google Drive (recommended)")
        val bf = Store.s("backup_file")
        group(action("cloud", T.sage, if (bf.isBlank()) "Choose backup file in Drive" else "Change backup file", if (bf.isBlank()) "Pick Google Drive in the next screen, then Save. The same file is updated every day." else "Linked: " + (Uri.parse(bf).lastPathSegment ?: "file")) { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "mission-backup.json").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQ_FILE) },
            if (bf.isNotBlank()) action("close", T.danger, "Stop Drive file backups", null) { Store.put("backup_file", null); redraw() } else note0("Google Drive does not allow folder picking, so one file is used."))
        label("Folder (device, SD card)")
        val tree = Store.s("backup_tree")
        group(action("cloud", T.sage, if (tree.isBlank()) "Choose Drive folder" else "Change Drive folder", if (tree.isBlank()) "Pick a folder inside Google Drive in the next screen" else Uri.parse(tree).lastPathSegment) { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION), REQ_TREE) },
            if (tree.isNotBlank()) action("close", T.danger, "Stop Drive backups", null) { Store.put("backup_tree", null); redraw() } else note0("Uses your signed-in Google account through the system picker. No keys are stored."))
        note("Android Auto Backup also copies the launcher data to your Google account when phone backup is on.")
        label("Files")
        group(action("up", T.slate, "Export to file", "Save a copy anywhere") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE, "mission-backup-${LocalDate.now(Repo.zone())}.json"), REQ_EXPORT) },
            action("up", T.slate, "Export study hours (CSV)", "Opens in Excel or Sheets") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").putExtra(Intent.EXTRA_TITLE, "study-hours-${LocalDate.now(Repo.zone())}.csv"), REQ_CSV) })
        label("Local copies")
        val files = Backup.localFiles(c); if (files.isEmpty()) group(note0("None yet"))
        else group(note0("${files.size} local copies. Restore only from Settings > Reset and data."))
    }

    // ---------- Reset and data ----------
    private fun LinearLayout.resetData() {
        val c = this@SettingsActivity
        if (!ResetGate.isSet()) {
            label("Reset password")
            note("Set a 6 to 12 digit password. It is stored as a salted PBKDF2 hash and is never included in backups.")
            ResetGate.ensureSet(c) { redraw() }
            return
        }

        note("Every deletion first saves an automatic backup. A reset password is required for each action.")
        label("Per-cache deletion")
        group(
            action("timer", T.amber, "Study sessions", "${Store.rows("sessions").size} saved sessions") {
                gatedReset("Delete study sessions?") { Store.clear("sessions") }
            },
            action("checkc", T.sage, "Syllabus ticks", "Clear all manual syllabus progress") {
                gatedReset("Delete all syllabus ticks?") { Store.setStringList("syl_done", emptyList()) }
            },
            action("drop", T.teal, "Water today", "${Water.todayEntries().size} entries") {
                gatedReset("Delete today's water entries?") {
                    Water.todayEntries().forEach { Water.remove(it.optString("id")) }
                    Water.clear(c); Water.arm(c)
                }
            },
            action("sparkle", T.plum, "AI chat", "${Ai.history().size} saved messages") {
                gatedReset("Delete AI chat history?") { Ai.clearHistory() }
            },
            action("calendar", T.terra, "Schedule", "${Store.rows("schedule").size} schedule blocks") {
                gatedReset("Delete the schedule?") { Store.clear("schedule") }
            },
            action("refresh", T.slate, "Zite cache", "Remove locally cached Zite data") {
                gatedReset("Delete the Zite cache?") {
                    Store.saveObj("cache_zite", org.json.JSONObject())
                    Zite.invalidate()
                }
            },
            action("image", T.slate, "Wallpaper photos", "${Wallpaper.photos(c).size} saved photos") {
                gatedReset("Delete saved wallpaper photos?") { Wallpaper.clearPhotos(c) }
            },
            action("grid", T.olive, "Home layout", "Reset app pages; keep Android widgets") {
                gatedReset("Reset app pages?") { HomeLayout.reset() }
            },
            action("grid", T.slate, "Remove Android widget", "${HomeLayout.widgetIds().size} placed") { removeHomeWidget() }
        )

        label("Restore")
        group(action("down", T.slate, "Import backup", "Choose a file; current data is backed up first") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_IMPORT)
        })
        val files = Backup.localFiles(c)
        if (files.isNotEmpty()) group(*files.map { file ->
            action("refresh", T.sage, file.name.removePrefix("mission-backup-").removeSuffix(".json"), "${file.length() / 1024} KB") {
                gatedReset("Restore ${file.name}?") {
                    val result = Backup.restoreLocal(c, file)
                    if (result < 0) throw IllegalStateException("Backup restore failed")
                    Zite.invalidate()
                }
            }
        }.toTypedArray())

        label("Final option")
        group(action("trash", T.danger, "Erase all launcher data", "Last resort; cannot be undone") {
            gatedReset("Erase all launcher data?") {
                TimerService.stop(c)
                Water.clear(c)
                Sched.cancelWater(c)
                Wallpaper.clearPhotos(c)
                SecureStore.eraseAll()
                Store.eraseAll()
                Zite.invalidate()
                Store.put("water_on", false)
                Store.flushNow()
            }
        })
    }

    private fun gatedReset(title: String, operation: () -> Unit) {
        Dlg.confirm(this, title, "A local backup is saved before changes are made.", "Continue") {
            ResetGate.verifyAndBackup(this) {
                try {
                    operation()
                    Store.changed()
                    toast("Backup saved. Reset complete.")
                    redraw()
                } catch (e: Exception) {
                    Log.e("reset-data", e)
                    toast("Reset failed: ${e.message ?: "unknown error"}")
                }
            }
        }
    }

    private fun removeHomeWidget() {
        val c = this
        val ids = HomeLayout.widgetIds()
        if (ids.isEmpty()) { toast("No Android widgets are placed"); return }
        val manager = AppWidgetManager.getInstance(c)
        val labels = ids.map { id ->
            manager.getAppWidgetInfo(id)?.loadLabel(packageManager)?.toString() ?: "Android widget #$id"
        }
        Dlg.choose(c, "Select a widget to remove", labels) { index ->
            val id = ids[index]
            gatedReset("Remove ${labels[index]}?") {
                check(HomeLayout.removeWidget(id)) { "Widget is no longer on the home screen" }
                AppWidgetHost(c, 1024).deleteAppWidgetId(id)
            }
        }
    }

    // ---------- Permissions ----------
    private fun LinearLayout.permissions() {
        val c = this@SettingsActivity
        fun st(b: Boolean) = if (b) "Granted" else "Not granted"
        group(action("layers", T.teal, "Display over other apps", st(c.canOverlay()) + " - floating timer and water card") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
            action("clock", T.clay, "Usage access", st(Usage.granted(c)) + " - screen-time card") { Usage.openSettings(c) },
            action("bell", T.rose, "Notification access", "Mute distractions, keep important alerts") { try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) { } },
            action("shield", T.plum, "Do Not Disturb access", "For focus mode") { try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (e: Exception) { } },
            action("bell", T.amber, "App notifications", "Water, timer, backups") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) },
            action("refresh", T.sage, "Alarms and reminders", "Exact water reminders") { try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) } catch (e: Exception) { } },
            action("bolt", T.gold, "Battery: do not optimise", "Keeps hourly sync and reminders on time (Xiaomi: also set Autostart and No restrictions)") { try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (e: Exception) { } },
            action("grid", T.slate, "Set as default home app", "Choose Mission as your launcher") { try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) { } })
        note("Xiaomi HyperOS may stop background apps: open Security > Apps > Manage apps > Mission > Autostart on, Battery saver: No restrictions.")
    }

    // ---------- About ----------
    private fun LinearLayout.about() {
        val c = this@SettingsActivity
        group(value("Version", null, try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }) { },
            value("Data stored", "On this device (app storage)", "${Store.rows("sessions").size} sessions") { },
            action("info", T.text2, "Show recent error log", "For troubleshooting") { Dlg.info(c, "Log", Log.dump().ifBlank { "No errors" }) })
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, d: Intent?) {
        super.onActivityResult(req, res, d); val u = d?.data
        if (res != RESULT_OK || u == null) return
        when (req) {
            REQ_TREE -> { Backup.setTree(this, u); Thread { val m = Backup.runNow(this); runOnUiThread { toast(m); redraw() } }.start() }
            REQ_FILE -> { Backup.setFile(this, u); Thread { val m = Backup.runNow(this); runOnUiThread { toast(m); redraw() } }.start() }
            REQ_CSV -> toast(if (try {
                val output = contentResolver.openOutputStream(u, "wt") ?: throw IllegalStateException("Could not open CSV destination")
                output.use { it.write(Repo.csv().toByteArray()) }
                true
            } catch (e: Exception) { Log.e("csv-export", e); false }) "Exported" else "Export failed")
            REQ_EXPORT -> toast(if (Backup.export(this, u)) "Exported" else "Export failed")
            REQ_IMPORT -> gatedReset("Import this backup?") {
                val n = Backup.import(this, u, false)
                if (n < 0) throw IllegalArgumentException("The selected file is not a valid backup")
                Zite.invalidate()
            }
            REQ_PHOTO -> { toast(if (Wallpaper.addPhoto(this, u)) "Photo added" else "Could not read photo"); redraw() }
        }
    }

    // ---------- Setup checklist ----------
    private class Item(val title: String, val sub: String, val ok: Boolean, val fix: () -> Unit)
    private fun setupItems(): List<Item> {
        val c = this
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val notif = android.os.Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val listener = try { (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName) } catch (e: Exception) { false }
        val dnd = try { nm().isNotificationPolicyAccessGranted } catch (e: Exception) { false }
        val home = try { packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName == packageName } catch (e: Exception) { false }
        fun open(a: String, data: Boolean = false) { try { startActivity(Intent(a).apply { if (data) setData(Uri.parse("package:$packageName")) }) } catch (e: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (x: Exception) { } } }
        return listOf(
            Item("Default home app", "Press Home and choose Mission, Always", home) { open(Settings.ACTION_HOME_SETTINGS) },
            Item("Notifications", "Water, timer, briefs", notif) { if (android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 5) else open(Settings.ACTION_APP_NOTIFICATION_SETTINGS) },
            Item("Display over other apps", "Floating timer and water card", c.canOverlay()) { open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) },
            Item("Usage access", "Screen-time card and app limits", Usage.granted(c)) { Usage.openSettings(c) },
            Item("Notification access", "Mute distractions", listener) { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
            Item("Accessibility service", "Circle to Search, double-tap lock, shade swipe", CircleService.running()) { CircleService.openSettings(c) },
            Item("Do Not Disturb access", "Focus mode", dnd) { open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
            Item("Battery: no restrictions", "Hourly sync and reminders on time", pm.isIgnoringBatteryOptimizations(packageName)) { open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true) },
            Item("Xiaomi Autostart", "Cannot be detected: open and switch Mission on", false) {
                val tries = listOf(Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"), Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
                var ok = false; for (i in tries) { try { startActivity(i); ok = true; break } catch (e: Exception) { } }
                if (!ok) open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) })
    }
    private fun LinearLayout.setup() {
        val items = setupItems()
        note("Tap any row marked Fix. This screen refreshes when you come back.")
        group(*items.map { it -> rowBase(null, 0, it.title, it.sub, text(if (it.ok) "On" else "Fix", 13.5f, 3, if (it.ok) T.sage else T.terra)) { it.fix() } }.toTypedArray())
    }
    override fun onResume() { super.onResume(); if (stack.isNotEmpty()) redraw() }

    // ---------- Gestures ----------
    private fun LinearLayout.gestures() {
        label("Home gestures")
        group(choice("Double tap on home", "g_dtap", "lock", Gestures.options), choice("Swipe down on home", "g_down", "notif", Gestures.options), choice("Long-press empty area", "g_long", "edit", Gestures.options), choice("Swipe up on dock", "g_up", "drawer", Gestures.options))
        note("Lock screen and notification gestures use the accessibility service. Turn it on under Circle to Search.")
        label("Multitasking")
        group(choice("Open apps from drawer in", "open_mode", "normal", listOf("normal" to "Full screen", "float" to "Floating window", "split" to "Split / adjacent"), "Long-press an app in the drawer to pick per app"))
        note("Apps are started through the system launcher API, which keeps your other floating windows alive. The full-screen swipe-home gesture is drawn by HyperOS; Mission keeps system bars transparent and never adds its own gesture-exclusion zone.")
    }

    // ---------- Circle to Search ----------
    private fun LinearLayout.circle() {
        val c = this@SettingsActivity
        group(action("shield", T.plum, "Accessibility service", if (CircleService.running()) "Running" else "Off - tap, find Mission, switch on") { CircleService.openSettings(c) },
            action("search", T.plum, "Try it now", "Freezes the screen so you can circle something") { Handler(Looper.getMainLooper()).postDelayed({ CircleService.capture(c) }, 400); moveTaskToBack(true) })
        label("Ways to start")
        note("1. Quick Settings tile: edit tiles and add Circle to Search.\n2. Accessibility button in the navigation bar (Settings > Accessibility > Mission > shortcut).\n3. Home gesture mapped under Gestures (double tap, long-press, swipe).\n4. Tools card on the Study page.\n\nThe circled area is sent to Google Lens (or any app you pick). Screens that block screenshots (banking, DRM video) cannot be captured. Xiaomi's own navigation-bar Circle to Search keeps working with its default launcher only.")
    }

    // ---------- Limits ----------
    private fun LinearLayout.limits() {
        val c = this@SettingsActivity; val lim = Guard.limits()
        label("Mindful pause")
        group(sw("Pause before distracting apps", "Short countdown and reminder of days left", "mindful", true), num("Pause length", "mindful_secs", 5, 3, 20, "sec"),
            action("grid", T.rose, "Distraction apps", "${Notifs.block().size} apps (also muted when notifying)") { multiPick("Distraction apps", "block_apps", Notifs.block()) })
        label("Daily limits")
        val keys = lim.keys().asSequence().toList()
        if (keys.isEmpty()) group(note0("No limits yet")) else group(*keys.map { k -> value(Apps.byPkg(k)?.label ?: k, null, "${lim.optInt(k)} min") { Dlg.input(c, "Limit (minutes, 0 removes)", "", lim.optInt(k).toString(), true) { v -> Guard.setLimit(k, v.toIntOrNull() ?: 0); redraw() } } }.toTypedArray())
        group(action("plus", T.sage, "Add a limit", "Choose an app") { val l = Apps.list; Dlg.choose(c, "App", l.map { it.label }) { i -> Dlg.input(c, "Daily minutes for " + l[i].label, "e.g. 30", "", true) { v -> v.toIntOrNull()?.let { Guard.setLimit(l[i].pkg, it) }; if (!Usage.granted(c)) Usage.openSettings(c); redraw() } } })
        note("Limits are checked when you open an app from Mission. Usage access is required.")
    }

    // ---------- Briefs ----------
    private fun LinearLayout.briefs() {
        group(sw("Morning brief", "Days left, today's tasks, revisions", "brief_m_on", true), num("Morning hour", "brief_m_hour", 6, 0, 23, "h"),
            sw("Evening check-in", "Hours so far and open tasks", "brief_e_on", true), num("Evening hour", "brief_e_hour", 21, 0, 23, "h"),
            sw("Sunday weekly report", "Total hours, best day, water streak", "brief_w_on", true))
        note("Delivered by the hourly background tick, so they arrive within the hour you choose.")
    }
}
