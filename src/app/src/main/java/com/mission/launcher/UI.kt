package com.mission.launcher

import android.content.Context
import android.graphics.*
import android.text.TextUtils
import android.view.*
import android.widget.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ---------- small view helpers ----------
fun Context.text(
    s: String,
    sp: Float = 14f,
    weight: Int = 0,
    color: Int = T.text,
    lines: Int = 0,
): TextView =
    TextView(this).apply {
        text = s
        setTextSize(TypedValueSp, sp * T.ts)
        setTextColor(color)
        typeface = T.font(weight)
        includeFontPadding = false
        setLineSpacing(0f, 1.18f)
        if (lines > 0) {
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
        }
    }

const val TypedValueSp = android.util.TypedValue.COMPLEX_UNIT_SP

fun TextView.tabular(): TextView {
    fontFeatureSettings = "tnum"
    return this
}

fun LinearLayout.vertical(): LinearLayout {
    orientation = LinearLayout.VERTICAL
    return this
}

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(center: Boolean = true): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        if (center) gravity = Gravity.CENTER_VERTICAL
    }

fun lp(
    w: Int = -1,
    h: Int = -2,
    top: Int = 0,
    wt: Float = 0f,
): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(w, h, wt).apply {
        topMargin = T.dp(top)
    }

fun lpm(
    w: Int,
    h: Int,
    l: Int = 0,
    t: Int = 0,
    r: Int = 0,
    b: Int = 0,
    wt: Float = 0f,
): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(
        w,
        h,
        wt,
    ).apply {
        setMargins(T.dp(l), T.dp(t), T.dp(r), T.dp(b))
    }

fun LinearLayout.addTo(
    v: View,
    top: Int = 0,
    w: Int = -1,
    h: Int = -2,
) {
    addView(v, lp(w, h, top))
}

fun View.padDp(
    l: Int,
    t: Int,
    r: Int,
    b: Int,
) = setPadding(T.dp(l), T.dp(t), T.dp(r), T.dp(b))

fun Context.spacer(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, T.dp(h)) }

fun Context.hspace(w: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(T.dp(w), 1) }

fun Context.flex(): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

fun Context.divider(): View =
    View(this).apply {
        setBackgroundColor(T.line)
        layoutParams = LinearLayout.LayoutParams(-1, max(1, T.dpFixed(1) / 2 + 1))
    }

fun Context.icon(
    n: String,
    col: Int = T.text2,
    size: Int = 20,
    stroke: Float = 1.8f,
) = IconView(this, n, col, size, stroke)

/** Scale + haptic feedback, then run the action. Works for any view. */
fun View.tap(
    haptic: Boolean = true,
    f: () -> Unit,
) {
    isClickable = true
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        false
    }
    setOnClickListener {
        if (haptic) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        f()
    }
}

/** A white iOS-style rounded card with soft shadow. */
class Card(c: Context, val radius: Int = 24) : LinearLayout(c) {
    init {
        orientation = VERTICAL
        background = T.round(T.card, radius)
        padDp(18, 16, 18, 16)
        T.elevate(this, 3, radius)
        clipChildren = false
        clipToPadding = false
    }
}

fun Context.cardHeader(
    icon: String,
    title: String,
    tint: Int = T.accent,
    trailing: View? = null,
): LinearLayout =
    hbox().apply {
        val chip =
            FrameLayout(context).apply {
                background = T.round(T.withAlpha(tint, 0.16f), 9)
                addView(IconView(context, icon, T.textAccent(tint), 16, 1.9f), FrameLayout.LayoutParams(T.dp(16), T.dp(16), Gravity.CENTER))
            }
        addView(chip, LinearLayout.LayoutParams(T.dp(30), T.dp(30)))
        addView(text(title, 15f, 2, T.text), lpm(-2, -2, 10, 0, 0, 0, 1f))
        if (trailing != null) addView(trailing)
    }

fun Context.pill(
    label: String,
    icon: String? = null,
    filled: Boolean = false,
    color: Int = T.accent,
    f: () -> Unit,
): LinearLayout =
    hbox().apply {
        background = T.round(if (filled) color else T.inset, 14)
        padDp(12, 8, 14, 8)
        gravity = Gravity.CENTER
        val foreground = if (filled) T.contentColor(color) else T.text
        if (icon != null) {
            addView(IconView(context, icon, if (filled) foreground else T.text2, 15, 2f))
            addView(hspace(6))
        }
        addView(text(label, 12.5f, 2, foreground))
        if (icon == null) padDp(14, 8, 14, 8)
        tap(true, f)
    }

