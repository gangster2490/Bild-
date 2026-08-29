package de.spardirekt.agents.pro.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.spardirekt.agents.pro.model.AppMode
import de.spardirekt.agents.pro.model.CreativeMode
import de.spardirekt.agents.pro.model.VoiceLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("veo_prompt_pro_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val voice = stringPreferencesKey("default_voice")
        val mode = stringPreferencesKey("default_mode")
        val creative = stringPreferencesKey("default_creative")
        val tiktok = booleanPreferencesKey("tiktok_shop_mode")
        val historyFormat = stringPreferencesKey("history_format")
        val model = stringPreferencesKey("openai_model")
        val debugLogs = booleanPreferencesKey("debug_logs")
        val outputLanguage = stringPreferencesKey("output_language")
    }

    data class AppSettings(
        val defaultVoice: VoiceLanguage = VoiceLanguage.DE,
        val defaultMode: AppMode = AppMode.Simple,
        val defaultCreative: CreativeMode = CreativeMode.Auto,
        val tiktokShopMode: Boolean = true,
        val historyFormat: String = "full",
        val model: String = "gpt-4o",
        val debugLogs: Boolean = false,
        val outputLanguage: String = "RU"
    )

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            defaultVoice = runCatching {
                VoiceLanguage.valueOf(p[Keys.voice] ?: VoiceLanguage.DE.name)
            }.getOrDefault(VoiceLanguage.DE),
            defaultMode = runCatching {
                AppMode.valueOf(p[Keys.mode] ?: AppMode.Simple.name)
            }.getOrDefault(AppMode.Simple),
            defaultCreative = runCatching {
                CreativeMode.valueOf(p[Keys.creative] ?: CreativeMode.Auto.name)
            }.getOrDefault(CreativeMode.Auto),
            tiktokShopMode = p[Keys.tiktok] ?: true,
            historyFormat = p[Keys.historyFormat] ?: "full",
            model = p[Keys.model] ?: "gpt-4o",
            debugLogs = p[Keys.debugLogs] ?: false,
            outputLanguage = p[Keys.outputLanguage] ?: "RU"
        )
    }

    suspend fun setVoice(v: VoiceLanguage) {
        context.settingsDataStore.edit { it[Keys.voice] = v.name }
    }

    suspend fun setMode(m: AppMode) {
        context.settingsDataStore.edit { it[Keys.mode] = m.name }
    }

    suspend fun setCreative(c: CreativeMode) {
        context.settingsDataStore.edit { it[Keys.creative] = c.name }
    }

    suspend fun setTiktok(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.tiktok] = on }
    }

    suspend fun setHistoryFormat(fmt: String) {
        context.settingsDataStore.edit { it[Keys.historyFormat] = fmt }
    }

    suspend fun setModel(model: String) {
        context.settingsDataStore.edit { it[Keys.model] = model }
    }

    suspend fun setDebugLogs(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.debugLogs] = on }
    }

    suspend fun setOutputLanguage(lang: String) {
        context.settingsDataStore.edit { it[Keys.outputLanguage] = lang }
    }
}
