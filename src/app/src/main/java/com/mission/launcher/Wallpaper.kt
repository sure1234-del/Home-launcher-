package com.mission.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.net.Uri
import android.os.PowerManager
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Options for one render. */
class WOpts(val grain: Boolean = true, val comfort: Boolean = true, val season: Boolean = true, val dim: Float = 0f)

/**
 * Procedural, continuously time-of-day-aware wallpapers. Colours are interpolated smoothly between
 * keyframes (no sudden jumps), blue is reduced at night, and dithering grain prevents banding.
 */
object Scenes {
    val names = linkedMapOf("hills" to "Layered hills", "dunes" to "Soft dunes", "forest" to "Pine ridge", "ocean" to "Calm sea", "waves" to "Pastel waves", "gradient" to "Minimal glow", "paper" to "Warm paper", "amoled" to "AMOLED dark")

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun lc(a: Int, b: Int, t: Float): Int { val k = t.coerceIn(0f, 1f); return Color.argb(255, lerp(Color.red(a).toFloat(), Color.red(b).toFloat(), k).toInt(), lerp(Color.green(a).toFloat(), Color.green(b).toFloat(), k).toInt(), lerp(Color.blue(a).toFloat(), Color.blue(b).toFloat(), k).toInt()) }
    private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
    private fun hash(i: Int, seed: Int): Float { var x = i * 374761393 + seed * 668265263; x = (x xor (x shr 13)) * 1274126177; x = x xor (x shr 16); return (x and 0xffff) / 65535f }
    private fun noise(x: Float, seed: Int): Float { val i = floor(x).toInt(); val f = x - i; val u = f * f * (3 - 2 * f); return lerp(hash(i, seed), hash(i + 1, seed), u) }
    private fun fbm(x: Float, seed: Int): Float = noise(x, seed) * 0.55f + noise(x * 2.1f, seed + 7) * 0.28f + noise(x * 4.3f, seed + 13) * 0.17f

    // (hour, top colour, horizon colour)
    private val keys = listOf(
        Triple(0f, 0xFF0F1526.toInt(), 0xFF1D2740.toInt()), Triple(4.5f, 0xFF1A2138.toInt(), 0xFF3A3652.toInt()),
        Triple(6f, 0xFF5E6C99.toInt(), 0xFFF0B48C.toInt()), Triple(8f, 0xFF86A6CE.toInt(), 0xFFF6DEC4.toInt()),
        Triple(12f, 0xFF8EB3D6.toInt(), 0xFFE8EEEF.toInt()), Triple(16f, 0xFF90AECB.toInt(), 0xFFF1E4CF.toInt()),
        Triple(18f, 0xFF716C9C.toInt(), 0xFFF2AA7B.toInt()), Triple(19.4f, 0xFF3B3359.toInt(), 0xFFC66E5C.toInt()),
        Triple(21f, 0xFF161B30.toInt(), 0xFF2A3050.toInt()), Triple(24f, 0xFF0F1526.toInt(), 0xFF1D2740.toInt()))

    fun sky(h: Float): Pair<Int, Int> {
        var i = 0; while (i < keys.size - 2 && h >= keys[i + 1].first) i++
        val a = keys[i]; val b = keys[i + 1]; val t = smooth(0f, 1f, (h - a.first) / (b.first - a.first))
        return lc(a.second, b.second, t) to lc(a.third, b.third, t)
    }
    fun daylight(h: Float) = smooth(5.5f, 7.8f, h) * (1f - smooth(17.2f, 19.6f, h))
    private fun comfort(c: Int, hour: Float, on: Boolean): Int {
        if (!on) return c
        val night = 1f - daylight(hour); val k = 0.16f * night
        return Color.argb(255, min(255, (Color.red(c) * (1f + k * 0.35f)).toInt()), (Color.green(c) * (1f - k * 0.05f)).toInt(), (Color.blue(c) * (1f - k)).toInt())
    }

