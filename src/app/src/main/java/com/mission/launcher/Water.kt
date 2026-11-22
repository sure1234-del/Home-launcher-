package com.mission.launcher

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.widget.LinearLayout
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Hydration engine. A reminder is an ongoing notification that is re-posted
 * (with escalating wording and an on-screen card) until the user logs water.
 */
object Water {
    const val NID = 4001
    private val main = Handler(Looper.getMainLooper())

    fun goal() = Store.i("water_goal", 3000).coerceIn(500, 8000)

    fun enabled() = Store.b("water_on", true)

    fun startTime(): LocalTime =
        try {
            LocalTime.parse(Store.s("water_start_time", "%02d:00".format(Store.i("water_start", 7).coerceIn(0, 22))))
        } catch (e: Exception) {
            LocalTime.of(Store.i("water_start", 7).coerceIn(0, 22), 0)
        }

    fun endTime(): LocalTime {
        val selected =
            try {
                LocalTime.parse(Store.s("water_end_time", "%02d:00".format(Store.i("water_end", 22).coerceIn(1, 23))))
            } catch (e: Exception) {
                LocalTime.of(Store.i("water_end", 22).coerceIn(1, 23), 0)
            }
        return selected.takeIf { it.isAfter(startTime()) } ?: LocalTime.MAX
    }

    fun startHour() = startTime().hour

    fun endHour() = endTime().hour

    fun interval() = Store.i("water_interval", 90).coerceIn(20, 240)

    fun nagMin() = Store.i("water_nag", 8).coerceIn(2, 30)

    fun insist() = Store.b("water_insist", true)

    fun pending() = Store.b("w_pending", false)

    fun cups(): List<Int> =
        Store.s("water_cups", "150,250,330,500,750").split(",").mapNotNull {
            it.trim().toIntOrNull()
        }.ifEmpty { listOf(250, 500) }

    // ---- logging ----
    fun add(ml: Int): String {
        val id = Store.newId()
        Store.add("water", obj("id" to id, "t" to System.currentTimeMillis(), "ml" to ml))
        return id
    }

    fun remove(id: String) = Store.remove("water", id)

    fun todayEntries(): List<JSONObject> {
        val d = LocalDate.now(Repo.zone())
        return Store.rows("water").filter { Repo.dayOf(it.optLong("t")) == d }.sortedByDescending { it.optLong("t") }
    }

    fun history(days: Int): List<Pair<LocalDate, Int>> {
        val t = LocalDate.now(Repo.zone())
        return (days - 1 downTo 0).map {
            val d = t.minusDays(it.toLong())
            d to Repo.waterOn(d)
        }
    }

    fun streak(): Int {
        var d = LocalDate.now(Repo.zone())
        if (Repo.waterOn(d) < goal()) d = d.minusDays(1)
        var s = 0
        while (Repo.waterOn(d) >= goal() && s < 1000) {
            s++
            d = d.minusDays(1)
        }
        return s
    }

    /** How many ml the user should already have had by now to be on pace. */
    fun behind(): Int {
        val n = LocalTime.now(Repo.zone())
        val duration = Duration.between(startTime(), endTime()).toMinutes().coerceAtLeast(1)
        val elapsed = Duration.between(startTime(), n).toMinutes().toFloat()
        val f = (elapsed / duration).coerceIn(0f, 1f)
        return (goal() * f - Repo.waterToday()).toInt().coerceAtLeast(0)
    }

    // ---- scheduling ----
    fun arm(c: Context) {
        if (!enabled()) {
            clear(c)
            return
        }
        val now = LocalDateTime.now(Repo.zone())
        val start = startTime()
        val end = endTime()
        val local = now.toLocalTime()
        if (pending()) {
            if (!insist()) {
                Store.put("w_pending", false)
                Store.put("w_level", 0)
                Store.put("w_nag_at", 0L)
                Sched.cancelWater(c)
            } else if (!notificationsAvailable(c) && !c.canOverlay()) {
                clear(c)
            } else {
                val due =
                    Store.l("w_nag_at").takeIf { it > System.currentTimeMillis() }
                        ?: (System.currentTimeMillis() + nagMin() * 60000L)
                scheduleNag(c, due)
                return
            }
        }
        val have = Repo.waterToday()
        var next =
            when {
                have >= goal() || !local.isBefore(end) -> now.toLocalDate().plusDays(1).atTime(start)
                local.isBefore(start) -> now.toLocalDate().atTime(start)
                else -> now.plusMinutes((if (behind() > goal() / 6) interval() * 2 / 3 else interval()).toLong())
            }
        if (!next.toLocalTime().isBefore(end)) next = next.toLocalDate().plusDays(1).atTime(start)
        val at = next.atZone(Repo.zone()).toInstant().toEpochMilli()
        Store.put("w_next", at)
        Sched.water(c, at)
    }

