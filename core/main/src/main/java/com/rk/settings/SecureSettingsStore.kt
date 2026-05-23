package com.rk.settings

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureSettingsStore {
    private const val KEYSTORE_ALIAS = "xed_secure_settings_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private var prefs: SharedPreferences? = null
    private var keyStore: KeyStore? = null
    private var secretKey: SecretKey? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("xed_secure_prefs", Context.MODE_PRIVATE)
        keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore!!.containsAlias(KEYSTORE_ALIAS)) {
            secretKey = (keyStore!!.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            secretKey = keyGenerator.generateKey()
        }

        migrateFromPlainPrefs(context)
    }

    private fun migrateFromPlainPrefs(context: Context) {
        val plainPrefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val keysToMigrate = listOf("ai_api_key", "git_password", "git_username")
        var migrated = false
        for (key in keysToMigrate) {
            if (contains(key)) continue
            val plainValue = plainPrefs.getString(key, null)
            if (!plainValue.isNullOrBlank()) {
                put(key, plainValue)
                migrated = true
            }
        }
        if (migrated) {
            plainPrefs.edit().remove("ai_api_key").remove("git_password").remove("git_username").apply()
        }
    }

    fun put(key: String, value: String) {
        val encrypted = encrypt(value) ?: return
        prefs!!.edit().putString(key, encrypted).apply()
    }

    fun get(key: String, default: String = ""): String {
        val encrypted = prefs!!.getString(key, null) ?: return default
        return decrypt(encrypted) ?: default
    }

    fun contains(key: String): Boolean = prefs!!.contains(key)

    fun remove(key: String) {
        prefs!!.edit().remove(key).apply()
    }

    fun clear() {
        prefs!!.edit().clear().apply()
    }

    private fun encrypt(plaintext: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = iv + encryptedBytes
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("SecureSettingsStore", "encrypt failed", e)
            null
        }
    }

    private fun decrypt(encoded: String): String? {
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = combined.copyOfRange(0, 12)
            val encryptedBytes = combined.copyOfRange(12, combined.size)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("SecureSettingsStore", "decrypt failed", e)
            null
        }
    }
}
