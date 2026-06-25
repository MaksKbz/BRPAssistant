package com.brp.assistant.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
    private val SELECTED_VEHICLE_ID = stringPreferencesKey("selected_vehicle_id")
    private val CUSTOM_MODELS_JSON = stringPreferencesKey("custom_models_json")
    private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    private val GROK_API_KEY = stringPreferencesKey("grok_api_key")
    private val AI_PROVIDER = stringPreferencesKey("ai_provider")
    private val AI_MODEL_NAME = stringPreferencesKey("ai_model_name")
    private val AI_SYSTEM_PROMPT = stringPreferencesKey("ai_system_prompt")
    private val AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
    private val PURCHASE_DATE = longPreferencesKey("purchase_date")
    private val APP_THEME = stringPreferencesKey("app_theme")

    val activeModelId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_MODEL_ID]
    }

    val selectedVehicleId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_VEHICLE_ID]
    }

    val customModelsJson: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_MODELS_JSON]
    }

    val geminiApiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY]
    }

    // NOTE: No default key here. User must enter their own Groq API key in Settings.
    val grokApiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GROK_API_KEY]
    }

    val aiProvider: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AI_PROVIDER] ?: "Gemini"
    }

    val aiModelName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AI_MODEL_NAME] ?: "gemini-1.5-flash"
    }

    val aiSystemPrompt: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AI_SYSTEM_PROMPT] ?: "Ты — экспертный ассистент BRP. Отвечай кратко и профессионально."
    }

    val aiTemperature: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[AI_TEMPERATURE] ?: 0.7f
    }

    val purchaseDate: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[PURCHASE_DATE]
    }

    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: "System"
    }

    suspend fun setGeminiApiKey(key: String?) {
        context.dataStore.edit { preferences ->
            if (key != null) {
                preferences[GEMINI_API_KEY] = key
            } else {
                preferences.remove(GEMINI_API_KEY)
            }
        }
    }

    suspend fun setGrokApiKey(key: String?) {
        context.dataStore.edit { preferences ->
            if (key != null) {
                preferences[GROK_API_KEY] = key
            } else {
                preferences.remove(GROK_API_KEY)
            }
        }
    }

    suspend fun setAiProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_PROVIDER] = provider
        }
    }

    suspend fun setAiModelName(model: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_MODEL_NAME] = model
        }
    }

    suspend fun setAiSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_SYSTEM_PROMPT] = prompt
        }
    }

    suspend fun setAiTemperature(temp: Float) {
        context.dataStore.edit { preferences ->
            preferences[AI_TEMPERATURE] = temp
        }
    }

    suspend fun setCustomModelsJson(json: String?) {
        context.dataStore.edit { preferences ->
            if (json != null) {
                preferences[CUSTOM_MODELS_JSON] = json
            } else {
                preferences.remove(CUSTOM_MODELS_JSON)
            }
        }
    }

    suspend fun setActiveModelId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id != null) {
                preferences[ACTIVE_MODEL_ID] = id
            } else {
                preferences.remove(ACTIVE_MODEL_ID)
            }
        }
    }

    suspend fun setSelectedVehicleId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id != null) {
                preferences[SELECTED_VEHICLE_ID] = id
            } else {
                preferences.remove(SELECTED_VEHICLE_ID)
            }
        }
    }

    suspend fun setPurchaseDate(date: Long?) {
        context.dataStore.edit { preferences ->
            if (date != null) {
                preferences[PURCHASE_DATE] = date
            } else {
                preferences.remove(PURCHASE_DATE)
            }
        }
    }

    suspend fun setAppTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = theme
        }
    }
}
