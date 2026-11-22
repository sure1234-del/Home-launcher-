package com.mission.launcher

import android.content.Context
import android.os.CancellationSignal
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.security.MessageDigest

object Pin {
    fun isSet() = Store.s("pin_hash").isNotEmpty()

    fun len() = Store.i("pin_len", 4).coerceIn(4, 8)

    private fun hash(p: String): String {
        val salt = Store.s("pin_salt").ifEmpty { Store.newId().also { Store.put("pin_salt", it) } }
        return MessageDigest.getInstance("SHA-256").digest((salt + p).toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun set(p: String) {
        Store.put("pin_len", p.length)
        Store.put("pin_hash", hash(p))
        grant()
    }

    fun clear() {
        Store.put("pin_hash", null)
        Store.put("pin_fail", 0)
    }

    fun check(p: String): Boolean {
        if (lockLeft() > 0) return false
        val ok = hash(p) == Store.s("pin_hash")
        if (ok) {
            Store.put("pin_fail", 0)
            grant()
        } else {
            val f = Store.i("pin_fail") + 1
            Store.put("pin_fail", f)
            if (f >= 5) Store.put("pin_lock_until", System.currentTimeMillis() + 30000L * (f - 4))
        }
        return ok
    }

    fun lockLeft(): Int = ((Store.l("pin_lock_until") - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)

    fun unlocked() = System.currentTimeMillis() < Store.l("pin_until")

    fun grant() {
        Store.put("pin_until", System.currentTimeMillis() + Store.i("pin_window", 10) * 60000L)
    }

    fun relock() {
        Store.put("pin_until", 0L)
    }
}

/** Full-screen keypad (dots + round keys). mode: "verify" calls back with the entered PIN, "new" collects a new one. */
class PinPad(
    c: Context,
    val title: String,
    val length: Int,
    val allowCancel: Boolean,
    val onEntered: (String, PinPad) -> Unit,
    val onCancel: () -> Unit,
    val onBio: (() -> Unit)? = null,
) : FrameLayout(c) {
    private var buf = ""
    private val dots = LinearLayout(c)
    private val msg = c.text("", 13f, 1, T.textAccent(T.danger))
    private val head = c.text(title, 20f, 3)

    init {
        setBackgroundColor(T.page)
        isClickable = true
        isFocusable = true
        val col = c.vbox().apply { gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(c.icon("lock", T.text2, 26, 1.8f))
        col.addView(head, lp(-2, -2, 12))
        col.addView(msg, lp(-2, -2, 8))
        dots.gravity = Gravity.CENTER
        col.addView(dots, lp(-2, -2, 18))
        drawDots()
        val pad = c.vbox()
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        for (r in 0 until 4) {
            val row = c.hbox()
            for (k in 0 until 3) {
                row.addView(key(c, keys[r * 3 + k]), lpm(T.dp(76), T.dp(76), 8, 6, 8, 6))
            }
            pad.addView(row)
        }
        col.addView(pad, lp(-2, -2, 22))
        if (allowCancel) {
            col.addView(
                c.text("Cancel", 14f, 2, T.text2).apply {
                    padDp(20, 10, 20, 10)
                    tap { onCancel() }
                },
                lp(-2, -2, 6),
            )
        }
        addView(col, LayoutParams(-2, -2, Gravity.CENTER))
    }

    private fun drawDots() {
        dots.removeAllViews()
        for (i in 0 until length) dots.addView(
            View(context).apply {
                background = T.round(if (i < buf.length) T.text else T.line, 8)
            },
            lpm(T.dp(13), T.dp(13), 7, 0, 7, 0),
        )
    }

    private fun key(
        c: Context,
        k: String,
    ): View {
        val fl = FrameLayout(c).apply { background = T.round(if (k == "bio" && onBio == null) 0 else T.inset, 38) }
        when (k) {
            "del" -> fl.addView(c.icon("chevl", T.text2, 22, 2f), FrameLayout.LayoutParams(T.dp(22), T.dp(22), Gravity.CENTER))
            "bio" ->
                if (onBio != null) {
                    fl.addView(
                        c.icon("shield", T.text2, 22, 1.8f),
                        FrameLayout.LayoutParams(T.dp(22), T.dp(22), Gravity.CENTER),
                    )
                }
            else -> fl.addView(c.text(k, 26f, 1), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        if (k == "bio" && onBio == null) return fl
        fl.tap(false) {
            fl.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            when (k) {
                "del" -> if (buf.isNotEmpty()) buf = buf.dropLast(1)
                "bio" -> onBio?.invoke()
                else ->
                    if (buf.length < length) {
                        buf += k
                        if (buf.length == length) {
                            val e = buf
                            postDelayed({ onEntered(e, this) }, 90)
                        }
                    }
            }
            drawDots()
        }
        return fl
    }

    fun error(m: String) {
        buf = ""
        drawDots()
        msg.text = m
        performHapticFeedback(HapticFeedbackConstants.REJECT)
        animate().translationX(14f).setDuration(60).withEndAction { animate().translationX(0f).setDuration(80).start() }.start()
    }

    fun reset(newTitle: String? = null) {
        buf = ""
        drawDots()
        msg.text = ""
        if (newTitle != null) head.text = newTitle
    }
}

object Bio {
    fun available(c: Context): Boolean =
        try {
            c.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT) || c.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FACE)
        } catch (
            e: Exception,
        ) {
            false
        }

    fun prompt(
        c: Context,
        ok: () -> Unit,
    ) {
        try {
            val ex = c.mainExecutor
            val p =
                android.hardware.biometrics.BiometricPrompt.Builder(
                    c,
                ).setTitle("Unlock Mission").setNegativeButton("Use PIN", ex) { _, _ -> }.build()
            p.authenticate(
                CancellationSignal(),
                ex,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(r: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                        Pin.grant()
                        ok()
                    }
                },
            )
        } catch (e: Throwable) {
            Log.e("bio", e)
        }
    }
}
