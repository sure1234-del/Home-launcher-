package com.mission.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts provider credentials with a non-exportable Android Keystore key. */
object SecureStore {
    private const val PREFS = "secure_secrets"
    private const val ALIAS = "mission_launcher_secrets_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Volatile var lastError: String? = null
        private set

    fun get(name: String): String {
        return try {
            val encoded =
                Store.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, null)
                    ?: run {
                        lastError = null
                        return ""
                    }
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > 12) { "Invalid encrypted credential" }
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8).also { lastError = null }
        } catch (e: Exception) {
            lastError = "A saved key could not be decrypted. Enter it again in Settings."
            Log.e("secure-store-read", e)
            ""
        }
    }

    fun put(
        name: String,
        value: String,
    ) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val saved =
                Store.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(name, Base64.encodeToString(payload, Base64.NO_WRAP)).commit()
            check(saved) { "Encrypted credential could not be saved" }
            lastError = null
        } catch (e: Exception) {
            lastError = "The key could not be saved securely."
            Log.e("secure-store-write", e)
            throw IllegalStateException("Encrypted credential could not be saved", e)
        }
    }

    fun eraseAll() {
        try {
            val saved = Store.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
            check(saved) { "Encrypted credentials could not be cleared" }
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
            lastError = null
        } catch (e: Exception) {
            lastError = "Encrypted credentials could not be cleared."
            Log.e("secure-store-erase", e)
            throw IllegalStateException("Encrypted credentials could not be cleared", e)
        }
    }

    fun migrateLegacy() {
        val zite = Store.s("zite_key")
        val gemini = Store.s("gemini_key")
        val aiKeys =
            try {
                JSONObject(Store.s("ai_keys", "{}"))
            } catch (e: Exception) {
                Log.e("secure-store-migrate", e)
                JSONObject()
            }
        try {
            if (zite.isNotBlank()) put("zite", zite)
            val keys = aiKeys.keys()
            while (keys.hasNext()) {
                val provider = keys.next()
                val secret = aiKeys.optString(provider)
                if (secret.isNotBlank()) put("ai:$provider", secret)
            }
            if (gemini.isNotBlank() && get("ai:gemini").isBlank()) put("ai:gemini", gemini)
            if (zite.isNotBlank()) Store.put("zite_key", null)
            if (gemini.isNotBlank()) Store.put("gemini_key", null)
            if (aiKeys.length() > 0) Store.put("ai_keys", null)
        } catch (e: Exception) {
            Log.e("secure-store-migrate", e)
            Store.put("zite_key", null)
            Store.put("gemini_key", null)
            Store.put("ai_keys", null)
            Store.flushNow()
            lastError = "Older API keys could not be migrated. Re-enter them in Settings."
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