fun Context.iconBtn(
    icon: String,
    col: Int = T.text2,
    size: Int = 36,
    f: () -> Unit,
): FrameLayout =
    FrameLayout(this).apply {
        background = T.round(T.inset, 12)
        addView(IconView(context, icon, T.textAccent(col), 18, 1.9f), FrameLayout.LayoutParams(T.dp(18), T.dp(18), Gravity.CENTER))
        minimumWidth = T.dp(size)
        minimumHeight = T.dp(size)
        layoutParams = LinearLayout.LayoutParams(T.dp(size), T.dp(size))
        tap(true, f)
    }

fun Context.chipRow(vararg v: View): HorizontalScrollView =
    HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(LinearLayout(context).apply { v.forEachIndexed { i, x -> addView(x, lpm(-2, -2, if (i == 0) 0 else 8, 0, 0, 0)) } })
    }

/** Collapsible section: big data stays hidden until the header is tapped. */
class Collapsible(
    c: Context,
    title: String,
    sub: String = "",
    startOpen: Boolean = false,
    val key: String? = null,
    build: LinearLayout.() -> Unit,
) : LinearLayout(c) {
    private val body =
        LinearLayout(c).apply {
            orientation = VERTICAL
            visibility = GONE
        }
    private val chev = IconView(c, "chev", T.text3, 16, 2f)
    private var open = false
    private var built = false
    private val builder = build

    init {
        orientation = VERTICAL
        val head =
            c.hbox().apply {
                padDp(0, 10, 0, 10)
                addView(c.text(title, 13.5f, 2, T.text))
                if (sub.isNotEmpty()) addView(c.text("  $sub", 12f, 0, T.text3))
                addView(c.flex())
                addView(chev)
            }
        addView(head)
        addView(body)
        head.tap { toggle() }
        if (startOpen || (key != null && Store.b("open_$key"))) toggle(false)
    }

    fun toggle(animate: Boolean = true) {
        open = !open
        if (open && !built) {
            built = true
            body.builder()
        }
        body.visibility = if (open) VISIBLE else GONE
        chev.animate().rotation(if (open) 90f else 0f).setDuration(if (animate) 180 else 0).start()
        if (key != null) Store.put("open_$key", open)
    }

    fun rebuild() {
        body.removeAllViews()
        built = false
        if (open) {
            built = true
            body.builder()
        }
    }
}

// ---------- charts ----------
class Row(val label: String, val value: Float, val color: Int, val right: String = "", val tag: Any? = null)

/** Horizontal colour-coded progress rows (value 0..1). */
class HBars(c: Context, var rows: List<Row>, val rowDp: Int = 30) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var onRow: ((Row) -> Unit)? = null

    override fun onMeasure(
        w: Int,
        h: Int,
    ) = setMeasuredDimension(MeasureSpec.getSize(w), max(1, rows.size) * T.dp(rowDp))

    override fun onDraw(cv: Canvas) {
        val rh = T.dpf(rowDp.toFloat())
        val lw = width * 0.40f
        val vw = T.dpf(54f)
        val bh = T.dpf(8f)
        p.typeface = T.font(1)
        p.textSize = T.dpf(12f * T.ts)
        rows.forEachIndexed { i, r ->
            val y = i * rh
            val cy = y + rh / 2
            p.style = Paint.Style.FILL
            p.color = T.text2
            p.textAlign = Paint.Align.LEFT
            cv.drawText(
                TextUtils.ellipsize(r.label, android.text.TextPaint(p), lw - T.dpf(8f), TextUtils.TruncateAt.END).toString(),
                0f,
                cy + p.textSize * 0.35f,
                p,
            )
            val x0 = lw
            val x1 = width - vw
            p.color = T.inset
            cv.drawRoundRect(x0, cy - bh / 2, x1, cy + bh / 2, bh, bh, p)
            val f = r.value.coerceIn(0f, 1f)
            if (f > 0f) {
                p.color = r.color
                cv.drawRoundRect(x0, cy - bh / 2, x0 + max(bh, (x1 - x0) * f), cy + bh / 2, bh, bh, p)
            }
            p.color = T.text
            p.textAlign = Paint.Align.RIGHT
            p.typeface = T.font(2)
            cv.drawText(r.right.ifEmpty { "${(f * 100).toInt()}%" }, width.toFloat(), cy + p.textSize * 0.35f, p)
            p.typeface = T.font(1)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (onRow == null) return false
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val i = (e.y / T.dpf(rowDp.toFloat())).toInt()
            rows.getOrNull(i)?.let {
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onRow?.invoke(it)
            }
        }
        return true
    }
}

