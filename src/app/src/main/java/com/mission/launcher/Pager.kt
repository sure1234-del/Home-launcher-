package com.mission.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/** Horizontal pager for home pages (framework only). Vertical scrolling inside pages keeps working. */
class Pager(c: Context) : FrameLayout(c) {
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private val minFling = ViewConfiguration.get(c).scaledMinimumFlingVelocity * 4
    var pos = 0f
        private set
    var index = 0
        private set
    var onPos: ((Float) -> Unit)? = null
    var onSettled: ((Int) -> Unit)? = null
    private var vt: VelocityTracker? = null
    private var dx0 = 0f
    private var dy0 = 0f
    private var drag = false
    private var startPos = 0f
    private var anim: ValueAnimator? = null
    val count get() = childCount
    val busy get() = drag || (anim?.isRunning == true)

    fun setPages(
        v: List<View>,
        keep: Int = 0,
    ) {
        removeAllViews()
        v.forEach { addView(it, LayoutParams(-1, -1)) }
        index = keep.coerceIn(0, maxOf(0, v.size - 1))
        pos = index.toFloat()
        applyPos()
    }

    override fun onLayout(
        ch: Boolean,
        l: Int,
        t: Int,
        r: Int,
        b: Int,
    ) {
        super.onLayout(ch, l, t, r, b)
        applyPos()
    }

    private fun applyPos() {
        val w = width.toFloat()
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            v.translationX = (i - pos) * w
            v.visibility = if (abs(i - pos) < 1.01f) VISIBLE else INVISIBLE
        }
        onPos?.invoke(pos)
    }

    fun goTo(
        i: Int,
        animate: Boolean = true,
    ) {
        val t = i.coerceIn(0, maxOf(0, childCount - 1))
        index = t
        anim?.cancel()
        if (!animate) {
            pos = t.toFloat()
            applyPos()
            onSettled?.invoke(t)
            return
        }
        anim =
            ValueAnimator.ofFloat(pos, t.toFloat()).apply {
                duration = 260
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener {
                    pos = it.animatedValue as Float
                    applyPos()
                }
                addListener(
                    object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: android.animation.Animator) {
                            onSettled?.invoke(t)
                        }
                    },
                )
                start()
            }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dx0 = e.x
                dy0 = e.y
                drag = false
                anim?.cancel()
                startPos = pos
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dx = e.x - dx0
                val dy = e.y - dy0
                if (!drag && abs(dx) > slop * 1.5f && abs(dx) > abs(dy) * 1.4f) {
                    drag = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return drag
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        vt?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val w = width.toFloat().coerceAtLeast(1f)
                var np = startPos - (e.x - dx0) / w
                val max = (childCount - 1).toFloat()
                if (np < 0f) np *= 0.35f
                if (np > max) np = max + (np - max) * 0.35f
                pos = np
                applyPos()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.computeCurrentVelocity(1000)
                val vx = vt?.xVelocity ?: 0f
                vt?.recycle()
                vt = null
                var t = Math.round(pos)
                if (abs(vx) > minFling) t = if (vx < 0) Math.ceil(pos.toDouble() - 0.0001).toInt() else Math.floor(pos.toDouble() + 0.0001).toInt()
                if (abs(vx) > minFling && abs(pos - startPos) < 0.02f) t = index
                goTo(t.coerceIn(0, childCount - 1))
                drag = false
            }
        }
        return true
    }
}