    @Synchronized
    fun remind(
        c: Context,
        nag: Boolean,
        expectedNagAt: Long? = null,
    ) {
        Boot.ensure(c)
        if (!enabled()) {
            clear(c)
            return
        }
        if (nag && expectedNagAt != null) {
            if (expectedNagAt == 0L || Store.l("w_nag_at") != expectedNagAt) return
            Store.put("w_nag_at", 0L)
            Store.flushNow()
        }
        val have = Repo.waterToday()
        val local = LocalTime.now(Repo.zone())
        if (have >= goal() || local.isBefore(startTime()) || !local.isBefore(endTime())) {
            clear(c)
            arm(c)
            return
        }
        val interactive =
            try {
                (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            } catch (
                e: Exception,
            ) {
                Log.e("water-power", e)
                false
            }
        val overlayAllowed = c.canOverlay()
        val notificationPosted = post(c, Store.i("w_level", 0) + if (nag) 1 else 0)
        val cardPossible = overlayAllowed && interactive
        if (!notificationPosted && !cardPossible && !overlayAllowed) {
            clear(c)
            arm(c)
            return
        }

        val level =
            if (notificationPosted || cardPossible) {
                Store.i("w_level", 0) + if (nag) 1 else 0
            } else {
                Store.i("w_level", 0)
            }
        val cardGeneration = Store.i("w_card_generation", 0) + 1
        Store.put("w_card_generation", cardGeneration)
        Store.put("w_pending", true)
        Store.put("w_level", level)
        if (cardPossible) card(c, level, cardGeneration)
        if (insist()) {
            scheduleNag(c, System.currentTimeMillis() + nagMin() * 60000L)
        } else {
            Store.put("w_pending", false)
            Store.put("w_level", 0)
            Store.put("w_nag_at", 0L)
            arm(c)
        }
    }

    fun clear(c: Context) {
        Store.put("w_pending", false)
        Store.put("w_level", 0)
        Store.put("w_nag_at", 0L)
        Store.put("w_card_generation", Store.i("w_card_generation", 0) + 1)
        Sched.cancelWater(c)
        try {
            c.nm().cancel(NID)
        } catch (e: Exception) {
            Log.e("water-cancel", e)
        }
        Overlay.remove(c, "water")
    }

    fun logFromAction(
        c: Context,
        ml: Int,
    ) {
        add(ml)
        clear(c)
        Sched.cancelWater(c)
        arm(c)
        val have = Repo.waterToday()
        if (have >= goal() && Store.s("w_goal_day") != Store.today()) {
            Store.put("w_goal_day", Store.today())
            val nm = c.nm()
            if (notificationsAvailable(c, CH_INFO)) {
                try {
                    nm.notify(
                        NID + 1,
                        Notification.Builder(
                            c,
                            CH_INFO,
                        ).setSmallIcon(
                            smallIcon(c),
                        ).setContentTitle("Water goal reached").setContentText("$have ml today. Well done.").setAutoCancel(true).build(),
                    )
                } catch (
                    e: Exception,
                ) {
                    Log.e("water-notification", e)
                }
            }
        }
    }

    fun snooze(c: Context) {
        Store.put("w_card_generation", Store.i("w_card_generation", 0) + 1)
        Store.put("w_pending", true)
        try {
            c.nm().cancel(NID)
        } catch (e: Exception) {
            Log.e("water-cancel", e)
        }
        Overlay.remove(c, "water")
        scheduleNag(c, System.currentTimeMillis() + 10 * 60000L)
    }

    fun skip(c: Context) {
        clear(c)
        Sched.cancelWater(c)
        arm(c)
    }

    fun onDismissed(c: Context) {
        if (pending() && insist() && notificationsAvailable(c)) post(c, Store.i("w_level", 0))
    }

    private fun scheduleNag(
        c: Context,
        at: Long,
    ) {
        Store.put("w_nag_at", at)
        Store.flushNow()
        Sched.waterNag(c, at)
    }

    private fun notificationsAvailable(
        c: Context,
        channel: String = CH_WATER,
    ): Boolean =
        try {
            val nm = c.nm()
            nm.areNotificationsEnabled() && (nm.getNotificationChannel(channel)?.importance ?: NotificationManager.IMPORTANCE_DEFAULT) != NotificationManager.IMPORTANCE_NONE
        } catch (e: Exception) {
            false
        }

    // ---- notification ----
    private fun smallIcon(c: Context): Int {
        val id = c.resources.getIdentifier("ic_stat_drop", "drawable", c.packageName)
        return if (id != 0) id else android.R.drawable.ic_dialog_info
    }

    private fun act(
        c: Context,
        code: Int,
        action: String,
        ml: Int = 0,
        mutable: Boolean = false,
    ): PendingIntent {
        val i = Intent(c, WaterReceiver::class.java).setAction(action).putExtra("ml", ml)
        return PendingIntent.getBroadcast(
            c,
            code,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE),
        )
    }

