package com.mission.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

interface Host {
    val act: Activity
    fun refresh()
    fun openChat(prefill: String? = null)
    fun openSettings(path: String? = null)
    fun openDrawer()
    fun toggleEdit()
    fun pickSystemWidget()
}

fun View.whileAttached(periodMs: Long, f: () -> Unit) {
    val h = Handler(Looper.getMainLooper())
    val r = object : Runnable { override fun run() { f(); h.postDelayed(this, periodMs) } }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { h.removeCallbacks(r); h.post(r) }
        override fun onViewDetachedFromWindow(v: View) { h.removeCallbacks(r) }
    })
}

object Cards {
    class Meta(val title: String, val icon: String, val wide: Boolean = false, val h: Int = 220)
    val catalog = linkedMapOf(
        "header" to Meta("Clock and shortcuts", "clock", true, 110), "countdown" to Meta("Exam countdown", "target", true, 230),
        "upcoming" to Meta("Upcoming (study)", "calendar", false, 240), "plan" to Meta("Today's plan", "checkc", false, 260),
        "water" to Meta("Water", "drop", false, 300), "weekly" to Meta("Weekly schedule", "layers", false, 260),
        "important" to Meta("Important", "bell", false, 150), "revision" to Meta("Revise today", "refresh", false, 160),
        "focus" to Meta("Focus and goal", "bolt", false, 240), "timer" to Meta("Study timer", "timer", false, 250),
        "history" to Meta("Study history", "chart", false, 300), "syl3d" to Meta("Prelims syllabus (3D)", "grid", false, 520),
        "prelims" to Meta("Prelims insight", "trend", false, 380),
        "mockstats" to Meta("Mock results", "target", false, 340), "zite" to Meta("Zite sync", "refresh", false, 150),
        "ai" to Meta("AI assistant", "sparkle", false, 200), "notes" to Meta("Notes", "note", false, 200),
        "usage" to Meta("Screen time", "clock", false, 260), "links" to Meta("Quick links", "link", false, 170),
        "nexttopic" to Meta("Next topics to study", "bolt", false, 260), "tools" to Meta("Tools", "sliders", false, 200))

    fun make(h: Host, id: String): View? = try {
        val c = h.act
        when (id) {
            "header" -> header(h); "countdown" -> countdown(c); "upcoming" -> upcoming(h); "plan" -> plan(h); "water" -> water(h); "weekly" -> weekly(h)
            "important" -> important(h); "revision" -> revision(h); "focus" -> focus(h); "timer" -> timer(h); "history" -> history(h); "syl3d" -> syl3d(h)
            "prelims" -> prelims(h); "mockstats" -> mockStats(h); "zite" -> ziteCard(h); "ai" -> aiCard(h); "notes" -> notes(h); "usage" -> usage(h); "links" -> links(h); "nexttopic" -> nextTopic(h); "tools" -> tools(h)
            else -> null
        }
    } catch (e: Throwable) { Log.e("card-$id", e); errorCard(h.act, id, e) }

    private fun errorCard(c: Context, id: String, e: Throwable): View = Card(c).apply { addView(c.text("Card '$id' could not load", 13f, 2, T.danger)); addView(c.text(e.javaClass.simpleName + ": " + (e.message ?: ""), 11f, 0, T.text3), lp(-2, -2, 4)) }

    private fun greeting(): String { val h = LocalTime.now(Repo.zone()).hour; return when { h < 5 -> "Late night"; h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; h < 21 -> "Good evening"; else -> "Wind down" } }
    private fun pct(f: Float) = "${(f * 100).toInt()}%"

    // ---------------- header ----------------
    private fun header(h: Host): View {
        val c = h.act; val box = c.vbox(); box.padDp(4, 6, 4, 4)
        val row = c.hbox()
        val clock = c.text("", 56f, 0, T.text).tabular(); clock.letterSpacing = -0.03f
        row.addView(clock); row.addView(c.flex())
        row.addView(c.iconBtn("search") { h.openDrawer() }, lpm(-2, -2, 0, 0, 8, 0))
        row.addView(c.iconBtn("sparkle", T.plum) { h.openChat() }, lpm(-2, -2, 0, 0, 8, 0))
        row.addView(c.iconBtn("sliders") { h.toggleEdit() }, lpm(-2, -2, 0, 0, 8, 0))
        row.addView(c.iconBtn("gear") { h.openSettings() })
        box.addView(row)
        val date = c.text("", 15f, 1, T.text2); box.addView(date, lp(-2, -2, 2))
        box.whileAttached(15000) { val n = java.time.LocalDateTime.now(Repo.zone()); clock.text = "%02d:%02d".format(n.hour, n.minute); date.text = n.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ", " + n.dayOfMonth + " " + n.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "  -  " + greeting() }
        return box
    }

