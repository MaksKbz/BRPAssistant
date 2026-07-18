package com.brp.assistant.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Хранилище настроек приложения.
 *
 * API-ключи — EncryptedSharedPreferences (AES256-SIV/GCM).
 * Остальные настройки — DataStore.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ── DataStore keys ─────────────────────────────────────────────────────────────────────────
    private val ACTIVE_MODEL_ID       = stringPreferencesKey("active_model_id")
    private val SELECTED_VEHICLE_ID   = stringPreferencesKey("selected_vehicle_id")
    private val SELECTED_VEHICLE_NAME = stringPreferencesKey("selected_vehicle_name")
    private val CUSTOM_MODELS_JSON    = stringPreferencesKey("custom_models_json")
    private val AI_PROVIDER           = stringPreferencesKey("ai_provider")
    private val AI_MODEL_NAME         = stringPreferencesKey("ai_model_name")
    private val AI_SYSTEM_PROMPT      = stringPreferencesKey("ai_system_prompt")
    private val AI_TEMPERATURE        = floatPreferencesKey("ai_temperature")
    private val PURCHASE_DATE         = longPreferencesKey("purchase_date")
    private val APP_THEME             = stringPreferencesKey("app_theme")
    private val CHAT_FORCE_ONLINE     = stringPreferencesKey("chat_force_online")
    private val ONBOARDING_COMPLETED  = stringPreferencesKey("onboarding_completed")

    // ── EncryptedSharedPreferences ──────────────────────────────────────────────
    private companion object {
        const val ENC_PREFS_FILE = "brp_secure_prefs"
        const val KEY_GEMINI    = "gemini_api_key"
        // ⚠️ "grok_api_key" — намеренная опечатка. НЕ ИСПРАВЛЯТЬ!
        const val KEY_GROQ      = "grok_api_key"
        const val TAG           = "SettingsRepo"
    }

    private val encryptedPrefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENC_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init EncryptedSharedPreferences", e)
            null
        }
    }

    private val _geminiApiKey = MutableStateFlow(readEncrypted(KEY_GEMINI))
    val geminiApiKey: Flow<String?> = _geminiApiKey.asStateFlow()

    private val _groqApiKey = MutableStateFlow(readEncrypted(KEY_GROQ))
    val groqApiKey: Flow<String?> = _groqApiKey.asStateFlow()

    private fun readEncrypted(key: String): String? = encryptedPrefs?.getString(key, null)

    // ── DataStore flows ─────────────────────────────────────────────────────────────────────
    val activeModelId: Flow<String?> =
        context.dataStore.data.map { it[ACTIVE_MODEL_ID] }
    val selectedVehicleId: Flow<String?> =
        context.dataStore.data.map { it[SELECTED_VEHICLE_ID] }

    /** Денормализованное имя техники — восстанавливается мгновенно при холодном старте. */
    val selectedVehicleName: Flow<String?> =
        context.dataStore.data.map { it[SELECTED_VEHICLE_NAME] }

    val customModelsJson: Flow<String?> =
        context.dataStore.data.map { it[CUSTOM_MODELS_JSON] }
    val aiProvider: Flow<String?> =
        context.dataStore.data.map { it[AI_PROVIDER] ?: "Gemini" }
    val aiModelName: Flow<String?> =
        context.dataStore.data.map { it[AI_MODEL_NAME] ?: "gemini-1.5-flash" }
    val aiSystemPrompt: Flow<String> =
        context.dataStore.data.map { it[AI_SYSTEM_PROMPT] ?: "Ты — экспертный ассистент BRP. Отвечай кратко и профессионально." }
    val aiTemperature: Flow<Float> =
        context.dataStore.data.map { it[AI_TEMPERATURE] ?: 0.7f }
    val chatForceOnline: Flow<String?> =
        context.dataStore.data.map { it[CHAT_FORCE_ONLINE] }
    val purchaseDate: Flow<Long?> =
        context.dataStore.data.map { it[PURCHASE_DATE] }
    val appTheme: Flow<String> =
        context.dataStore.data.map { it[APP_THEME] ?: "System" }

    /** true — онбординг уже показывался, false — первый запуск. */
    val onboardingCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[ONBOARDING_COMPLETED] == "true" }

    // ── API-ключи ──────────────────────────────────────────────────────────────────
    suspend fun setGeminiApiKey(key: String?) = withContext(Dispatchers.IO) {
        writeEncrypted(KEY_GEMINI, key)
        _geminiApiKey.value = key
    }

    suspend fun setGroqApiKey(key: String?) = withContext(Dispatchers.IO) {
        writeEncrypted(KEY_GROQ, key)
        _groqApiKey.value = key
    }

    private fun writeEncrypted(key: String, value: String?) {
        val prefs = encryptedPrefs ?: return
        prefs.edit().apply {
            if (value != null) putString(key, value) else remove(key)
            apply()
        }
    }

    // ── DataStore setters ────────────────────────────────────────────────────────────
    suspend fun setAiProvider(provider: String) {
        context.dataStore.edit { it[AI_PROVIDER] = provider }
    }
    suspend fun setAiModelName(model: String) {
        context.dataStore.edit { it[AI_MODEL_NAME] = model }
    }
    suspend fun setAiSystemPrompt(prompt: String) {
        context.dataStore.edit { it[AI_SYSTEM_PROMPT] = prompt }
    }
    suspend fun setAiTemperature(temp: Float) {
        context.dataStore.edit { it[AI_TEMPERATURE] = temp }
    }
    suspend fun setCustomModelsJson(json: String?) {
        context.dataStore.edit {
            if (json != null) it[CUSTOM_MODELS_JSON] = json else it.remove(CUSTOM_MODELS_JSON)
        }
    }
    suspend fun setActiveModelId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[ACTIVE_MODEL_ID] = id else it.remove(ACTIVE_MODEL_ID)
        }
    }
    suspend fun setSelectedVehicleId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[SELECTED_VEHICLE_ID] = id else it.remove(SELECTED_VEHICLE_ID)
        }
    }
    suspend fun setSelectedVehicleName(name: String?) {
        context.dataStore.edit {
            if (name != null) it[SELECTED_VEHICLE_NAME] = name else it.remove(SELECTED_VEHICLE_NAME)
        }
    }
    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = "true" }
    }
    suspend fun setPurchaseDate(date: Long?) {
        context.dataStore.edit {
            if (date != null) it[PURCHASE_DATE] = date else it.remove(PURCHASE_DATE)
        }
    }
    suspend fun setAppTheme(theme: String) {
        context.dataStore.edit { it[APP_THEME] = theme }
    }
    suspend fun setChatForceOnline(provider: String?) {
        context.dataStore.edit {
            if (provider == null) it.remove(CHAT_FORCE_ONLINE)
            else it[CHAT_FORCE_ONLINE] = provider
        }
    }
}