    private fun lines(
        level: Int,
        have: Int,
        goal: Int,
        behind: Int,
    ): Pair<String, String> {
        val tail = "$have / $goal ml today" + if (behind > 0) "  -  $behind ml behind pace" else ""
        return when {
            level <= 0 -> "Time for water" to tail
            level == 1 -> "Water is still pending" to "A glass now keeps your focus sharp. $tail"
            level in 2..3 -> "Please drink water now" to "Dehydration drops concentration. $tail"
            else -> "Hydration overdue" to "You have ignored this $level times. Drink and tap +250. $tail"
        }
    }

    fun post(
        c: Context,
        level: Int,
    ): Boolean {
        if (!notificationsAvailable(c)) return false
        val have = Repo.waterToday()
        val g = goal()
        val (title, body) = lines(level, have, g, behind())
        val open =
            PendingIntent.getActivity(
                c,
                7,
                Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val ri = RemoteInput.Builder("ml").setLabel("amount in ml").build()
        val custom =
            Notification.Action.Builder(
                Icon.createWithResource(c, smallIcon(c)),
                "Add / remove",
                act(c, 14, WaterReceiver.CUSTOM, 0, true),
            ).addRemoteInput(ri).build()
        val c1 = cups().getOrElse(1) { 250 }
        val c2 = cups().getOrElse(3) { 500 }
        val b =
            Notification.Builder(c, CH_WATER).setSmallIcon(smallIcon(c)).setContentTitle(title).setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body)).setCategory(Notification.CATEGORY_REMINDER)
                .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(level == 0 && false).setContentIntent(open)
                .setDeleteIntent(act(c, 15, WaterReceiver.DISMISSED))
                .setProgress(
                    g,
                    have.coerceAtMost(g),
                    false,
                ).setColor(T.teal).setShowWhen(true).setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(c, smallIcon(c)),
                        "+$c1 ml",
                        act(c, 11, WaterReceiver.ADD, c1),
                    ).build(),
                )
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(c, smallIcon(c)),
                        "+$c2 ml",
                        act(c, 12, WaterReceiver.ADD, c2),
                    ).build(),
                )
                .addAction(custom)
        val n = b.build()
        n.flags = n.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        return try {
            c.nm().notify(NID, n)
            true
        } catch (e: Exception) {
            Log.e("water-notification", e)
            false
        }
    }

    // ---- on-screen insistence card ----
    private fun card(
        c0: Context,
        level: Int,
        generation: Int,
    ) {
        val c = c0.applicationContext
        main.post {
            try {
                if (Store.i("w_card_generation", 0) != generation || !c.canOverlay()) return@post
                val have = Repo.waterToday()
                val (title, body) = lines(level, have, goal(), behind())
                val box =
                    LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        background = T.round(T.cardSolid, 22, 1, T.line)
                        padDp(16, 14, 16, 14)
                        T.elevate(this, 8, 22)
                    }
                val head = c.hbox()
                head.addView(c.icon("drop", T.teal, 20, 2f))
                head.addView(c.text(title, 14.5f, 2), lpm(-2, -2, 8, 0, 0, 0, 1f))
                box.addView(head)
                box.addView(c.text(body, 12f, 0, T.text2), lp(-2, -2, 6))
                val row = c.hbox()
                val a = cups().getOrElse(1) { 250 }
                val b2 = cups().getOrElse(3) { 500 }
                row.addView(c.pill("+$a ml", "drop", true, T.teal) { logFromAction(c, a) }, lpm(-2, -2, 0, 0, 8, 0))
                row.addView(c.pill("+$b2 ml", null, false) { logFromAction(c, b2) }, lpm(-2, -2, 0, 0, 8, 0))
                row.addView(c.pill("Later", null, false) { snooze(c) }, lpm(-2, -2, 0, 0, 8, 0))
                box.addView(row, lp(-2, -2, 10))
                val lp =
                    Overlay.params(T.dp(300)).apply {
                        gravity = Gravity.TOP or Gravity.END
                        x = T.dp(16)
                        y = T.dp(48)
                    }
                Overlay.show(c, "water", box, lp)
            } catch (e: Exception) {
                Log.e("water-card", e)
            }
        }
    }
}