    // ---------------- countdown ----------------
    private fun countdown(c: Activity): View {
        val card = Card(c, 28); card.padDp(22, 20, 22, 20)
        card.addView(c.cardHeader("target", "UPSC Prelims 2027", T.terra))
        val days = c.text("", 58f, 3, T.text).tabular(); val unit = c.text("days", 16f, 1, T.text3); val hms = c.text("", 20f, 2, T.text2).tabular()
        val r = c.hbox(); r.gravity = Gravity.BOTTOM; r.addView(days); r.addView(unit, lpm(-2, -2, 8, 0, 0, 10)); card.addView(r, lp(-2, -2, 10))
        card.addView(hms, lp(-2, -2, 2))
        val d = Repo.examDate(); card.addView(c.text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + ", " + d.dayOfMonth + " " + d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.year + "  -  Paper 1, 9:30 AM", 12.5f, 1, T.text3), lp(-2, -2, 6))
        val today = LocalDate.now(Repo.zone())
        val z = Zite.data(c); val left = z.mocks.count { it.date?.isBefore(today) == false }
        val stats = c.hbox(); card.addView(stats, lp(-1, -2, 14))
        fun stat(v: String, l: String, col: Int) { stats.addView(c.statBlock(v, l, col), LinearLayout.LayoutParams(0, -2, 1f)) }
        val wk = java.time.temporal.ChronoUnit.WEEKS.between(today, d)
        stat("$wk", "weeks left", T.text); stat("$left", "mocks ahead", T.terra); stat("${Repo.streak()}d", "study streak", T.sage); stat(fmtMins(Repo.minutesOn(today)), "studied today", T.amber)
        card.whileAttached(1000) { val s = Repo.examSecondsLeft(); days.text = (s / 86400).toString(); hms.text = "%dh %02dm %02ds".format(s % 86400 / 3600, s % 3600 / 60, s % 60) }
        return card
    }

    // ---------------- upcoming ----------------
    private fun upcoming(h: Host): View {
        val c = h.act; val card = Card(c)
        card.addView(c.cardHeader("calendar", "Upcoming", T.plum, c.iconBtn("plus", T.text2, 32) { Dlg.addEvent(c) }))
        val items = Upcoming.items(c, 14)
        if (items.isEmpty()) card.addView(c.emptyNote("No tests or study events ahead"), lp(-2, -2, 6))
        fun row(u: Up): View = c.hbox().apply { padDp(0, 9, 0, 9)
            addView(View(c).apply { background = T.round(u.color, 5) }, LinearLayout.LayoutParams(T.dp(8), T.dp(8)))
            val col = c.vbox(); col.addView(c.text(u.title, 13.5f, 2, T.text, 1)); if (u.sub.isNotBlank()) col.addView(c.text(u.sub, 11.5f, 0, T.text3, 1), lp(-2, -2, 1))
            addView(col, lpm(0, -2, 12, 0, 8, 0, 1f)); addView(c.text(Upcoming.label(u.date).substringBefore(" - ") + if (u.time.isNotBlank()) " ${u.time}" else "", 11.5f, 1, if (u.date == LocalDate.now(Repo.zone())) T.terra else T.text2)) }
        items.take(4).forEachIndexed { i, u -> card.addView(row(u), lp(-1, -2, if (i == 0) 6 else 0)) }
        if (items.size > 4) card.addView(Collapsible(c, "${items.size - 4} more", "", false, "up_more") { items.drop(4).forEach { addView(row(it)) } })
        return card
    }

    // ---------------- plan ----------------
    private fun plan(h: Host): View {
        val c = h.act; val card = Card(c); val today = LocalDate.now(Repo.zone())
        val ts = Repo.tasksFor(today)
        val done = ts.count { it.optBoolean("done") }
        card.addView(c.cardHeader("checkc", "Today's plan", T.sage, c.text("$done/${ts.size}", 12.5f, 2, T.text3)))
        fun row(t: JSONObject): View = c.hbox().apply { padDp(0, 8, 0, 8)
            val ic = IconView(c, if (t.optBoolean("done")) "checkc" else "circle", if (t.optBoolean("done")) T.sage else T.text3, 22, 1.8f); addView(ic)
            addView(c.text(t.optString("text"), 14f, 1, if (t.optBoolean("done")) T.text3 else T.text).apply { if (t.optBoolean("done")) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG }, lpm(0, -2, 10, 0, 0, 0, 1f))
            if (t.optString("time").isNotBlank()) addView(c.text(t.optString("time"), 11.5f, 1, T.text3))
            tap { Repo.toggleTask(t.optString("id")) } }
        if (ts.isEmpty()) card.addView(c.emptyNote("Nothing planned. Add the first task."), lp(-2, -2, 4))
        ts.take(5).forEachIndexed { i, t -> card.addView(row(t), lp(-1, -2, if (i == 0) 6 else 0)) }
        if (ts.size > 5) card.addView(Collapsible(c, "${ts.size - 5} more tasks", "", false, "plan_more") { ts.drop(5).forEach { addView(row(it)) } })
        if (ts.any { !it.optBoolean("done") }) card.addView(c.pill("Move undone to tomorrow", "chev", false) { val n = Repo.carryOver(); c.toast("$n tasks moved") }, lp(-2, -2, 6))
        val e = Dlg.field(c, "Add task for today"); e.setSingleLine(); e.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        e.setOnEditorActionListener { _, _, _ -> val s = e.text.toString().trim(); if (s.isNotEmpty()) { Repo.addTask(s); e.setText("") }; true }
        card.addView(e, lp(-1, -2, 8)); return card
    }

    // ---------------- water ----------------
    private fun water(h: Host): View {
        val c = h.act; val card = Card(c); val have = Repo.waterToday(); val goal = Water.goal()
        card.addView(c.cardHeader("drop", "Water", T.teal, c.text(if (Water.pending()) "reminder active" else "streak ${Water.streak()}d", 11.5f, 2, if (Water.pending()) T.terra else T.text3)))
        val top = c.hbox(); top.addView(Ring(c, have.toFloat() / goal, T.teal, "${have * 100 / goal}%", "$have ml"), LinearLayout.LayoutParams(T.dp(112), T.dp(112)))
        val side = c.vbox(); side.addView(c.text("$have / $goal ml", 18f, 3).tabular())
        val b = Water.behind(); side.addView(c.text(if (have >= goal) "Goal reached today" else if (b > 0) "$b ml behind pace" else "On pace", 12.5f, 1, if (b > 0 && have < goal) T.terra else T.sage), lp(-2, -2, 3))
        side.addView(c.text("Reminders %02d:%02d - %02d:%02d IST, every %d min".format(Water.startTime().hour, Water.startTime().minute, Water.endTime().hour, Water.endTime().minute, Water.interval()), 11.5f, 0, T.text3), lp(-2, -2, 3))
        top.addView(side, lpm(0, -2, 16, 0, 0, 0, 1f)); card.addView(top, lp(-1, -2, 10))
        val cups = Water.cups()
        val chips = c.hbox(); cups.take(4).forEach { ml -> chips.addView(c.pill("+$ml", "drop", ml == cups.getOrElse(1) { 250 }, T.teal) { Water.logFromAction(c, ml) }, lpm(-2, -2, 0, 0, 8, 0)) }
        chips.addView(c.pill("Custom", null, false) { Dlg.input(c, "Add water (ml)", "e.g. 400", "", true, false, "Add") { s -> s.toIntOrNull()?.let { if (it in 10..3000) Water.logFromAction(c, it) } } }, lpm(-2, -2, 0, 0, 8, 0))
        card.addView(c.chipRow(chips), lp(-1, -2, 12))
        card.addView(Collapsible(c, "Today's log", "${Water.todayEntries().size} entries", false, "water_log") {
            val es = Water.todayEntries(); if (es.isEmpty()) addView(c.emptyNote("No water logged yet"))
            es.forEach { e -> addView(c.hbox().apply { padDp(0, 7, 0, 7); addView(c.text(java.time.Instant.ofEpochMilli(e.optLong("t")).atZone(Repo.zone()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }, 12.5f, 1, T.text3).tabular()); addView(c.text("${e.optInt("ml")} ml", 14f, 2), lpm(0, -2, 14, 0, 0, 0, 1f)) }) } })
        card.addView(Collapsible(c, "Last 7 days", "", false, "water_hist") {
            val hs = Water.history(7); addView(StackBars(c, hs.map { it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2) }, hs.map { listOf((if (it.second >= goal) T.teal else T.lighten(T.teal, 0.35f)) to it.second / 1000f) }, "L").apply { heightDp = 130 }) })
        return card
    }

    // ---------------- weekly schedule ----------------
    private fun weekly(h: Host): View {
        val c = h.act; val card = Card(c); val today = LocalDate.now(Repo.zone()).dayOfWeek.value
        card.addView(c.cardHeader("layers", "Weekly schedule", T.slate, c.iconBtn("plus", T.text2, 32) { slotDialog(c) }))
        val now = LocalTime.now(Repo.zone()).let { "%02d:%02d".format(it.hour, it.minute) }
        fun slot(s: JSONObject, hl: Boolean): View = c.hbox().apply { padDp(0, 8, 0, 8)
            val on = hl && now >= s.optString("start") && now < s.optString("end")
            addView(c.text(s.optString("start") + "\n" + s.optString("end"), 11f, 1, if (on) T.sage else T.text3).tabular(), LinearLayout.LayoutParams(T.dp(44), -2))
            addView(View(c).apply { background = T.round(T.subjectColor(s.optString("subject", s.optString("title"))), 3) }, LinearLayout.LayoutParams(T.dp(4), T.dp(30)))
            val col = c.vbox(); col.addView(c.text(s.optString("title"), 14f, 2, T.text, 1)); if (s.optString("subject").isNotBlank()) col.addView(c.text(s.optString("subject"), 11.5f, 0, T.text3), lp(-2, -2, 1))
            addView(col, lpm(0, -2, 12, 0, 0, 0, 1f)); if (on) addView(c.text("now", 11f, 3, T.sage)) }
        val t = Repo.scheduleFor(today)
        if (t.isEmpty()) { card.addView(c.emptyNote("No blocks today."), lp(-2, -2, 4)); card.addView(c.pill("Use a balanced template", "sparkle", false) { template(c) }, lp(-2, -2, 6)) }
        t.forEachIndexed { i, s -> card.addView(slot(s, true), lp(-1, -2, if (i == 0) 6 else 0)) }
        card.addView(Collapsible(c, "Full week", "", false, "week_full") { for (d in 1..7) { addView(c.text(java.time.DayOfWeek.of(d).getDisplayName(TextStyle.FULL, Locale.ENGLISH), 12f, 3, if (d == today) T.sage else T.text2), lp(-2, -2, 8)); val l = Repo.scheduleFor(d); if (l.isEmpty()) addView(c.emptyNote("free")) else l.forEach { addView(slot(it, false)) } } })
        return card
    }
    fun template(c: Activity) {
        val blocks = listOf(Triple("06:00", "08:00", "Prelims GS block") to "Polity & Governance", Triple("09:00", "12:00", "Deep study: weak subject") to "Economy & Finance", Triple("14:30", "17:00", "Optional: Philosophy") to "Philosophy",
            Triple("17:30", "19:00", "Current affairs and PYQ practice") to "Current Affairs", Triple("21:00", "22:00", "Revision and answer writing") to "Revision")
        for (d in 1..7) blocks.forEach { (t, s) -> Store.add("schedule", obj("dow" to d, "start" to t.first, "end" to t.second, "title" to t.third, "subject" to s)) }
        c.toast("Template added - long-press a block to delete")
    }
    fun slotDialog(c: Activity) {
        val box = c.vbox(); val t = Dlg.field(c, "Title (e.g. Polity revision)"); box.addView(t)
        var dow = LocalDate.now(Repo.zone()).dayOfWeek.value; var st = "09:00"; var en = "11:00"
        val subj = Dlg.field(c, "Subject (optional)"); box.addView(subj, lp(-1, -2, 8))
        val d = c.pill("Day: " + java.time.DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, Locale.ENGLISH), "calendar") { }; val s = c.pill("Start: $st", "clock") { }; val e = c.pill("End: $en", "clock") { }
        box.addView(d, lp(-2, -2, 10)); box.addView(s, lp(-2, -2, 8)); box.addView(e, lp(-2, -2, 8))
        (d.getChildAt(2) as TextView).let { tv -> d.setOnClickListener { Dlg.choose(c, "Day", (1..7).map { java.time.DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Locale.ENGLISH) }) { i -> dow = i + 1; tv.text = "Day: " + java.time.DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) } } }
        (s.getChildAt(2) as TextView).let { tv -> s.setOnClickListener { Dlg.time(c) { x -> st = x; tv.text = "Start: $x" } } }
        (e.getChildAt(2) as TextView).let { tv -> e.setOnClickListener { Dlg.time(c) { x -> en = x; tv.text = "End: $x" } } }
        Dlg.view(c, "New schedule block", box, "Add") { if (t.text.isNotBlank()) Store.add("schedule", obj("dow" to dow, "start" to st, "end" to en, "title" to t.text.toString().trim(), "subject" to subj.text.toString().trim())) }
    }

    // ---------------- important / revision ----------------
    private fun important(h: Host): View {
        val c = h.act; val card = Card(c); card.addView(c.cardHeader("bell", "Important", T.amber))
        val items = Store.rows("important").sortedByDescending { it.optLong("t") }.take(3)
        val enabled = try { Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")?.contains(c.packageName) == true } catch (e: Exception) { false }
        if (!enabled) card.addView(c.pill("Allow notification access", "bell", true, T.amber) { try { c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) { } }, lp(-2, -2, 8))
        else if (items.isEmpty()) card.addView(c.emptyNote("Nothing important right now"), lp(-2, -2, 4))
        items.forEach { n -> card.addView(c.vbox().apply { padDp(0, 7, 0, 7); addView(c.text(n.optString("title"), 13.5f, 2, T.text, 1)); addView(c.text(n.optString("text"), 12f, 0, T.text3, 1), lp(-2, -2, 1)) }) }
        val m = Store.rows("muted").size
        card.addView(c.text("$m distracting alerts muted (saved, not deleted)", 11.5f, 0, T.text3), lp(-2, -2, 8)); return card
    }
    private fun revision(h: Host): View {
        val c = h.act; val card = Card(c); val due = Repo.revisionsDue()
        card.addView(c.cardHeader("refresh", "Revise today", T.rose, c.iconBtn("plus", T.text2, 32) { Dlg.input(c, "Topic to revise", "e.g. Fundamental Rights") { if (it.isNotBlank()) Repo.addRevision(it) } }))
        if (due.isEmpty()) card.addView(c.emptyNote("Nothing due today"), lp(-2, -2, 4))
        due.take(4).forEach { r -> card.addView(c.hbox().apply { padDp(0, 8, 0, 8); addView(c.text(r.optString("topic"), 14f, 1, T.text, 2), LinearLayout.LayoutParams(0, -2, 1f)); addView(c.text("R${r.optInt("round") + 1}", 11f, 2, T.text3), lpm(-2, -2, 8, 0, 8, 0)); addView(c.pill("Done", "check", true, T.sage) { Repo.reviseDone(r.optString("id")) }) }, lp(-1, -2, 2)) }
        if (due.size > 4) card.addView(c.text("+${due.size - 4} more due", 12f, 1, T.text3), lp(-2, -2, 4)); return card
    }

    // ---------------- focus + goal ----------------
    private fun focus(h: Host): View {
        val c = h.act; val card = Card(c); val goalMin = Store.i("study_goal_min", 360); val done = Repo.minutesOn(LocalDate.now(Repo.zone())) + (if (TimerState.active()) (TimerState.phaseMs() / 60000).toInt() else 0)
        card.addView(c.cardHeader("bolt", "Focus", T.gold, c.text(if (Focus.active()) "locked" else "", 11.5f, 3, T.terra)))
        val top = c.hbox(); top.addView(Ring(c, done.toFloat() / goalMin, T.gold, fmtMins(done), "of ${fmtMins(goalMin)}").apply { tap { Dlg.input(c, "Daily study goal (minutes)", "e.g. 420", goalMin.toString(), true) { v -> v.toIntOrNull()?.let { Store.put("study_goal_min", it.coerceIn(30, 1000)); h.refresh() } } } }, LinearLayout.LayoutParams(T.dp(100), T.dp(100)))
        val col = c.vbox(); col.addView(c.statBlock("${Repo.streak()} days", "study streak", T.text)); col.addView(c.statBlock(fmtMins(Repo.weekMinutes()), "this week", T.text), lp(-2, -2, 10))
        top.addView(col, lpm(0, -2, 18, 0, 0, 0, 1f)); card.addView(top, lp(-1, -2, 10))
        if (Focus.active()) { val t = c.text("", 20f, 3, T.terra).tabular(); card.addView(t, lp(-2, -2, 10)); card.addView(c.text("Apps are locked until the session ends. Water and calls still work.", 11.5f, 0, T.text3), lp(-2, -2, 4)); card.whileAttached(1000) { val s = Focus.left(); t.text = "%d:%02d left".format(s / 60, s % 60); if (s == 0L) h.refresh() } }
        else { val r = c.hbox(); listOf(25, 50, 90).forEach { m -> r.addView(c.pill("$m min", "bolt", m == 50, T.gold) { Dlg.confirm(c, "Start $m min focus?", "The app drawer stays locked and distractions are muted. You cannot cancel early.", "Start") { Focus.start(c, m, TimerState.subject()); h.refresh() } }, lpm(-2, -2, 0, 0, 8, 0)) }; card.addView(c.chipRow(r), lp(-1, -2, 12)) }
        return card
    }

    // ---------------- timer ----------------
    private fun timer(h: Host): View {
        val c = h.act; val card = Card(c)
        card.addView(c.cardHeader("timer", "Study timer", T.teal))
        val subjects = (Zite.data(c).syl.map { Syl.short(it.name) } + listOf("Philosophy", "Mains writing", "Current Affairs", "Revision", "General")).distinct()
        var subj = TimerState.subject()
        val state = c.text("", 26f, 3).tabular()
        card.addView(state, lp(-2, -2, 10)); card.addView(c.text(if (TimerState.active()) "Floating timer is on screen - drag it anywhere" else "Floats over every app, counts your study hours automatically", 11.5f, 0, T.text3), lp(-2, -2, 2))
        if (!TimerState.active()) {
            val chips = c.hbox(); subjects.forEach { s -> chips.addView(c.pill(s, null, s == subj, T.subjectColor(s)) { subj = s; Store.put("timer_subject", s); h.refresh() }, lpm(-2, -2, 0, 0, 8, 0)) }
            card.addView(c.chipRow(chips), lp(-1, -2, 10))
            card.addView(Segmented(c, listOf("Stopwatch", "Pomodoro ${TimerState.workMin()}/${TimerState.breakMin()}"), if (TimerState.mode() == "pomo") 1 else 0) { Store.put("timer_mode", if (it == 1) "pomo" else "stopwatch") }, lp(-2, -2, 10))
            card.addView(c.pill("Start " + TimerState.subject(), "play", true, T.teal) {
                if (!c.canOverlay()) { Dlg.confirm(c, "Allow floating timer", "Display over other apps is needed for the floating timer. The timer still runs in a notification without it.", "Open settings") { c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))) } }
                TimerState.start(TimerState.subject(), TimerState.mode()); TimerService.start(c); h.refresh() }, lp(-2, -2, 10))
        } else {
            val r = c.hbox()
            r.addView(c.pill(if (TimerState.running()) "Pause" else "Resume", if (TimerState.running()) "pause" else "play", true, T.teal) { if (TimerState.running()) TimerState.pause() else TimerState.resume(); h.refresh() }, lpm(-2, -2, 0, 0, 8, 0))
            r.addView(c.pill("Stop and save", "stop", false) { val result = TimerState.finish(true); TimerService.stop(c); c.toast(result.message); h.refresh() })
            card.addView(r, lp(-2, -2, 12))
        }
        card.whileAttached(1000) { val s = TimerState.phaseMs() / 1000; state.text = if (TimerState.active()) (if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)) + "  " + TimerState.subject() else "Ready" }
        return card
    }

    // ---------------- history (Regain-style stacked graph) ----------------
    private fun history(h: Host): View {
        val c = h.act; val card = Card(c); var range = Store.i("hist_range", 0)
        card.addView(c.cardHeader("chart", "Study history", T.amber))
        val holder = c.vbox(); card.addView(holder, lp(-1, -2, 10))
        fun draw() {
            holder.removeAllViews(); val n = if (range == 0) 7 else 30; val days = (n - 1 downTo 0).map { LocalDate.now(Repo.zone()).minusDays(it.toLong()) }
            val data = Repo.byDaySubject(days); val subjects = data.flatMap { it.keys }.distinct().sortedBy { s -> -data.sumOf { it[s] ?: 0 } }
            val stacks = data.map { m -> subjects.mapNotNull { s -> m[s]?.let { T.subjectColor(s) to it / 60f } } }
            val labels = days.map { if (n == 7) it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2) else if (it.dayOfMonth % 5 == 0) "${it.dayOfMonth}" else "" }
            holder.addView(StackBars(c, labels, stacks).apply { heightDp = 160 })
            val tot = data.sumOf { it.values.sum() }; holder.addView(c.text("Total ${fmtMins(tot)}  -  avg ${fmtMins(tot / n)} / day", 12.5f, 2, T.text2), lp(-2, -2, 6))
            val leg = c.hbox(); subjects.take(8).forEach { s -> leg.addView(View(c).apply { background = T.round(T.subjectColor(s), 4) }, LinearLayout.LayoutParams(T.dp(8), T.dp(8))); leg.addView(c.text(" $s ${fmtMins(data.sumOf { it[s] ?: 0 })}", 11.5f, 1, T.text3), lpm(-2, -2, 2, 0, 12, 0)) }
            if (subjects.isEmpty()) holder.addView(c.emptyNote("Start the study timer or a focus session to see your hours here")) else holder.addView(c.chipRow(leg), lp(-1, -2, 8))
        }
        card.addView(Segmented(c, listOf("7 days", "30 days"), range) { range = it; Store.put("hist_range", it); draw() }, lp(-2, -2, 10)); draw()
        card.addView(Collapsible(c, "Sessions", "", false, "sess") { val rows = Store.rows("sessions").sortedByDescending { it.optLong("start") }.take(25)
            if (rows.isEmpty()) addView(c.emptyNote("No sessions yet"))
            rows.forEach { s -> val d = Repo.dayOf(s.optLong("start")); addView(c.hbox().apply { padDp(0, 7, 0, 7); addView(View(c).apply { background = T.round(T.subjectColor(s.optString("subject")), 4) }, LinearLayout.LayoutParams(T.dp(8), T.dp(8))); addView(c.text(s.optString("subject") + "  " + d.dayOfMonth + " " + d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), 13f, 1), lpm(0, -2, 10, 0, 0, 0, 1f)); addView(c.text(fmtMins(s.optInt("mins")), 13f, 2, T.text2)) }) } })
        card.addView(c.pill("Add hours manually", "plus", false) { Dlg.input(c, "Minutes studied today", "e.g. 90", "", true, false, "Add") { m -> m.toIntOrNull()?.let { if (it in 1..1000) { val now = System.currentTimeMillis(); Store.add("sessions", obj("start" to now - it * 60000L, "end" to now, "mins" to it, "subject" to TimerState.subject(), "kind" to "manual")) } } } }, lp(-2, -2, 8))
        return card
    }

    // ---------------- 3D syllabus ----------------
    private fun syl3d(h: Host): View {
        val c = h.act; val card = Card(c, 28); val z = Zite.data(c)
        card.addView(c.cardHeader("grid", "Prelims syllabus", T.slate, c.text(if (z.live) "live from Zite" else "snapshot", 11f, 1, T.text3)))
        val ov = Syl.overall(z)
        val slices = z.syl.map { Donut3D.Slice(Syl.short(it.name), it.micros.size.toFloat().coerceAtLeast(1f), T.subjectColor(it.name), 0.35f + Syl.subjectProgress(z, it) * 0.65f, it) }
        val dn = Donut3D(c).apply { this.slices = slices; centerTop = pct(ov); centerBottom = "covered" }
        card.addView(dn, lp(-1, -2, 8))
        val detail = c.vbox(); card.addView(detail, lp(-1, -2, 4))
        fun legend() { detail.removeAllViews()
            detail.addView(c.text("Tap a slice for micro-topics. Slice brightness = progress.", 11.5f, 0, T.text3))
            detail.addView(HBars(c, z.syl.map { Row(Syl.short(it.name), Syl.subjectProgress(z, it), T.subjectColor(it.name), "${(Syl.subjectProgress(z, it) * 100).toInt()}%", it) }, 28).apply { onRow = { r -> val i = z.syl.indexOf(r.tag as Subj); dn.selected = i; dn.invalidate(); show(c, h, detail, z, r.tag as Subj) { legend() } } }, lp(-1, -2, 6)) }
        dn.onPick = { i -> if (i >= 0) show(c, h, detail, z, z.syl[i]) { legend() } else legend() }
        legend(); return card
    }
    private fun show(c: Activity, h: Host, box: LinearLayout, z: ZData, s: Subj, back: () -> Unit) {
        box.removeAllViews(); val col = T.subjectColor(s.name)
        var dirty = false
        box.addView(c.hbox().apply { addView(View(c).apply { background = T.round(col, 5) }, LinearLayout.LayoutParams(T.dp(10), T.dp(10))); addView(c.text(s.name, 15f, 3), lpm(0, -2, 8, 0, 0, 0, 1f)); addView(c.iconBtn("close", T.text2, 30) { if (dirty) Store.changed(); back() }) })
        val r = z.subjResp[s.name]; val att = (r?.get(0) ?: 0) + (r?.get(1) ?: 0)
        val practice = when {
            att >= 5 -> "practice accuracy ${(r!![0] * 100 / att)}% ($att answers)"
            att > 0 -> "$att answers; accuracy shown after 5"
            else -> "no practice yet"
        }
        box.addView(c.text("${s.micros.size} micro-topics  -  ${s.q} PYQs  -  $practice", 12f, 1, T.text3), lp(-2, -2, 4))
        val dot = mapOf(Syl.St.DONE to T.sage, Syl.St.LEARNING to T.amber, Syl.St.NEW to T.text3)
        box.addView(Collapsible(c, "Micro-topics", "Tap to mark done; untick needs the reset password", true, null) {
            s.micros.sortedByDescending { it.q }.forEach { m -> val st = Syl.microState(z, s.name, m.name)
                val key = s.name + "|" + m.name
                val stateDot = View(c).apply { background = T.round(dot[st] ?: T.text3, 5) }
                addView(c.hbox().apply { padDp(0, 8, 0, 8); addView(stateDot, LinearLayout.LayoutParams(T.dp(9), T.dp(9)))
                    addView(c.text(m.name, 13f, 1, T.text, 2), lpm(0, -2, 10, 0, 8, 0, 1f)); addView(c.text("${m.q} Q", 11.5f, 1, T.text3))
                    tap { toggleSyllabus(c, key, false) { done ->
                        dirty = true
                        val hasAttempts = (z.microResp[key]?.let { it[0] + it[1] > 0 } == true)
                        stateDot.background = T.round(if (done) T.sage else if (hasAttempts) T.amber else T.text3, 5)
                    } }
                    setOnLongClickListener { Dlg.info(c, m.name, m.subs.sortedByDescending { it.q }.joinToString("\n") { (it.name.ifBlank { "General" }) + "  (" + it.q + ")" }); true } }) } })
    }

    private fun toggleSyllabus(c: Activity, key: String, notify: Boolean, onChanged: (Boolean) -> Unit = {}) {
        val wasDone = key in Syl.doneSet()
        val change = {
            Syl.toggle(key)
            onChanged(!wasDone)
            if (notify) Store.changed()
        }
        if (wasDone) ResetGate.verifyAndBackup(c, change) else change()
    }

    // ---------------- prelims insight ----------------
    private fun prelims(h: Host): View {
        val c = h.act; val card = Card(c); val z = Zite.data(c)
        card.addView(c.cardHeader("trend", "Prelims insight", T.terra))
        val att = z.attempts.filter { it.isMock }; val avg = if (att.isEmpty()) 0.0 else att.map { it.acc }.average()
        val c0 = z.subjResp.values.sumOf { it[0] }; val w0 = z.subjResp.values.sumOf { it[1] }; val prac = if (c0 + w0 > 0) c0.toFloat() / (c0 + w0) else 0f
        val rings = c.hbox(); fun rg(p: Float, col: Int, big: String, small: String) { rings.addView(Ring(c, p, col, big, small, 8), LinearLayout.LayoutParams(0, T.dp(92), 1f)) }
        rg(Syl.overall(z), T.slate, pct(Syl.overall(z)), "syllabus"); rg(prac, T.sage, pct(prac), "accuracy"); rg(avg.toFloat(), T.terra, pct(avg.toFloat()), "mock avg")
        card.addView(rings, lp(-1, -2, 12))
        val rows = z.syl.filter { val r = z.subjResp[it.name]; r != null && r[0] + r[1] >= 5 }.map { val r = z.subjResp[it.name]!!; Row(Syl.short(it.name), r[0].toFloat() / (r[0] + r[1]), T.subjectColor(it.name), "${r[0] * 100 / (r[0] + r[1])}%  (${r[0] + r[1]})") }
        card.addView(c.text("Accuracy by subject (minimum 5 answers)", 12f, 2, T.text2), lp(-2, -2, 14))
        if (rows.isEmpty()) card.addView(c.emptyNote("Practise in the PYQ portal and this fills in")) else card.addView(HBars(c, rows, 28), lp(-1, -2, 4))
        card.addView(Collapsible(c, "PYQs by year and subject", "From the Zite PYQ Questions table", false, "pyq_years") {
            val years = z.pyqYears.toSortedMap(compareByDescending { it })
            if (years.isEmpty()) addView(c.emptyNote("Sync Zite to load year-level PYQ counts"))
            years.forEach { (year, subjects) ->
                val total = subjects.values.sum()
                addView(c.hbox().apply {
                    addView(c.text(year.toString(), 13f, 2, T.terra), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(c.text("$total PYQs", 12f, 2, T.text2))
                }, lp(-1, -2, 6))
                subjects.entries.sortedByDescending { it.value }.forEach { (subject, count) ->
                    addView(c.text("$subject - $count", 11.5f, 0, T.text3), lp(-2, -2, 2))
                }
            }
        })
        val skipped = z.subjResp.values.sumOf { it[2] }
        card.addView(c.text("Skipped ${skipped}  -  Wrong ${w0}  -  Correct ${c0}", 11.5f, 1, T.text3), lp(-2, -2, 8)); return card
    }

    // ---------------- mock stats ----------------
    private fun mockStats(h: Host): View {
        val c = h.act; val card = Card(c); val z = Zite.data(c)
        card.addView(c.cardHeader("target", "Mock results", T.terra))
        val att = z.attempts.filter { it.isMock }.sortedBy { it.at } + Store.rows("scores").map { Attempt(it.optString("title", "Mock"), "Manual", Repo.startOf(try { LocalDate.parse(it.optString("date")) } catch (e: Exception) { LocalDate.now(Repo.zone()) }), it.optDouble("score"), 0, 0, 0, 0.0, 0, 0) }
        if (att.isEmpty()) card.addView(c.emptyNote("No mock attempts yet"), lp(-2, -2, 6)) else {
            val last = att.last(); val best = att.maxOf { it.score }; val avg = att.map { it.score }.average()
            card.addView(c.hbox().apply { addView(c.statBlock("%.0f".format(last.score), "latest", T.terra), LinearLayout.LayoutParams(0, -2, 1f)); addView(c.statBlock("%.0f".format(best), "best", T.sage), LinearLayout.LayoutParams(0, -2, 1f)); addView(c.statBlock("%.0f".format(avg), "average", T.text), LinearLayout.LayoutParams(0, -2, 1f)); addView(c.statBlock("${att.size}", "attempts", T.text), LinearLayout.LayoutParams(0, -2, 1f)) }, lp(-1, -2, 10))
            if (att.size >= 2) card.addView(LineChart(c, att.takeLast(8).map { Repo.dayOf(it.at).let { d -> "${d.dayOfMonth}/${d.monthValue}" } }, att.takeLast(8).map { it.score.toFloat() }, T.terra), lp(-1, -2, 8))
            if (last.c + last.w > 0) card.addView(c.text("Latest: ${last.title}  -  ${last.c} right, ${last.w} wrong, ${last.s} skipped  -  ${last.sec / 60} min", 11.5f, 1, T.text3), lp(-2, -2, 6))
        }
        val next = z.mocks.firstOrNull { it.date?.isBefore(LocalDate.now(Repo.zone())) == false }
        if (next != null) card.addView(c.text("Next: ${next.title} on ${next.date}", 12.5f, 2, T.text2), lp(-2, -2, 8))
        card.addView(Collapsible(c, "All attempts", "", false, "att_all") { att.reversed().forEach { a -> addView(c.hbox().apply { padDp(0, 7, 0, 7); addView(c.text(a.title, 12.5f, 1, T.text, 1), LinearLayout.LayoutParams(0, -2, 1f)); addView(c.text("%.1f".format(a.score), 13f, 3, T.terra)) }) } })
        card.addView(c.pill("Add score manually", "plus", false) { Dlg.input(c, "Mock score", "e.g. 112.5", "", false, false, "Save") { s -> s.toDoubleOrNull()?.let { Store.add("scores", obj("date" to Store.today(), "score" to it, "title" to "Mock")) } } }, lp(-2, -2, 8))
        return card
    }

    // ---------------- zite ----------------
    private fun ziteCard(h: Host): View {
        val c = h.act; val card = Card(c); val z = Zite.data(c)
        card.addView(c.cardHeader("refresh", "Zite sync", T.sage, c.text(if (z.live) "live" else "snapshot", 11.5f, 3, if (z.live) T.sage else T.amber)))
        val last = Store.l("zite_last_ok", 0)
        card.addView(c.text(if (!Zite.hasKey()) "Add your Zite API key in Settings > Zite for hourly live sync. Showing the built-in snapshot." else if (last > 0) "Last synced " + java.text.SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH).format(java.util.Date(last)) + ". Auto-updates every hour." else "Not synced yet", 12.5f, 0, T.text2), lp(-2, -2, 6))
        if (Zite.lastError.isNotBlank() || Store.s("zite_last_err").isNotBlank() && last == 0L) card.addView(c.text("Error: " + Store.s("zite_last_err").take(120), 11.5f, 0, T.danger), lp(-2, -2, 4))
        val r = c.hbox(); r.addView(c.pill("Sync now", "refresh", true, T.sage) { if (!Zite.hasKey()) h.openSettings("zite") else { c.toast("Syncing..."); Thread { val m = Zite.sync(c, true); c.runOnUiThread { c.toast(m); h.refresh() } }.start() } }, lpm(-2, -2, 0, 0, 8, 0))
        r.addView(c.pill("Open portal", "link", false) { try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Store.s("zite_portal", Zite.PORTAL)))) } catch (e: Exception) { } })
        card.addView(r, lp(-2, -2, 10)); return card
    }

    // ---------------- AI ----------------
    private fun aiCard(h: Host): View {
        val c = h.act; val card = Card(c); val p = Ai.provider()
        card.addView(c.cardHeader("sparkle", "AI assistant", T.plum, c.text(p.name, 11.5f, 2, T.text3)))
        val last = Ai.history().lastOrNull { it.optString("role") == "model" }
        if (last != null) card.addView(c.text(last.optString("text").take(260), 13f, 0, T.text2, 5), lp(-2, -2, 8))
        val row = c.hbox(); val e = Dlg.field(c, if (Ai.ready()) "Ask anything about your prep" else "Add an AI key in Settings > AI"); e.setSingleLine(); e.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        row.addView(e, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(c.hspace(8)); row.addView(c.iconBtn("up", T.onAccent, 44) { val s = e.text.toString().trim(); if (s.isNotEmpty()) h.openChat(s) }.also { it.background = T.round(T.plum, 14) })
        e.setOnEditorActionListener { _, _, _ -> val s = e.text.toString().trim(); if (s.isNotEmpty()) h.openChat(s); true }
        card.addView(row, lp(-1, -2, 10))
        val chips = c.hbox(); chips.addView(c.pill("Open chat", "sparkle", false) { h.openChat() }, lpm(-2, -2, 0, 0, 8, 0))
        Ai.installedAiApps(c).take(5).forEach { a -> chips.addView(c.pill(a.label.take(16), null, false) { if (!Ai.openApp(c, a.pkg)) c.toast("Cannot open") }, lpm(-2, -2, 0, 0, 8, 0)) }
        card.addView(c.chipRow(chips), lp(-1, -2, 10)); return card
    }

    // ---------------- notes ----------------
    private fun notes(h: Host): View {
        val c = h.act; val card = Card(c); val ns = Store.rows("notes").sortedByDescending { it.optLong("t") }
        card.addView(c.cardHeader("note", "Notes", T.clay, c.iconBtn("plus", T.text2, 32) { Dlg.input(c, "New note", "", "", false, true) { if (it.isNotBlank()) Store.add("notes", obj("date" to Store.today(), "text" to it, "t" to System.currentTimeMillis())) } }))
        if (ns.isEmpty()) card.addView(c.emptyNote("No notes yet"), lp(-2, -2, 4))
        ns.take(3).forEach { n -> card.addView(c.vbox().apply { padDp(0, 8, 0, 8); addView(c.text(n.optString("text"), 13f, 0, T.text, 3)); addView(c.text(n.optString("date"), 11f, 0, T.text3), lp(-2, -2, 2))
            tap { Dlg.input(c, "Edit note", "", n.optString("text"), false, true) { v -> if (v.isNotBlank()) Store.update("notes", n.optString("id")) { it.put("text", v) } } } }) }
        if (ns.size > 3) card.addView(Collapsible(c, "${ns.size - 3} older", "", false, "notes_more") { ns.drop(3).take(30).forEach { n -> addView(c.vbox().apply { padDp(0, 8, 0, 8); addView(c.text(n.optString("text"), 13f, 0, T.text, 3)); addView(c.text(n.optString("date"), 11f, 0, T.text3), lp(-2, -2, 2)) }) } })
        return card
    }

    // ---------------- screen time ----------------
    private fun usage(h: Host): View {
        val c = h.act; val card = Card(c); card.addView(c.cardHeader("clock", "Screen time today", T.clay))
        if (!Usage.granted(c)) { card.addView(c.text("Usage access is needed to show where your time goes.", 12.5f, 0, T.text2), lp(-2, -2, 6)); card.addView(c.pill("Grant usage access", "shield", true, T.clay) { Usage.openSettings(c) }, lp(-2, -2, 8)); return card }
        val u = Usage.today(c).toList().sortedByDescending { it.second }; val total = u.sumOf { it.second } / 60000
        card.addView(c.text(fmtMins(total.toInt()), 26f, 3).tabular(), lp(-2, -2, 6))
        val block = Notifs.block()
        val rows = u.take(6).mapIndexed { i, (pkg, ms) -> Row(try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }, ms / 60000f / maxOf(1f, u.first().second / 60000f), if (pkg in block) T.danger else T.palette[i % 10], fmtMins((ms / 60000).toInt())) }
        card.addView(HBars(c, rows, 28), lp(-1, -2, 8)); card.addView(c.text("Red = in your distraction list", 11f, 0, T.text3), lp(-2, -2, 4)); return card
    }

    // ---------------- links ----------------
    private fun links(h: Host): View {
        val c = h.act; val card = Card(c); card.addView(c.cardHeader("link", "Quick links", T.slate, c.iconBtn("plus", T.text2, 32) { addLink(c) }))
        val ls = listOf(obj("title" to "PYQ and mock portal (Zite)", "url" to Store.s("zite_portal", Zite.PORTAL))) + Store.rows("links")
        ls.forEach { l -> card.addView(c.hbox().apply { padDp(0, 9, 0, 9); addView(c.icon("globe", T.slate, 18)); addView(c.text(l.optString("title"), 14f, 1, T.text, 1), lpm(0, -2, 10, 0, 0, 0, 1f)); addView(c.icon("chev", T.text3, 16, 2f))
            tap { try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.optString("url")))) } catch (e: Exception) { c.toast("Cannot open link") } } }, lp(-1, -2, 2)) }
        return card
    }
    private fun addLink(c: Activity) { Dlg.input(c, "Link title", "e.g. CA Tracker") { t -> if (t.isNotBlank()) Dlg.input(c, "Link URL", "https://") { u -> if (u.startsWith("http")) Store.add("links", obj("title" to t, "url" to u)) else c.toast("URL must start with http") } } }

    // ---------------- next topics (smart suggestion) ----------------
    private fun nextTopic(h: Host): View {
        val c = h.act; val card = Card(c); val z = Zite.data(c)
        card.addView(c.cardHeader("bolt", "Next topics to study", T.gold))
        // biggest PYQ weight first, among topics not done; weak accuracy bumps priority
        val cand = z.syl.flatMap { s -> s.micros.map { m -> Triple(s, m, Syl.microState(z, s.name, m.name)) } }.filter { it.third != Syl.St.DONE }
            .sortedByDescending { (s, m, st) -> val r = z.microResp[s.name + "|" + m.name]; val acc = if (r != null && r[0] + r[1] > 0) r[0].toFloat() / (r[0] + r[1]) else 0.5f; m.q * (1.4f - acc) * (if (st == Syl.St.LEARNING) 1.15f else 1f) }.take(4)
        if (cand.isEmpty()) card.addView(c.emptyNote("Syllabus complete. Revise and take mocks."), lp(-2, -2, 6))
        cand.forEachIndexed { i, (s, m, st) -> card.addView(c.hbox().apply { padDp(0, 9, 0, 9)
            addView(View(c).apply { background = T.round(T.subjectColor(s.name), 5) }, LinearLayout.LayoutParams(T.dp(8), T.dp(8)))
            val col = c.vbox(); col.addView(c.text(m.name, 13.5f, 2, T.text, 2)); col.addView(c.text(Syl.short(s.name) + "  -  ${m.q} PYQs" + (if (st == Syl.St.LEARNING) "  -  in progress" else ""), 11.5f, 0, T.text3), lp(-2, -2, 1))
            addView(col, lpm(0, -2, 12, 0, 8, 0, 1f))
            addView(c.iconBtn("sparkle", T.plum, 32) { h.openChat("Teach me " + m.name + " (" + s.name + ") for UPSC Prelims: key facts, common traps and 5 practice MCQs.") })
            addView(c.iconBtn("check", T.sage, 32) { toggleSyllabus(c, s.name + "|" + m.name, true) }) }, lp(-1, -2, if (i == 0) 6 else 0)) }
        card.addView(c.text("Ranked by past-paper weight and your weak accuracy", 11f, 0, T.text3), lp(-2, -2, 6)); return card
    }

    // ---------------- tools ----------------
    private fun tools(h: Host): View {
        val c = h.act; val card = Card(c); card.addView(c.cardHeader("sliders", "Tools", T.slate))
        fun tile(ic: String, label: String, tint: Int, f: () -> Unit): View = c.vbox().apply { gravity = Gravity.CENTER; padDp(6, 12, 6, 12); background = T.round(T.inset, 16); addView(c.icon(ic, tint, 24, 1.9f)); addView(c.text(label, 11.5f, 2, T.text2, 1).apply { gravity = Gravity.CENTER }, lp(-2, -2, 6)); tap { f() } }
        val r1 = c.hbox(false); val r2 = c.hbox(false)
        listOf(tile("search", "Circle to Search", T.plum) { CircleService.capture(c) }, tile("sparkle", "Assistant", T.teal) { if (!Ai.assist(c)) c.toast("No assistant") }, tile("lock", "Lock screen", T.slate) { CircleService.lock(c) }).forEach { r1.addView(it, lpm(0, -2, 0, 0, 8, 0, 1f)) }
        listOf(tile("bell", "Notifications", T.amber) { CircleService.shade(c) }, tile("note", "Quick note", T.clay) { c.startActivity(Intent(c, CaptureActivity::class.java)) }, tile("gear", "Settings", T.text2) { h.openSettings() }).forEach { r2.addView(it, lpm(0, -2, 0, 0, 8, 0, 1f)) }
        card.addView(r1, lp(-1, -2, 10)); card.addView(r2, lp(-1, -2, 8))
        if (!CircleService.running()) card.addView(c.pill("Enable accessibility for these", "shield", true, T.plum) { CircleService.openSettings(c) }, lp(-2, -2, 10))
        return card
    }
}
