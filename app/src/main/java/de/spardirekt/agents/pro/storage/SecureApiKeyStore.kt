package de.spardirekt.agents.pro.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureApiKeyStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            "veo_prompt_pro_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasKey(): Boolean = !getKey().isNullOrBlank()

    fun getKey(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun saveKey(apiKey: String) {
        prefs.edit().putString(KEY, apiKey.trim()).apply()
    }

    fun removeKey() {
        prefs.edit().remove(KEY).apply()
    }

    fun maskedPreview(): String {
        val key = getKey() ?: return ""
        if (key.length <= 8) return "••••••••"
        return key.take(4) + "••••" + key.takeLast(4)
    }

    companion object {
        private const val KEY = "openai_api_key"
    }
}
