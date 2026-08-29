package com.rin.repairagent.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the AI API key encrypted with an AES key held in Android Keystore.
 * Plaintext key is never persisted. Logs must never contain the key value.
 */
class ApiKeyVault(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasKey(): Boolean = !prefs.getString(KEY_CIPHERTEXT, null).isNullOrBlank()

    fun getProvider(): String = prefs.getString(KEY_PROVIDER, "OPENAI") ?: "OPENAI"

    fun getMaskedKey(): String {
        val last4 = prefs.getString(KEY_LAST4, null) ?: return "••••"
        return "••••••••$last4"
    }

    fun getLastFour(): String = prefs.getString(KEY_LAST4, "") ?: ""

    /** Decrypts the stored key for in-memory API use only. Never log the return value. */
    fun unlockKey(): String? {
        val ciphertextB64 = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP))
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            val plain = cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP))
            String(plain, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun saveKey(apiKey: String, provider: String) {
        require(apiKey.isNotBlank()) { "API key is blank" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(apiKey.toByteArray(StandardCharsets.UTF_8))
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(KEY_LAST4, apiKey.takeLast(4))
            .putString(KEY_PROVIDER, provider)
            .apply()
    }

    fun deleteKey() {
        prefs.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .remove(KEY_LAST4)
            .remove(KEY_PROVIDER)
            .apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null)
        if (existing is SecretKey) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "rin_repair_agent_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "rin_secure_prefs"
        private const val KEY_CIPHERTEXT = "api_key_ciphertext"
        private const val KEY_IV = "api_key_iv"
        private const val KEY_LAST4 = "api_key_last4"
        private const val KEY_PROVIDER = "api_provider"
    }
}