/** Circular progress ring with centre text. */
class Ring(c: Context, var progress: Float, var color: Int, var big: String, var small: String = "", val strokeDp: Int = 9) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var anim = 0f

    init {
        animate().setDuration(0)
    }

    fun set(
        pr: Float,
        b: String,
        s: String = "",
    ) {
        progress = pr
        big = b
        small = s
        invalidate()
    }

    override fun onDraw(cv: Canvas) {
        val sw = T.dpf(strokeDp.toFloat())
        val r = RectF(sw / 2, sw / 2, width - sw / 2, height - sw / 2)
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.strokeCap = Paint.Cap.ROUND
        p.color = T.inset
        cv.drawArc(r, 0f, 360f, false, p)
        p.color = color
        cv.drawArc(r, -90f, 360f * progress.coerceIn(0f, 1f), false, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.color = T.text
        p.typeface = T.font(3)
        p.textSize = width * (if (small.isEmpty()) 0.26f else 0.24f)
        cv.drawText(big, width / 2f, height / 2f + (if (small.isEmpty()) p.textSize * 0.35f else p.textSize * 0.1f), p)
        if (small.isNotEmpty()) {
            p.typeface = T.font(1)
            p.color = T.text3
            p.textSize = width * 0.12f
            cv.drawText(small, width / 2f, height / 2f + width * 0.2f, p)
        }
    }
}

/** Pseudo-3D donut: tilted ellipse with extruded side wall; slices are tappable. */
class Donut3D(c: Context) : View(c) {
    class Slice(val label: String, val value: Float, val color: Int, val prog: Float = 1f, val tag: Any? = null)

