package com.mission.launcher

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.time.LocalDateTime
import java.time.ZoneId

const val CH_WATER = "water_v2"
const val CH_TIMER = "timer_v1"
const val CH_INFO = "info_v1"

object Boot {
    /** Safe to call from anywhere (activity, receiver, service). */
    fun ensure(c: Context) {
        Store.init(c); T.init(c.applicationContext)
        channels(c.applicationContext)
    }
    fun channels(c: Context) {
        val nm = c.nm()
        if (nm.getNotificationChannel(CH_WATER) == null) nm.createNotificationChannel(NotificationChannel(CH_WATER, "Water reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Stays on screen until you log your water"; enableVibration(true); vibrationPattern = longArrayOf(0, 120, 80, 120); setShowBadge(true); lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
        if (nm.getNotificationChannel(CH_TIMER) == null) nm.createNotificationChannel(NotificationChannel(CH_TIMER, "Study timer", NotificationManager.IMPORTANCE_LOW).apply { description = "Shown while the floating study timer runs"; setShowBadge(false) })
        if (nm.getNotificationChannel(CH_INFO) == null) nm.createNotificationChannel(NotificationChannel(CH_INFO, "Study updates", NotificationManager.IMPORTANCE_DEFAULT))
    }
}

fun Context.online(): Boolean = try { val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager; val c = cm.getNetworkCapabilities(cm.activeNetwork); c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } catch (e: Exception) { false }
fun Context.canOverlay(): Boolean = Settings.canDrawOverlays(this)

// ---------------- scheduling ----------------
object Sched {
    private fun pi(c: Context, code: Int, cls: Class<*>, act: String?, flags: Int = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent {
        val i = Intent(c, cls); if (act != null) i.action = act; return PendingIntent.getBroadcast(c, code, i, flags)
    }
    fun setAt(c: Context, at: Long, p: PendingIntent) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) }
        catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) }
    }
    fun hourly(c: Context) {
        val t = LocalDateTime.now(Repo.zone()).withMinute(0).withSecond(0).withNano(0).plusHours(1).withSecond(10)
        setAt(c, t.atZone(Repo.zone()).toInstant().toEpochMilli(), pi(c, 11, TickReceiver::class.java, "tick"))
    }
    fun water(c: Context, at: Long) = setAt(c, at, pi(c, 21, WaterReceiver::class.java, WaterReceiver.TICK))
    fun waterNag(c: Context, at: Long) = setAt(c, at, pi(c, 22, WaterReceiver::class.java, WaterReceiver.NAG))
    fun cancelWater(c: Context) { val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager; am.cancel(pi(c, 21, WaterReceiver::class.java, WaterReceiver.TICK)); am.cancel(pi(c, 22, WaterReceiver::class.java, WaterReceiver.NAG)) }
    fun focus(c: Context, at: Long) = setAt(c, at + 1000, pi(c, 31, TickReceiver::class.java, "focus"))
}

// ---------------- overlay windows (floating widgets over every app) ----------------
object Overlay {
    private val views = HashMap<String, View>()
    private val main = Handler(Looper.getMainLooper())
    fun params(w: Int = WindowManager.LayoutParams.WRAP_CONTENT, focusable: Boolean = false): WindowManager.LayoutParams {
        var f = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!focusable) f = f or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(w, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, f, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
    }
    fun has(tag: String) = views.containsKey(tag)
    fun show(c: Context, tag: String, v: View, lp: WindowManager.LayoutParams, autoRemoveMs: Long = 0) {
        main.post {
            try {
                if (!c.canOverlay()) return@post
                views.remove(tag)?.let { old -> try { (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(old) } catch (e: Exception) { } }
                val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.addView(v, lp); views[tag] = v
                if (autoRemoveMs > 0) main.postDelayed({ if (views[tag] === v) remove(c, tag) }, autoRemoveMs)
            } catch (e: Exception) { Log.e("overlay", e) }
        }
    }
    fun update(c: Context, tag: String, lp: WindowManager.LayoutParams) { try { val v = views[tag] ?: return; (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(v, lp) } catch (e: Exception) { } }
    fun remove(c: Context, tag: String) {
        main.post { try { val v = views.remove(tag) ?: return@post; (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v) } catch (e: Exception) { } }
    }
}

// ---------------- receivers ----------------
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pr = goAsync(); val act = i.action
        Thread {
            try {
                Boot.ensure(c)
                if (act == "focus") { Focus.finishIfDue(c); return@Thread }
                Focus.finishIfDue(c)
                Zite.sync(c)
                Wallpaper.hourly(c)
                Backup.auto(c)
                Briefs.tick(c)
                Water.arm(c)
                try {
                    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                    c.cacheDir.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
                } catch (e: Exception) { Log.e("cache-cleanup", e) }
            } catch (e: Exception) { Log.e("tick", e) } finally { Sched.hourly(c); pr.finish() }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Boot.ensure(c); Sched.hourly(c); Water.arm(c)
        if (TimerState.active()) TimerService.start(c)
    }
}

class WaterReceiver : BroadcastReceiver() {
    companion object { const val TICK = "w.tick"; const val NAG = "w.nag"; const val ADD = "w.add"; const val CUSTOM = "w.custom"; const val SNOOZE = "w.snooze"; const val DISMISSED = "w.dismissed"; const val SKIP = "w.skip" }
    override fun onReceive(c: Context, i: Intent) {
        Boot.ensure(c)
        when (i.action) {
            TICK -> Water.remind(c, false)
            NAG -> Water.remind(c, true)
            ADD -> Water.logFromAction(c, i.getIntExtra("ml", 250))
            CUSTOM -> {
                val raw = android.app.RemoteInput.getResultsFromIntent(i)?.getCharSequence("ml")?.toString()?.trim().orEmpty()
                val amount = raw.toIntOrNull()
                if (amount != null && amount in 10..3000) Water.logFromAction(c, amount) else Water.remind(c, true)
            }
            SNOOZE -> Water.snooze(c)
            SKIP -> Water.skip(c)
            DISMISSED -> Water.onDismissed(c)
        }
    }
}

fun Context.nm(): NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
