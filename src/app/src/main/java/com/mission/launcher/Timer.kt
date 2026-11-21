package com.mission.launcher

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Study-timer state, persisted so the timer survives process death and reboots. */
object TimerState {
    fun running() = Store.b("timer_running")
    fun mode() = Store.s("timer_mode", "stopwatch")          // stopwatch | pomo
    fun phase() = Store.s("timer_phase", "work")             // work | break
    fun subject() = Store.s("timer_subject", "General")
    fun workMin() = Store.i("pomo_work", 25)
    fun breakMin() = Store.i("pomo_break", 5)
    /** accumulated ms of the current phase including the running segment */
    fun phaseMs(): Long = Store.l("timer_acc") + (if (running()) System.currentTimeMillis() - Store.l("timer_seg") else 0L)
    fun active() = Store.b("timer_active")
    fun start(subject: String? = null, mode: String? = null) {
        if (subject != null) Store.put("timer_subject", subject); if (mode != null) Store.put("timer_mode", mode)
        Store.put("timer_active", true); Store.put("timer_phase", "work"); Store.put("timer_acc", 0L); Store.put("timer_seg", System.currentTimeMillis()); Store.put("timer_running", true)
        Store.put("timer_session_start", System.currentTimeMillis()); Store.put("timer_session_ms", 0L); Store.put("pomo_done", 0)
    }
    fun pause() { if (!running()) return; Store.put("timer_acc", phaseMs()); Store.put("timer_running", false) }
    fun resume() { if (running() || !active()) return; Store.put("timer_seg", System.currentTimeMillis()); Store.put("timer_running", true) }
    /** credit finished work time to the session total */
    fun bankWork() { if (phase() == "work") Store.put("timer_session_ms", Store.l("timer_session_ms") + phaseMs()) }
    fun finish(save: Boolean): Int {
        pause(); bankWork()
        val ms = Store.l("timer_session_ms"); val mins = (ms / 60000L).toInt()
        if (save && mins >= 1) Store.add("sessions", obj("start" to Store.l("timer_session_start"), "end" to System.currentTimeMillis(), "mins" to mins, "subject" to subject(), "kind" to mode()))
        Store.put("timer_active", false); Store.put("timer_running", false); Store.put("timer_acc", 0L)
        return mins
    }
}