    var slices: List<Slice> = emptyList()
    var centerTop = ""
    var centerBottom = ""
    var selected = -1
    var onPick: ((Int) -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val ratio = 0.56f
    private val depth get() = T.dpf(16f)

    override fun onMeasure(
        w: Int,
        h: Int,
    ) {
        val ww = MeasureSpec.getSize(w)
        setMeasuredDimension(ww, (ww * 0.56f + depth + T.dpf(18f)).toInt())
    }

    private fun geom(): FloatArray {
        val pad = T.dpf(10f)
        val rx = width / 2f - pad
        val ry = rx * 0.56f
        return floatArrayOf(width / 2f, pad + ry, rx, ry)
    }

    private fun slicePath(
        g: FloatArray,
        a0: Float,
        sw: Float,
        off: Float,
        dxy: FloatArray,
    ): Path {
        path.reset()
        val cx = g[0] + dxy[0]
        val cy = g[1] + dxy[1] + off
        path.arcTo(RectF(cx - g[2], cy - g[3], cx + g[2], cy + g[3]), a0, sw, true)
        val ix = g[2] * ratio
        val iy = g[3] * ratio
        path.arcTo(RectF(cx - ix, cy - iy, cx + ix, cy + iy), a0 + sw, -sw, false)
        path.close()
        return path
    }

    override fun onDraw(cv: Canvas) {
        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return
        val g = geom()
        val gap = if (slices.size > 1) 1.6f else 0f
        var a = -90f
        val items = ArrayList<FloatArray>()
        slices.forEachIndexed {
                i,
                s,
            ->
            val sw = 360f * s.value / total
            items.add(floatArrayOf(a + gap / 2, max(0.5f, sw - gap)))
            a += sw
        }
        // side wall (draw all, deepest first)
        var d = depth
        while (d > 0f) {
            slices.forEachIndexed { i, s ->
                val it = items[i]
                val dxy = pop(i, it)
                p.style = Paint.Style.FILL
                p.color = T.darken(s.color, 0.62f)
                p.alpha = 255
                cv.drawPath(slicePath(g, it[0], it[1], d, dxy), p)
            }
            d -= T.dpf(2f).coerceAtLeast(1f)
        }
        // top face
        slices.forEachIndexed { i, s ->
            val it = items[i]
            val dxy = pop(i, it)
            p.style = Paint.Style.FILL
            p.color = s.color
            p.alpha = (255 * (0.45f + 0.55f * s.prog.coerceIn(0f, 1f))).toInt()
            cv.drawPath(slicePath(g, it[0], it[1], 0f, dxy), p)
            p.alpha = 255
            p.style = Paint.Style.STROKE
            p.strokeWidth = T.dpf(1f)
            p.color = 0x40FFFFFF
            cv.drawPath(slicePath(g, it[0], it[1], 0f, dxy), p)
        }
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.color = T.text
        p.typeface = T.font(3)
        p.textSize = T.dpf(22f * T.ts)
        cv.drawText(centerTop, g[0], g[1] + p.textSize * 0.15f, p)
        p.color = T.text3
        p.typeface = T.font(1)
        p.textSize = T.dpf(11f * T.ts)
        cv.drawText(centerBottom, g[0], g[1] + T.dpf(20f * T.ts), p)
    }

    private fun pop(
        i: Int,
        it: FloatArray,
    ): FloatArray {
        if (i != selected) return floatArrayOf(0f, 0f)
        val mid = Math.toRadians((it[0] + it[1] / 2).toDouble())
        val k = T.dpf(7f)
        return floatArrayOf(cos(mid).toFloat() * k, sin(mid).toFloat() * k * 0.56f)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val g = geom()
        val dx = (e.x - g[0]) / g[2]
        val dy = (e.y - g[1]) / g[3]
        val rr = sqrt(dx * dx + dy * dy)
        if (rr > 1.02f || rr < ratio) return true
        var ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        if (ang < 0) ang += 360f
        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        var acc = 0f
        for ((i, s) in slices.withIndex()) {
            acc += 360f * s.value / total
            if (ang <= acc) {
                selected = if (selected == i) -1 else i
                invalidate()
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onPick?.invoke(selected)
                break
            }
        }
        return true
    }
}

/** Stacked day columns, colour-coded by subject (study history, 2-D). */
class StackBars(c: Context, val days: List<String>, val stacks: List<List<Pair<Int, Float>>>, val unit: String = "h") : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var heightDp = 150

    override fun onMeasure(
        w: Int,
        h: Int,
    ) = setMeasuredDimension(MeasureSpec.getSize(w), T.dp(heightDp))

    override fun onDraw(cv: Canvas) {
        val n = days.size
        if (n == 0) return
        val top = T.dpf(8f)
        val bot = height - T.dpf(22f)
        val mx = max(1f, stacks.maxOfOrNull { s -> s.sumOf { it.second.toDouble() }.toFloat() } ?: 1f)
        val slot = width.toFloat() / n
        val bw = min(slot * 0.56f, T.dpf(34f))
        p.textSize = T.dpf(10.5f * T.ts)
        p.typeface = T.font(1)
        // gridlines
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = T.line
        for (g in 0..2) {
            val y = bot - (bot - top) * g / 2f
            cv.drawLine(0f, y, width.toFloat(), y, p)
        }
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            var y = bot
            stacks[i].forEach {
                    (col, v) ->
                val h = (bot - top) * v / mx
                if (h > 0.5f) {
                    p.color = col
                    cv.drawRoundRect(cx - bw / 2, y - h, cx + bw / 2, y, T.dpf(4f), T.dpf(4f), p)
                    y -= h + T.dpf(1.5f)
                }
            }
            p.color = T.text3
            p.textAlign = Paint.Align.CENTER
            cv.drawText(days[i], cx, height - T.dpf(6f), p)
            val tot = stacks[i].sumOf { it.second.toDouble() }.toFloat()
            if (tot > 0f) {
                p.color = T.text2
                cv.drawText(fmt(tot) + unit, cx, y - T.dpf(4f), p)
            }
        }
    }

    private fun fmt(v: Float) = if (v >= 10f || v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)
}