    fun render(w: Int, h: Int, hour: Float, scene: String, month: Int, o: WOpts, dayKey: Int = 0): Bitmap {
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        val (top, hz) = sky(hour); val L = daylight(hour)
        val horizon = h * when (scene) { "ocean" -> 0.58f; "dunes" -> 0.55f; "forest" -> 0.6f; else -> 0.62f }
        when (scene) {
            "paper" -> { cv.drawColor(lc(0xFFEDE8DE.toInt(), 0xFF2A2825.toInt(), 1f - L)) }
            "amoled" -> { cv.drawColor(Color.BLACK); val g = RadialGradient(w * 0.5f, h * 1.05f, h * 0.9f, intArrayOf(lc(0xFF000000.toInt(), top, 0.55f), 0xFF000000.toInt()), null, Shader.TileMode.CLAMP); p.shader = g; cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null }
            "gradient" -> { p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), comfort(top, hour, o.comfort), comfort(hz, hour, o.comfort), Shader.TileMode.CLAMP); cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
                glow(cv, p, w * 0.72f, h * 0.28f, h * 0.7f, lc(hz, Color.WHITE, 0.4f), 0.30f) }
            else -> {
                p.shader = LinearGradient(0f, 0f, 0f, horizon, comfort(top, hour, o.comfort), comfort(hz, hour, o.comfort), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), horizon + 2, p); p.shader = null
                celestial(cv, p, w, h, horizon, hour, L, hz)
                clouds(cv, p, w, h, horizon, hour, L, hz, dayKey, if (o.season && month in 6..9) 1.6f else 1f)
                when (scene) {
                    "hills" -> ridges(cv, p, w, h, horizon, hz, L, dayKey, 5, 0xFF8995A8.toInt(), 0xFF40504A.toInt(), 0.2f)
                    "forest" -> { ridges(cv, p, w, h, horizon, hz, L, dayKey, 5, 0xFF8FA39A.toInt(), 0xFF24352E.toInt(), 0.26f); pines(cv, p, w, h, L, dayKey) }
                    "dunes" -> dunes(cv, p, w, h, horizon, hz, L, dayKey)
                    "ocean" -> ocean(cv, p, w, h, horizon, hz, top, hour, L)
                    "waves" -> waves(cv, p, w, h, horizon, hz, L)
                }
            }
        }
        if (o.season) when {
            month in 6..9 && scene != "amoled" -> { p.color = 0xFF55606E.toInt(); p.alpha = (40 * L + 18).toInt(); cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p) }
            (month == 12 || month <= 2) && scene != "amoled" -> { p.shader = LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), 0x00E6ECF2, 0x66E6ECF2, Shader.TileMode.CLAMP); cv.drawRect(0f, h * 0.55f, w.toFloat(), h.toFloat(), p); p.shader = null }
            month in 3..5 && L > 0.2f -> { p.color = 0xFFFF9A3C.toInt(); p.alpha = (26 * L).toInt(); cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p) }
        }
        finish(bm, o)
        return bm
    }
    fun finish(bm: Bitmap, o: WOpts) {
        val cv = Canvas(bm); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (o.grain) { val t = noiseTile(); p.shader = BitmapShader(t, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); p.alpha = 255; cv.drawRect(0f, 0f, bm.width.toFloat(), bm.height.toFloat(), p); p.shader = null }
        if (o.dim > 0f) { p.color = Color.BLACK; p.alpha = (o.dim * 255).toInt(); cv.drawRect(0f, 0f, bm.width.toFloat(), bm.height.toFloat(), p) }
    }
    private var tile: Bitmap? = null
    private fun noiseTile(): Bitmap { tile?.let { return it }; val s = 96; val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888); val r = java.util.Random(11); val px = IntArray(s * s) { val v = r.nextInt(255); Color.argb(9, v, v, v) }; b.setPixels(px, 0, s, 0, 0, s, s); tile = b; return b }

    private fun glow(cv: Canvas, p: Paint, x: Float, y: Float, r: Float, col: Int, a: Float) { p.shader = RadialGradient(x, y, r, intArrayOf(T.withAlpha(col, a), T.withAlpha(col, 0f)), null, Shader.TileMode.CLAMP); cv.drawCircle(x, y, r, p); p.shader = null }

    private fun celestial(cv: Canvas, p: Paint, w: Int, h: Int, horizon: Float, hour: Float, L: Float, hz: Int) {
        if (L < 0.95f) { // stars
            val r = java.util.Random(5); val a = ((1f - L) * 220).toInt()
            p.color = Color.WHITE; for (i in 0 until 110) { val x = r.nextFloat() * w; val y = r.nextFloat() * horizon * 0.85f; val s = 0.7f + r.nextFloat() * 1.6f; p.alpha = (a * (0.35f + r.nextFloat() * 0.65f)).toInt(); cv.drawCircle(x, y, s, p) }; p.alpha = 255
        }
        val day = hour in 5.8f..18.4f
        val t = if (day) (hour - 5.8f) / 12.6f else (((hour - 18.4f + 24f) % 24f) / 11.4f)
        val x = w * (0.1f + 0.8f * t.coerceIn(0f, 1f)); val y = horizon - sin(PI.toFloat() * t.coerceIn(0f, 1f)) * horizon * 0.78f
        if (day) { glow(cv, p, x, y, h * 0.3f, 0xFFFFE6B0.toInt(), 0.55f * (0.4f + L * 0.6f)); p.color = 0xFFFFF4D0.toInt(); cv.drawCircle(x, y, h * 0.045f, p) }
        else { glow(cv, p, x, y, h * 0.2f, 0xFFDDE6FF.toInt(), 0.28f); p.color = 0xFFF1EBD8.toInt(); cv.drawCircle(x, y, h * 0.04f, p); p.color = lc(0xFF1A2138.toInt(), hz, 0.2f); cv.drawCircle(x + h * 0.016f, y - h * 0.008f, h * 0.036f, p) }
    }
    private fun clouds(cv: Canvas, p: Paint, w: Int, h: Int, horizon: Float, hour: Float, L: Float, hz: Int, seed: Int, density: Float) {
        val r = java.util.Random(seed.toLong() + 3); val n = (6 * density).toInt()
        val drift = (hour / 24f) * w * 0.5f
        for (i in 0 until n) {
            val cx = ((r.nextFloat() * w * 1.4f + drift) % (w * 1.3f)) - w * 0.15f; val cy = horizon * (0.12f + r.nextFloat() * 0.5f); val rw = w * (0.09f + r.nextFloat() * 0.12f)
            val col = lc(0xFF55607A.toInt(), lc(hz, Color.WHITE, 0.7f), L)
            for (k in 0 until 4) { val ox = (k - 1.5f) * rw * 0.55f; val ry = rw * (0.22f + 0.1f * (k % 2))
                p.shader = RadialGradient(cx + ox, cy, rw * 0.8f, intArrayOf(T.withAlpha(col, 0.32f * (0.4f + L * 0.6f)), T.withAlpha(col, 0f)), null, Shader.TileMode.CLAMP)
                cv.save(); cv.scale(1f, ry / (rw * 0.8f), cx + ox, cy); cv.drawCircle(cx + ox, cy, rw * 0.8f, p); cv.restore() }
        }
        p.shader = null
    }
    private fun ridges(cv: Canvas, p: Paint, w: Int, h: Int, horizon: Float, hz: Int, L: Float, seed: Int, layers: Int, far: Int, near: Int, amp: Float) {
        for (l in 0 until layers) {
            val d = l / (layers - 1f)
            val base = horizon - h * 0.04f + (h - horizon) * (0.1f + 0.78f * d * d)
            val a = h * amp * (0.35f + 0.65f * (1f - d * 0.4f)) * 0.5f
            val dayCol = lc(lc(hz, far, 0.6f), near, d * 0.95f)
            val nightCol = lc(0xFF1D2438.toInt(), 0xFF0B0F1A.toInt(), d)
            val col = lc(nightCol, lc(dayCol, hz, 0.12f * (1f - d)), L)
            val path = Path(); path.moveTo(0f, h.toFloat())
            var x = 0f; while (x <= w + 8) { val y = base - a * fbm(x / (w * (0.26f - 0.03f * l)) + l * 9.3f, seed + l * 31); path.lineTo(x, y); x += 8f }
            path.lineTo(w.toFloat(), h.toFloat()); path.close()
            p.shader = LinearGradient(0f, base - a, 0f, h.toFloat(), col, lc(col, Color.BLACK, 0.18f), Shader.TileMode.CLAMP); cv.drawPath(path, p); p.shader = null
            // atmospheric haze between layers
            p.shader = LinearGradient(0f, base - a, 0f, base + a * 0.9f, T.withAlpha(hz, 0.30f * (1f - d) * (0.3f + L * 0.7f)), T.withAlpha(hz, 0f), Shader.TileMode.CLAMP); cv.drawPath(path, p); p.shader = null
        }
    }
    private fun pines(cv: Canvas, p: Paint, w: Int, h: Int, L: Float, seed: Int) {
        val r = java.util.Random(seed.toLong() + 21); p.color = lc(0xFF0A0F14.toInt(), 0xFF1B2A23.toInt(), L)
        for (i in 0 until 38) { val x = r.nextFloat() * w; val th = h * (0.05f + r.nextFloat() * 0.07f); val y = h * (0.9f + r.nextFloat() * 0.1f); val tw = th * 0.34f
            val pa = Path(); pa.moveTo(x, y - th); pa.lineTo(x + tw, y); pa.lineTo(x - tw, y); pa.close(); cv.drawPath(pa, p) }
    }
    private fun dunes(cv: Canvas, p: Paint, w: Int, h: Int, horizon: Float, hz: Int, L: Float, seed: Int) {
        for (l in 0 until 4) { val d = l / 3f; val base = horizon + (h - horizon) * (0.05f + 0.8f * d * d)
            val dayCol = lc(lc(hz, 0xFFE0B98F.toInt(), 0.6f), 0xFF9C6F4E.toInt(), d); val col = lc(lc(0xFF1F2236.toInt(), 0xFF0E0F18.toInt(), d), dayCol, L)
            val path = Path(); path.moveTo(0f, h.toFloat()); var x = 0f
            while (x <= w + 6) { val y = base - h * 0.07f * (0.5f + 0.5f * sin(x / w * (3.2f + l) * PI.toFloat() + l * 1.7f + seed * 0.1f)) - h * 0.02f * noise(x / (w * 0.2f), seed + l); path.lineTo(x, y); x += 6f }
            path.lineTo(w.toFloat(), h.toFloat()); path.close(); p.color = col; cv.drawPath(path, p) }
    }
    private fun ocean(cv: Canvas, p: Paint, w: Int, h: Int, horizon: Float, hz: Int, top: Int, hour: Float, L: Float) {
        p.shader = LinearGradient(0f, horizon, 0f, h.toFloat(), lc(hz, 0xFF6C8FA0.toInt(), 0.35f), lc(top, 0xFF1F3B4B.toInt(), 0.6f), Shader.TileMode.CLAMP); cv.drawRect(0f, horizon, w.toFloat(), h.toFloat(), p); p.shader = null
        val day = hour in 5.8f..18.4f; val t = (if (day) (hour - 5.8f) / 12.6f else ((hour - 18.4f + 24f) % 24f) / 11.4f).coerceIn(0f, 1f); val sx = w * (0.1f + 0.8f * t)
        for (i in 0 until 26) { val y = horizon + (h - horizon) * (i / 26f) * (i / 26f) + 4; val half = w * (0.012f + 0.0045f * i) * (0.5f + 0.5f * noise(i * 1.7f, 3)); p.color = if (day) 0xFFFFF0CC.toInt() else 0xFFE3EAFF.toInt(); p.alpha = (110 * (1f - i / 28f) * (0.4f + 0.6f * (if (day) L else 0.5f))).toInt(); cv.drawRoundRect(sx - half, y, sx + half, y + 2.4f, 2f, 2f, p) }
        p.alpha = 255
    }
    private fun waves(cv: Canvas, p: Paint, w: Int, h: Int, horizon: Float, hz: Int, L: Float) {
        for (l in 0 until 5) { val d = l / 4f; val base = horizon * 0.8f + (h - horizon * 0.8f) * (0.1f + 0.85f * d)
            val dayCol = lc(lc(hz, 0xFFB8C7C4.toInt(), 0.5f), 0xFF6E8E89.toInt(), d); val col = lc(lc(0xFF222A3E.toInt(), 0xFF0F1420.toInt(), d), dayCol, L)
            val path = Path(); path.moveTo(0f, h.toFloat()); var x = 0f
            while (x <= w + 6) { path.lineTo(x, base - h * 0.035f * sin(x / w * 2f * PI.toFloat() * (1.2f + l * 0.35f) + l * 2.1f)); x += 6f }
            path.lineTo(w.toFloat(), h.toFloat()); path.close(); p.color = col; cv.drawPath(path, p) }
    }
}

