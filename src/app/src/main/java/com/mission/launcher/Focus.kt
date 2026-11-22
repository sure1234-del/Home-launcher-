package com.mission.launcher

import android.app.NotificationManager
import android.content.Context
import java.time.LocalDate

/** Strict focus: locks the app drawer and (optionally) silences notifications. It cannot be cancelled early. */
object Focus {
    fun endAt() = Store.l("focus_end", 0)

    fun left(): Long = if (endAt() > 0) ((endAt() - System.currentTimeMillis()) / 1000).coerceAtLeast(0) else 0L

    fun active() = left() > 0

    fun start(
        c: Context,
        minutes: Int,
        subject: String = "Focus",
    ) {
        if (active()) return
        val end = System.currentTimeMillis() + minutes * 60000L
        Store.put("focus_end", end)
        Store.put("focus_len", minutes)
        Store.put("focus_started", System.currentTimeMillis())
        Store.put("focus_subject", subject)
        dnd(c, true)
        Sched.focus(c, end)
        // also run the floating timer so hours are counted automatically
        if (!TimerState.active()) {
            TimerState.start(subject, "stopwatch")
            TimerService.start(c)
        }
        Store.changed()
    }

    fun finishIfDue(c: Context) {
        val e = endAt()
        if (e <= 0 || System.currentTimeMillis() < e) return
        val len = Store.i("focus_len", 0)
        Store.put("focus_end", 0L)
        dnd(c, false)
        if (TimerState.active() && TimerState.mode() == "stopwatch" && TimerState.subject() == Store.s("focus_subject")) {
            TimerState.finish(true)
            TimerService.stop(c)
        } else {
            Repo.addSession(e - len * 60000L, e, Store.s("focus_subject", "Focus"), "focus")
        }
        Store.put("focus_done_day", LocalDate.now(Repo.zone()).toString())
        Store.changed()
    }

    fun dnd(
        c: Context,
        on: Boolean,
    ) {
        if (!Store.b("focus_dnd", true)) return
        try {
            val n = c.nm()
            if (n.isNotificationPolicyAccessGranted) {
                n.setInterruptionFilter(
                    if (on) NotificationManager.INTERRUPTION_FILTER_ALARMS else NotificationManager.INTERRUPTION_FILTER_ALL,
                )
            }
        } catch (
            e: Exception,
        ) {
        }
    }
}
