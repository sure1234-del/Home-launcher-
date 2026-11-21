package com.mission.launcher

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** One-time import of data written by the previous launcher version (SharedPreferences "m"). */
object Migrate {
    fun run(c: Context) {
        if (Store.b("migrated_v1")) return
        val p = c.getSharedPreferences("m", 0)
        if (p.all.isEmpty()) { Store.put("migrated_v1", true); return }
        fun rows(k: String) = (p.getString(k, "") ?: "").split("\n").filter { it.isNotBlank() }
        try {
            rows("plan").forEach { l -> val x = l.split("|", limit = 3); if (x.size == 3) Store.add("tasks", obj("date" to x[0], "done" to (x[1] == "1"), "text" to x[2])) }
            rows("notes").forEach { l -> val d = l.substringBefore("|"); Store.add("notes", obj("date" to d, "text" to l.substringAfter("|"), "t" to dayMs(d))) }
            rows("scores").forEach { l -> val x = l.split("|"); if (x.size >= 2) Store.add("scores", obj("date" to x[0], "score" to (x[1].toDoubleOrNull() ?: 0.0), "title" to "Mock")) }
            rows("rev").forEach { l -> val x = l.split("|"); if (x.size >= 3) Store.add("revisions", obj("topic" to x[0], "last" to x[1], "round" to (x[2].toIntOrNull() ?: 0))) }
            rows("water").forEach { l -> val x = l.split("|"); if (x.size >= 2) Store.add("water", obj("t" to dayMs(x[0]) + 12 * 3600000L, "ml" to (x[1].toIntOrNull() ?: 0))) }
            p.all.keys.filter { it.startsWith("fm_") }.forEach { k -> val d = k.removePrefix("fm_"); val m = p.getInt(k, 0); if (m > 0) { val s = dayMs(d) + 9 * 3600000L; Store.add("sessions", obj("start" to s, "end" to s + m * 60000L, "mins" to m, "subject" to "General", "kind" to "focus")) } }
            val pins = (p.getString("pins", "") ?: "").split(",").filter { it.isNotBlank() }; if (pins.isNotEmpty()) Store.setStringList("pins", pins)
            p.getString("exam", null)?.let { Store.put("exam_date", it) }
            p.getInt("wgoal", 0).let { if (it in 500..8000) Store.put("water_goal", it) }
            p.getString("gkey", null)?.let { if (it.isNotBlank()) Store.put("gemini_key", it) }
        } catch (e: Exception) { Log.e("migrate", e) }
        Store.put("migrated_v1", true)
    }
    private fun dayMs(d: String): Long = try { LocalDate.parse(d).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
}