object Wallpaper {
    private fun dims(c: Context): IntArray { val dm = Resources.getSystem().displayMetrics; return intArrayOf(max(dm.widthPixels, dm.heightPixels), min(dm.widthPixels, dm.heightPixels)) }
    fun enabled() = Store.b("wp_sys", true)
    fun scene() = Store.s("wp_scene", "hills")
    fun opts() = WOpts(Store.b("wp_grain", true), Store.b("wp_comfort", true), Store.b("wp_season", true), Store.i("wp_dim", 6) / 100f)
    fun hourNow(): Float { val n = LocalTime.now(Repo.zone()); return n.hour + n.minute / 60f }
    fun photos(c: Context): List<File> = File(c.filesDir, "wp").listFiles()?.filter { it.extension == "jpg" }?.sortedBy { it.name } ?: emptyList()

    fun build(c: Context, w: Int, h: Int, forLock: Boolean = false): Bitmap {
        val now = LocalDate.now(Repo.zone())
        val sc = scene(); val o = opts(); val pw = if (Store.b("wp_parallax", true)) (w * 1.2f).toInt() else w
        val bm: Bitmap = if (sc == "photo") { photoBitmap(c, pw, h, o) ?: Scenes.render(pw, h, hourNow(), "hills", now.monthValue, o, now.dayOfYear) }
        else Scenes.render(pw, h, if (Store.b("wp_auto", true)) hourNow() else Store.i("wp_fixed_hour", 12).toFloat(), sc, now.monthValue, o, now.dayOfYear)
        if (forLock && Store.b("wp_lock_text", true)) {
            val cv = Canvas(bm); val p = Paint(Paint.ANTI_ALIAS_FLAG); p.typeface = T.font(2); p.textAlign = Paint.Align.CENTER
            val days = java.time.temporal.ChronoUnit.DAYS.between(now, Repo.examDate())
            p.textSize = h * 0.032f; p.color = Color.WHITE; p.alpha = 190; cv.drawText("$days days to Prelims", pw / 2f, h * 0.93f, p)
        }
        return bm
    }
    private fun photoBitmap(c: Context, w: Int, h: Int, o: WOpts): Bitmap? {
        val list = photos(c); if (list.isEmpty()) return null
        val date = LocalDate.now(Repo.zone())
        val idx = when (Store.s("wp_photo_mode", "daily")) { "hourly" -> (date.dayOfYear * 24 + LocalTime.now(Repo.zone()).hour) % list.size; "single" -> 0; else -> date.dayOfYear % list.size }
        val src = try { BitmapFactory.decodeFile(list[idx].path) } catch (e: Throwable) { null } ?: return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(out); val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val s = max(w.toFloat() / src.width, h.toFloat() / src.height); val dw = src.width * s; val dh = src.height * s
        var draw = src
        val blur = Store.i("wp_blur", 0)
        if (blur > 0) { val k = 1 + blur / 6; val small = Bitmap.createScaledBitmap(src, max(8, src.width / (4 * k)), max(8, src.height / (4 * k)), true); draw = small }
        cv.drawBitmap(draw, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), p)
        // eye comfort: warm + slightly darker at night
        val night = 1f - Scenes.daylight(hourNow())
        if (o.comfort && night > 0.05f) { p.color = 0xFFFF9A4D.toInt(); p.alpha = (46 * night).toInt(); cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.alpha = 255 }
        val vg = RadialGradient(w / 2f, h / 2f, max(w, h) * 0.75f, intArrayOf(0x00000000, 0x55000000), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP); p.shader = vg; cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
        Scenes.finish(out, WOpts(o.grain, false, false, max(o.dim, 0.18f)))
        return out
    }
    fun apply(c: Context, force: Boolean = false): Boolean {
        try {
            if (!force) {
                if (!enabled()) return false
                if (Store.b("wp_pause_focus", true) && (Focus.active() || TimerState.running())) return false
                if (Store.b("wp_battery_pause", true) && (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode) return false
            }
            val d = dims(c); val wm = WallpaperManager.getInstance(c); val tgt = Store.s("wp_target", "both")
            if (tgt == "both" || tgt == "home") { val b = build(c, d[0], d[1]); wm.setBitmap(b, null, true, WallpaperManager.FLAG_SYSTEM); b.recycle() }
            if (tgt == "both" || tgt == "lock") { val b = build(c, d[0], d[1], true); wm.setBitmap(b, null, true, WallpaperManager.FLAG_LOCK); b.recycle() }
            Store.put("wp_last", System.currentTimeMillis()); return true
        } catch (e: Throwable) { Log.e("wallpaper", e); return false }
    }
    /** Called every hour; renders only when the chosen interval has passed so changes stay gentle. */
    fun hourly(c: Context) {
        if (!enabled() || Store.s("wp_scene") == "photo" && photos(c).isEmpty()) { if (!enabled()) return }
        val every = Store.i("wp_interval", 30).coerceIn(15, 240)
        if (System.currentTimeMillis() - Store.l("wp_last", 0) >= (every - 1) * 60000L) apply(c)
    }
    fun preview(c: Context, w: Int, h: Int): Bitmap = build(c, w, h)
    fun addPhoto(c: Context, uri: Uri): Boolean {
        return try {
            val dir = File(c.filesDir, "wp").apply { mkdirs() }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var ss = 1; while (o.outWidth / ss > 3000) ss *= 2
            val bm = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = ss }) } ?: return false
            File(dir, "p${System.currentTimeMillis()}.jpg").outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bm.recycle(); true
        } catch (e: Throwable) { Log.e("wp-add", e); false }
    }
    fun clearPhotos(c: Context) {
        val directory = File(c.filesDir, "wp")
        if (directory.exists()) {
            val files = directory.listFiles() ?: throw IllegalStateException("Could not list wallpaper photos")
            val failures = files.filter { it.exists() && !it.delete() }
            check(failures.isEmpty()) { "Could not delete ${failures.size} wallpaper photos" }
            check(directory.delete()) { "Could not remove wallpaper photo folder" }
        }
    }
}