class LineChart(c: Context, val xs: List<String>, val ys: List<Float>, val color: Int, val maxY: Float? = null) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var heightDp = 130

    override fun onMeasure(
        w: Int,
        h: Int,
    ) = setMeasuredDimension(MeasureSpec.getSize(w), T.dp(heightDp))

    override fun onDraw(cv: Canvas) {
        if (ys.isEmpty()) return
        val l = T.dpf(4f)
        val r = width - T.dpf(8f)
        val t = T.dpf(14f)
        val b = height - T.dpf(22f)
        val mx = maxY ?: max(1f, ys.max())
        val mn = min(0f, ys.min())

        fun X(i: Int) = if (ys.size == 1) (l + r) / 2 else l + (r - l) * i / (ys.size - 1)

        fun Y(v: Float) = b - (b - t) * (v - mn) / max(1f, mx - mn)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = T.line
        for (g in 0..2) {
            val y = b - (b - t) * g / 2f
            cv.drawLine(l, y, r, y, p)
        }
        val path = Path()
        ys.forEachIndexed { i, v ->
            if (i == 0) {
                path.moveTo(X(i), Y(v))
            } else {
                val px = X(i - 1)
                val py = Y(ys[i - 1])
                val cx = (px + X(i)) / 2
                path.cubicTo(cx, py, cx, Y(v), X(i), Y(v))
            }
        }
        val fill = Path(path)
        fill.lineTo(X(ys.size - 1), b)
        fill.lineTo(X(0), b)
        fill.close()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, t, 0f, b, T.withAlpha(color, 0.30f), T.withAlpha(color, 0f), Shader.TileMode.CLAMP)
        cv.drawPath(fill, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = T.dpf(2.4f)
        p.color = color
        p.strokeCap = Paint.Cap.ROUND
        cv.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.textSize = T.dpf(10.5f * T.ts)
        p.typeface = T.font(1)
        ys.forEachIndexed {
                i,
                v,
            ->
            p.color = T.cardSolid
            cv.drawCircle(X(i), Y(v), T.dpf(4.2f), p)
            p.color = color
            cv.drawCircle(X(i), Y(v), T.dpf(2.6f), p)
            p.color = T.text3
            if (xs.size <= 8 || i % 2 == 0) cv.drawText(xs.getOrElse(i) { "" }, X(i), height - T.dpf(6f), p)
        }
    }
}

/** iOS-like segmented control. */
class Segmented(c: Context, val options: List<String>, var sel: Int, val onChange: (Int) -> Unit) : LinearLayout(c) {
    init {
        orientation = HORIZONTAL
        background = T.round(T.inset, 12)
        padDp(3, 3, 3, 3)
        render()
    }

    private fun render() {
        removeAllViews()
        options.forEachIndexed { i, o ->
            val t =
                context.text(o, 12.5f, 2, if (i == sel) T.text else T.text3).apply {
                    gravity = Gravity.CENTER
                    padDp(10, 7, 10, 7)
                    if (i == sel) {
                        background = T.round(T.cardSolid, 10)
                        T.elevate(this, 1, 10)
                    }
                }
            t.setOnClickListener {
                sel = i
                render()
                onChange(i)
            }
            addView(t, LayoutParams(0, -2, 1f))
        }
    }
}

fun Context.statBlock(
    value: String,
    label: String,
    color: Int = T.text,
): LinearLayout =
    vbox().apply {
        addView(text(value, 22f, 3, T.textAccent(color)).tabular())
        addView(text(label, 11.5f, 1, T.text3), lp(-2, -2, 2))
    }

fun Context.emptyNote(s: String): TextView = text(s, 13f, 0, T.text3).apply { padDp(0, 6, 0, 6) }

fun android.app.Activity.systemBars(bg: Int? = null) {
    window.setDecorFitsSystemWindows(false)
    if (bg != null) {
        window.statusBarColor = bg
        window.navigationBarColor = bg
    } else {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
    try {
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    } catch (e: Throwable) {
    }
    val apply = {
        try {
            val m = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (T.dark) 0 else m, m)
        } catch (
            e: Throwable,
        ) {
        }
    }
    try {
        window.decorView.post { apply() }
    } catch (e: Throwable) {
    }
    try {
        window.decorView.postDelayed({ apply() }, 300)
    } catch (e: Throwable) {
    }
}

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun fmtMins(m: Int): String = if (m < 60) "${m}m" else "${m / 60}h ${"%02d".format(m % 60)}m"
