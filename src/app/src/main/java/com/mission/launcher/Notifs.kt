package com.mission.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Mutes distracting apps (kept in Settings > Focus & distractions) and keeps a short list of important
 * study notifications for the home card. Muted items are saved, not deleted, so nothing is lost.
 */
class Notifs : NotificationListenerService() {
    companion object {
        val DEFAULT_BLOCK = listOf("com.instagram.android", "com.facebook.katana", "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage", "com.amazon.mShop.android.shopping", "com.flipkart.android", "com.zhiliaoapp.musically", "in.mohalla.sharechat")
        val DEFAULT_KEEP = listOf("com.google.android.gm", "com.google.android.apps.messaging", "com.whatsapp", "org.telegram.messenger", "com.google.android.calendar", "com.google.android.apps.classroom")
        fun block(): Set<String> = if (Store.has("block_apps")) Store.stringList("block_apps").toSet() else DEFAULT_BLOCK.toSet()
        fun keep(): Set<String> = if (Store.has("keep_apps")) Store.stringList("keep_apps").toSet() else DEFAULT_KEEP.toSet()
    }
    override fun onNotificationPosted(s: StatusBarNotification) {
        try {
            Boot.ensure(this)
            val e = s.notification.extras
            val t = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val x = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val muteAlways = Store.b("mute_distractions", true)
            if (s.packageName in block() && s.isClearable && (muteAlways || Focus.active() || TimerState.running())) {
                val rows = Store.rows("muted"); if (rows.size > 80) Store.replace("muted", rows.takeLast(60))
                Store.add("muted", obj("pkg" to s.packageName, "title" to t, "t" to System.currentTimeMillis()))
                cancelNotification(s.key); return
            }
            if (s.packageName !in keep() || (t.isEmpty() && x.isEmpty())) return
            val rows = Store.rows("important"); if (rows.size > 40) Store.replace("important", rows.takeLast(20))
            Store.add("important", obj("pkg" to s.packageName, "title" to t, "text" to x.take(140), "t" to System.currentTimeMillis()))
        } catch (ex: Exception) { Log.e("notifs", ex) }
    }
}
