package com.mission.launcher

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Home gestures, each mappable in Settings > Gestures and multitasking. */
object Gestures {
    val options = listOf("none" to "Nothing", "lock" to "Lock screen", "notif" to "Notification shade", "drawer" to "App drawer", "circle" to "Circle to Search", "assistant" to "Assistant (Gemini / Hyper AI)", "edit" to "Edit home", "settings" to "Settings", "chat" to "Mission AI chat")
    fun run(h: Host, name: String) {
        val a = h.act
        when (name) {
            "lock" -> CircleService.lock(a); "notif" -> CircleService.shade(a); "drawer" -> h.openDrawer()
            "circle" -> CircleService.capture(a); "assistant" -> if (!Ai.assist(a)) a.toast("No assistant found")
            "edit" -> h.toggleEdit(); "settings" -> h.openSettings(); "chat" -> h.openChat()
        }
    }
}

/** Mindful pause and per-app daily limits, checked every time an app is opened from the launcher. */
object Guard {
    fun limits(): JSONObject = try { JSONObject(Store.s("app_limits", "{}")) } catch (e: Exception) { JSONObject() }
    fun setLimit(pkg: String, min: Int) { val o = limits(); if (min <= 0) o.remove(pkg) else o.put(pkg, min); Store.put("app_limits", o.toString()) }
    fun distracting(pkg: String) = pkg in Notifs.block()

    fun check(a: Activity, pkg: String, go: () -> Unit) {
        val lim = limits().optInt(pkg, 0)
        if (lim > 0 && Usage.granted(a)) {
            val used = ((Usage.today(a)[pkg] ?: 0L) / 60000).toInt()
            if (used >= lim) { limitDialog(a, used, lim, go); return }
        }
        if (Store.b("mindful", true) && distracting(pkg) && !Focus.active()) { pause(a, go); return }
        go()
    }
    private fun limitDialog(a: Activity, used: Int, lim: Int, go: () -> Unit) {
        val b = Dlg.builder(a).setTitle("Daily limit reached").setMessage("You have used $used of $lim minutes today. ${ChronoUnit.DAYS.between(LocalDate.now(), Repo.examDate())} days to Prelims.").setNegativeButton("Stay focused", null)
        if (!Pin.isSet()) b.setPositiveButton("Open anyway") { _, _ -> go() } else b.setPositiveButton("Enter PIN") { _, _ -> if (a is MainActivity) a.gate { go() } else go() }
        b.show()
    }
    private fun pause(a: Activity, go: () -> Unit) {
        val secs = Store.i("mindful_secs", 5).coerceIn(3, 20); val h = Handler(Looper.getMainLooper())
        val d = Dlg.builder(a).setTitle("Take a breath").setMessage("Is this what you want to do right now?\n\n${ChronoUnit.DAYS.between(LocalDate.now(), Repo.examDate())} days to Prelims. Studied today: ${fmtMins(Repo.minutesOn(LocalDate.now()))}.")
            .setNegativeButton("Back to study", null).setPositiveButton("Open anyway ($secs)") { _, _ -> go() }.create()
        d.show(); val btn = d.getButton(android.app.AlertDialog.BUTTON_POSITIVE); btn.isEnabled = false
        var left = secs
        val r = object : Runnable { override fun run() { left--; if (!d.isShowing) return; if (left <= 0) { btn.isEnabled = true; btn.text = "Open anyway" } else { btn.text = "Open anyway ($left)"; h.postDelayed(this, 1000) } } }
        h.postDelayed(r, 1000)
    }
}

/** Morning brief, evening nudge and weekly report, delivered from the hourly tick. */
object Briefs {
    private fun post(c: Context, id: Int, title: String, text: String) {
        try {
            val open = PendingIntent.getActivity(c, id, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val ic = c.resources.getIdentifier("ic_stat_drop", "drawable", c.packageName).let { if (it != 0) it else android.R.drawable.ic_dialog_info }
            c.nm().notify(id, Notification.Builder(c, CH_INFO).setSmallIcon(ic).setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build())
        } catch (e: Exception) { Log.e("brief", e) }
    }
    fun tick(c: Context) {
        val h = LocalTime.now().hour; val today = LocalDate.now().toString(); val days = ChronoUnit.DAYS.between(LocalDate.now(), Repo.examDate())
        if (Store.b("brief_m_on", true) && h == Store.i("brief_m_hour", 6) && Store.s("brief_m_day") != today) {
            Store.put("brief_m_day", today)
            val tasks = Repo.tasksFor(LocalDate.now()); val rev = Repo.revisionsDue().size
            post(c, 6101, "Good morning - $days days to Prelims", (if (tasks.isEmpty()) "No tasks planned yet. Add 3 priorities." else "${tasks.size} tasks today: " + tasks.take(3).joinToString(", ") { it.optString("text") }) + (if (rev > 0) "\n$rev topics to revise." else "") + "\nWater goal ${Water.goal()} ml. Study streak ${Repo.streak()} days.")
        }
        if (Store.b("brief_e_on", true) && h == Store.i("brief_e_hour", 21) && Store.s("brief_e_day") != today) {
            Store.put("brief_e_day", today)
            val goal = Store.i("study_goal_min", 360); val done = Repo.minutesOn(LocalDate.now()); val open = Repo.tasksFor(LocalDate.now()).count { !it.optBoolean("done") }
            post(c, 6102, if (done >= goal) "Goal reached - well done" else "Evening check-in", "Studied ${fmtMins(done)} of ${fmtMins(goal)}." + (if (open > 0) " $open tasks still open." else "") + (if (done < goal) " A 25-minute block still counts." else " Plan tomorrow in two minutes."))
        }
        if (Store.b("brief_w_on", true) && LocalDate.now().dayOfWeek == java.time.DayOfWeek.SUNDAY && h == Store.i("brief_e_hour", 21) && Store.s("brief_w_day") != today) {
            Store.put("brief_w_day", today)
            val days7 = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }; val mins = days7.map { Repo.minutesOn(it) }; val best = days7[mins.indexOf(mins.max())]
            post(c, 6103, "Your week: ${fmtMins(mins.sum())}", "Best day ${best.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} (${fmtMins(mins.max())}). Average ${fmtMins(mins.sum() / 7)} a day. Water streak ${Water.streak()} days.")
        }
    }
}
