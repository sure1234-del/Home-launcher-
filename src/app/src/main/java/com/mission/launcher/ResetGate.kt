package com.mission.launcher

import android.app.Activity
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object ResetGate {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val MAX_ATTEMPTS = 5
    private const val HMAC_ALIAS = "mission_reset_verifier_v1"

    fun isSet(): Boolean =
        (Store.s("reset_verifier").isNotBlank() && Store.s("reset_salt").isNotBlank()) ||
            (Store.s("reset_hash").isNotBlank() && Store.s("reset_salt").isNotBlank())

    fun ensureSet(
        activity: Activity,
        onReady: () -> Unit,
    ) {
        if (isSet()) onReady() else setPassword(activity, onReady)
    }

    fun verify(
        activity: Activity,
        onAuthorized: () -> Unit,
    ) {
        if (!isSet()) {
            setPassword(activity, onAuthorized)
            return
        }
        val remaining = lockSeconds()
        if (remaining > 0) {
            Dlg.info(activity, "Reset locked", "Try again in $remaining seconds.")
            return
        }
        enterCode(activity, "Enter reset password") { password ->
            if (check(password)) {
                onAuthorized()
            } else {
                val wait = lockSeconds()
                Dlg.info(
                    activity,
                    "Incorrect password",
                    if (wait > 0) "Too many attempts. Try again in $wait seconds." else "Your data was not changed.",
                )
            }
        }
    }

    fun verifyAndBackup(
        activity: Activity,
        onAuthorized: () -> Unit,
    ) {
        verify(activity) {
            Thread {
                val status = Backup.runNow(activity)
                activity.runOnUiThread {
                    if (status.startsWith("Backup saved to ")) {
                        onAuthorized()
                    } else {
                        Dlg.info(activity, "Backup failed", "$status Data was not changed.")
                    }
                }
            }.start()
        }
    }

    private fun setPassword(
        activity: Activity,
        onReady: () -> Unit,
    ) {
        enterCode(activity, "Set reset password (6-12 digits)") { first ->
            if (first.length !in 6..12 || first.any { !it.isDigit() }) {
                Dlg.info(activity, "Invalid password", "Use 6 to 12 digits.")
                setPassword(activity, onReady)
                return@enterCode
            }
            enterCode(activity, "Confirm reset password") { second ->
                if (!MessageDigest.isEqual(first.toByteArray(), second.toByteArray())) {
                    Dlg.info(activity, "Passwords do not match", "The reset password was not saved.")
                    setPassword(activity, onReady)
                } else {
                    if (save(first)) onReady() else Dlg.info(activity, "Could not set password", "Your reset password was not saved.")
                }
            }
        }
    }

    private fun enterCode(
        activity: Activity,
        title: String,
        onEntered: (String) -> Unit,
    ) {
        val input =
            EditText(activity).apply {
                hint = "6-12 digits"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setSingleLine()
                textSize = 18f
            }
        val container =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(T.dp(24), T.dp(8), T.dp(24), 0)
                addView(input, LinearLayout.LayoutParams(-1, -2))
            }
        val dialog =
            Dlg.builder(activity)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("Continue") { _, _ -> onEntered(input.text.toString()) }
                .setNegativeButton("Cancel", null)
                .show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun save(password: String): Boolean {
        try {
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            val derived = derive(password, salt)
            val verifier = hmac(derived)
            Store.put("reset_salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            Store.put("reset_verifier", android.util.Base64.encodeToString(verifier, android.util.Base64.NO_WRAP))
            Store.put("reset_hash", null)
            Store.put("reset_fail", 0)
            Store.put("reset_lock_until", 0L)
            Store.flushNow()
            return true
        } catch (e: Exception) {
            Log.e("reset-password", e)
            return false
        }
    }

    private fun check(password: String): Boolean {
        val verifier = Store.s("reset_verifier")
        val salt =
            try {
                android.util.Base64.decode(Store.s("reset_salt"), android.util.Base64.NO_WRAP)
            } catch (
                e: IllegalArgumentException,
            ) {
                Log.e("reset-password", e)
                return false
            }
        if (verifier.isBlank()) {
            val legacy =
                try {
                    android.util.Base64.decode(Store.s("reset_hash"), android.util.Base64.NO_WRAP)
                } catch (
                    e: IllegalArgumentException,
                ) {
                    Log.e("reset-password", e)
                    return false
                }
            val ok = MessageDigest.isEqual(legacy, derive(password, salt))
            if (ok) {
                save(password)
                Store.put("reset_fail", 0)
                Store.put("reset_lock_until", 0L)
                Store.flushNow()
            } else {
                recordFailure()
            }
            return ok
        }
        val expected =
            try {
                android.util.Base64.decode(verifier, android.util.Base64.NO_WRAP)
            } catch (
                e: IllegalArgumentException,
            ) {
                Log.e("reset-password", e)
                return false
            }
        val actual =
            try {
                hmac(derive(password, salt))
            } catch (e: Exception) {
                Log.e("reset-password", e)
                return false
            }
        val ok = MessageDigest.isEqual(expected, actual)
        if (ok) {
            Store.put("reset_fail", 0)
            Store.put("reset_lock_until", 0L)
        } else {
            recordFailure()
        }
        Store.flushNow()
        return ok
    }

    private fun derive(
        password: String,
        salt: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun hmac(value: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey())
        return mac.doFinal(value)
    }

    private fun recordFailure() {
        val failures = Store.i("reset_fail") + 1
        Store.put("reset_fail", failures)
        if (failures >= MAX_ATTEMPTS) {
            val periods = 1L shl (failures - MAX_ATTEMPTS).coerceAtMost(10)
            Store.put("reset_lock_until", System.currentTimeMillis() + 60_000L * periods)
        }
        Store.flushNow()
    }

    private fun hmacKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(HMAC_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(HMAC_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun lockSeconds(): Int = ((Store.l("reset_lock_until") - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
}