/** Foreground service that owns the floating timer pill drawn over every app. */
class TimerService : Service() {
    companion object {
        const val ACT_TOGGLE = "t.toggle"; const val ACT_STOP = "t.stop"
        fun start(c: Context) { try { c.startForegroundService(Intent(c, TimerService::class.java)) } catch (e: Exception) { Log.e("timer-start", e) } }
        fun stop(c: Context) { c.stopService(Intent(c, TimerService::class.java)) }
    }
    private val h = Handler(Looper.getMainLooper())
    private var pill: LinearLayout? = null
    private var timeV: TextView? = null
    private var dot: View? = null
    private var panel: LinearLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var expanded = false
    private val tickR = object : Runnable { override fun run() { tick(); h.postDelayed(this, 1000) } }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); Boot.ensure(this) }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            ACT_TOGGLE -> { if (TimerState.running()) TimerState.pause() else TimerState.resume() }
            ACT_STOP -> { TimerState.finish(true); shutdown(); return START_NOT_STICKY }
        }
        if (!TimerState.active()) { shutdown(); return START_NOT_STICKY }
        goForeground()
        buildOverlay()
        h.removeCallbacks(tickR); h.post(tickR)
        return START_STICKY
    }

    private fun goForeground() {
        val n = notif()
        if (Build.VERSION.SDK_INT >= 34) startForeground(5001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(5001, n)
    }
    private fun fmt(ms: Long, countdown: Boolean): String { val s = (if (countdown) ms.coerceAtLeast(0) else ms) / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60) }
    private fun shownMs(): Pair<Long, Boolean> {
        val ms = TimerState.phaseMs()
        return if (TimerState.mode() == "pomo") { val tot = (if (TimerState.phase() == "work") TimerState.workMin() else TimerState.breakMin()) * 60000L; (tot - ms) to true } else ms to false
    }
    private fun notif(): Notification {
        val (ms, cd) = shownMs()
        val open = PendingIntent.getActivity(this, 9, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        fun a(code: Int, act: String) = PendingIntent.getService(this, code, Intent(this, TimerService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val icon = resources.getIdentifier("ic_stat_drop", "drawable", packageName).let { if (it != 0) it else android.R.drawable.ic_lock_idle_alarm }
        return Notification.Builder(this, CH_TIMER).setSmallIcon(icon).setOngoing(true).setContentIntent(open)
            .setContentTitle((if (TimerState.phase() == "work") "Studying: " else "Break: ") + TimerState.subject())
            .setContentText(fmt(ms, cd) + (if (TimerState.running()) "" else "  (paused)")).setCategory(Notification.CATEGORY_SERVICE).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, icon), if (TimerState.running()) "Pause" else "Resume", a(1, ACT_TOGGLE)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, icon), "Stop and save", a(2, ACT_STOP)).build()).build()
    }

    private fun tick() {
        if (!TimerState.active()) { shutdown(); return }
        val (ms, cd) = shownMs()
        if (TimerState.mode() == "pomo" && TimerState.running() && ms <= 0) phaseDone()
        val (ms2, cd2) = shownMs()
        timeV?.text = fmt(ms2, cd2)
        dot?.background = T.round(if (!TimerState.running()) T.text3 else if (TimerState.phase() == "work") T.sage else T.amber, 8)
        if (System.currentTimeMillis() % 5000 < 1000) try { nm().notify(5001, notif()) } catch (e: Exception) { }
    }
    private fun phaseDone() {
        buzz()
        if (TimerState.phase() == "work") { TimerState.bankWork(); Store.put("pomo_done", Store.i("pomo_done") + 1); Store.put("timer_phase", "break") } else Store.put("timer_phase", "work")
        Store.put("timer_acc", 0L); Store.put("timer_seg", System.currentTimeMillis())
        try { nm().notify(5002, Notification.Builder(this, CH_INFO).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(if (TimerState.phase() == "break") "Focus block done" else "Break over").setContentText(if (TimerState.phase() == "break") "Take ${TimerState.breakMin()} minutes off the screen." else "Back to ${TimerState.subject()}.").setAutoCancel(true).build()) } catch (e: Exception) { }
    }
    private fun buzz() { try { val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator; v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200, 120, 400), -1)) } catch (e: Exception) { } }

    private fun buildOverlay() {
        if (pill != null) return
        if (!canOverlay()) return
        val c = applicationContext
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; background = T.round(0xEE262421.toInt(), 22); padDp(12, 8, 12, 8); T.elevate(this, 6, 22) }
        val top = c.hbox()
        val d = View(c); dot = d; top.addView(d, LinearLayout.LayoutParams(T.dp(8), T.dp(8)))
        val tv = c.text("00:00", 15f, 3, 0xFFF3F0EB.toInt()).tabular(); timeV = tv; top.addView(tv, lpm(-2, -2, 8, 0, 0, 0))
        top.addView(c.text(TimerState.subject(), 11f, 1, 0xFFB9B3AA.toInt(), 1), lpm(-2, -2, 8, 0, 0, 0))
        root.addView(top)
        val pn = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE; padDp(0, 8, 0, 0) }
        fun b(ic: String, lbl: String, f: () -> Unit): View = c.hbox().apply { background = T.round(0x22FFFFFF, 12); padDp(10, 7, 12, 7); addView(c.icon(ic, 0xFFF3F0EB.toInt(), 14, 2f)); addView(c.text(" $lbl", 11.5f, 2, 0xFFF3F0EB.toInt())); setOnClickListener { f() } }
        pn.addView(b("pause", "Pause") { if (TimerState.running()) TimerState.pause() else TimerState.resume(); tick() }, lpm(-2, -2, 0, 0, 6, 0))
        pn.addView(b("stop", "Save") { val m = TimerState.finish(true); toast("Saved $m min"); shutdown() }, lpm(-2, -2, 0, 0, 6, 0))
        pn.addView(b("close", "Discard") { TimerState.finish(false); shutdown() })
        panel = pn; root.addView(pn)
        val params = Overlay.params().apply { x = Store.i("timer_x", T.dp(24)); y = Store.i("timer_y", T.dp(120)) }
        lp = params; pill = root
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = params.x; oy = params.y; moved = false }
                MotionEvent.ACTION_MOVE -> { val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt(); if (Math.abs(dx) + Math.abs(dy) > T.dp(8)) moved = true; if (moved) { params.x = ox + dx; params.y = oy + dy; Overlay.update(c, "timer", params) } }
                MotionEvent.ACTION_UP -> { if (moved) { Store.put("timer_x", params.x); Store.put("timer_y", params.y) } else { expanded = !expanded; pn.visibility = if (expanded) View.VISIBLE else View.GONE; Overlay.update(c, "timer", params) } }
            }
            true
        }
        Overlay.show(c, "timer", root, params)
    }

    private fun shutdown() { h.removeCallbacks(tickR); Overlay.remove(applicationContext, "timer"); pill = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { h.removeCallbacks(tickR); Overlay.remove(applicationContext, "timer"); pill = null; super.onDestroy() }
    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent) }
}
