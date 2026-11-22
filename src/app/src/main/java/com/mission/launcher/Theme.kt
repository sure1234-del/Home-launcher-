package com.mission.launcher

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider

/**
 * Visual language: warm paper-white cards (iOS widget feel), muted natural accents,
 * very little blue, Inter for Latin text (system Noto handles Devanagari).
 */
object T {
    var dark = false
    var ts = 1f // text scale
    var us = 1f // ui scale
    private var dens = 1f
    private var tfs: Array<Typeface> = arrayOf(Typeface.DEFAULT, Typeface.DEFAULT, Typeface.DEFAULT_BOLD, Typeface.DEFAULT_BOLD)

    fun init(c: Context) {
        dens = c.resources.displayMetrics.density
        ts = Store.f("text_scale", 1f).coerceIn(0.8f, 1.4f)
        us = Store.f("ui_scale", 1f).coerceIn(0.8f, 1.3f)
        val mode = Store.s("theme", "auto")
        val sys = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        dark =
            when (mode) {
                "dark" -> true
                "light" -> false
                "schedule" -> {
                    val h = java.time.LocalTime.now(Repo.zone()).hour
                    val from = Store.i("dark_from", 19)
                    val to = Store.i("dark_to", 6)
                    if (from > to) (h >= from || h < to) else (h in from until to)
                }
                else -> sys
            }
        loadFonts(c)
    }

    private var fontsLoaded = false

    private fun loadFonts(c: Context) {
        if (fontsLoaded) return
        try {
            fun w(wt: Int) = Typeface.Builder(c.assets, "Inter.ttf").setFontVariationSettings("'wght' $wt").build()
            tfs = arrayOf(w(400), w(500), w(600), w(750))
            fontsLoaded = true
        } catch (e: Throwable) {
            tfs = arrayOf(Typeface.create("sans-serif", Typeface.NORMAL), Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.create("sans-serif", Typeface.BOLD))
        }
    }

    /** weight 0 regular, 1 medium, 2 semibold, 3 bold */
    fun font(weight: Int): Typeface = tfs[weight.coerceIn(0, 3)]

    fun dp(v: Int): Int = (v * dens * us + 0.5f).toInt()

    fun dpf(v: Float): Float = v * dens * us

    fun dpFixed(v: Int): Int = (v * dens + 0.5f).toInt()

    // surfaces
    val card get() = if (dark) 0xF2292826.toInt() else 0xF8FFFFFF.toInt()
    val cardSolid get() = if (dark) 0xFF292826.toInt() else 0xFFFFFFFF.toInt()
    val page get() = if (dark) 0xFF171615.toInt() else 0xFFF4F1EB.toInt()
    val inset get() = if (dark) 0xFF35332F.toInt() else 0xFFF1EDE6.toInt()
    val line get() = if (dark) 0x22FFFFFF else 0x14000000
    val text get() = if (dark) 0xFFF3F0EB.toInt() else 0xFF211F1C.toInt()
    val text2 get() = if (dark) 0xFFB9B3AA.toInt() else 0xFF6A645C.toInt()
    val text3 get() = if (dark) 0xFFB8B1A8.toInt() else 0xFF746D64.toInt()
    val shadow get() = if (dark) 0x66000000 else 0x33463C2E

    // muted natural palette (eye-friendly, only one soft blue)
    val sage = 0xFF5E9B78.toInt()
    val amber = 0xFFE0A24E.toInt()
    val terra = 0xFFD7795B.toInt()
    val rose = 0xFFD06784.toInt()
    val plum = 0xFF8F74BE.toInt()
    val teal = 0xFF4C9C9B.toInt()
    val olive = 0xFF9DAF55.toInt()
    val clay = 0xFFB48A72.toInt()
    val slate = 0xFF6D93C4.toInt()
    val gold = 0xFFCDB04A.toInt()
    val danger = 0xFFD0564F.toInt()
    val accent get() = sage
    val palette get() = intArrayOf(amber, teal, plum, sage, slate, terra, rose, clay, olive, gold)

    fun contentColor(background: Int): Int {
        val white = Color.WHITE
        val ink = 0xFF211F1C.toInt()
        return if (contrast(background, white) >= contrast(background, ink)) white else ink
    }

    fun textAccent(color: Int): Int {
        val target = if (dark) Color.WHITE else Color.BLACK
        var current = color
        repeat(24) {
            if (contrast(current, page) >= 4.5 && contrast(current, cardSolid) >= 4.5) return current
            current =
                Color.rgb(
                    (Color.red(current) * 0.88f + Color.red(target) * 0.12f).toInt(),
                    (Color.green(current) * 0.88f + Color.green(target) * 0.12f).toInt(),
                    (Color.blue(current) * 0.88f + Color.blue(target) * 0.12f).toInt(),
                )
        }
        return if (dark) text else 0xFF211F1C.toInt()
    }

    private fun contrast(
        a: Int,
        b: Int,
    ): Double {
        val hi = maxOf(luminance(a), luminance(b))
        val lo = minOf(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    fun subjectColor(name: String): Int {
        val n = name.lowercase()
        return when {
            n.startsWith("econ") -> amber
            n.startsWith("geo") -> teal
            n.startsWith("polity") || n.contains("polity") -> plum
            n.startsWith("env") -> sage
            n.startsWith("sci") -> slate
            n.startsWith("hist") -> terra
            n.startsWith("current") -> rose
            n.startsWith("inter") || n.startsWith("ir") -> clay
            n.startsWith("art") -> olive
            n.startsWith("agri") -> gold
            else -> palette[(n.hashCode() and 0x7fffffff) % 10]
        }
    }

    fun withAlpha(
        c: Int,
        a: Float,
    ): Int = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    fun darken(
        c: Int,
        f: Float,
    ): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] *= f
        return Color.HSVToColor(Color.alpha(c), hsv)
    }

    fun lighten(
        c: Int,
        f: Float,
    ): Int {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        return Color.argb(Color.alpha(c), (r + (255 - r) * f).toInt(), (g + (255 - g) * f).toInt(), (b + (255 - b) * f).toInt())
    }

    fun round(
        color: Int,
        r: Int,
        stroke: Int = 0,
        strokeColor: Int = 0,
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpf(r.toFloat())
            if (stroke > 0) setStroke(stroke, strokeColor)
        }

    fun elevate(
        v: View,
        dpElev: Int,
        radius: Int,
    ) {
        v.elevation = dpf(dpElev.toFloat())
        v.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    o: Outline,
                ) {
                    o.setRoundRect(0, 0, view.width, view.height, dpf(radius.toFloat()))
                    o.alpha = 0.9f
                }
            }
        try {
            v.outlineAmbientShadowColor = shadow
            v.outlineSpotShadowColor = shadow
        } catch (e: Throwable) {
        }
    }
}
