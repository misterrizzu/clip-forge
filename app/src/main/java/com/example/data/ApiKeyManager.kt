package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig

class ApiKeyManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        initEncryptedPrefs()
    }

    private fun initEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("ApiKeyManager", "EncryptedSharedPreferences failed, falling back", e)
            context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    fun getApiKey(): String {
        val storedKey = prefs.getString(KEY_GEMINI_API, "") ?: ""
        if (storedKey.isNotBlank()) return storedKey.trim()

        // Fall back to BuildConfig key if present and not placeholder
        val buildConfigKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
        if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
            return buildConfigKey.trim()
        }
        return ""
    }

    fun saveApiKey(key: String): Boolean {
        val trimmed = key.trim()
        if (!validateApiKeyFormat(trimmed)) return false
        prefs.edit().putString(KEY_GEMINI_API, trimmed).apply()
        return true
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API).apply()
    }

    fun validateApiKeyFormat(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return false
        // Gemini / Vertex API keys are usually 39 chars starting with AIza... or similar key string
        return trimmed.length >= 10
    }

    fun getSubtitleSettings(): SubtitleSettings {
        val fontSize = try { SubtitleFontSize.valueOf(prefs.getString(KEY_SUB_FONT_SIZE, SubtitleFontSize.MEDIUM.name)!!) } catch (e: Exception) { SubtitleFontSize.MEDIUM }
        val textColor = try { SubtitleTextColor.valueOf(prefs.getString(KEY_SUB_TEXT_COLOR, SubtitleTextColor.YELLOW.name)!!) } catch (e: Exception) { SubtitleTextColor.YELLOW }
        val bgOpacity = try { SubtitleBgOpacity.valueOf(prefs.getString(KEY_SUB_BG_OPACITY, SubtitleBgOpacity.DARK.name)!!) } catch (e: Exception) { SubtitleBgOpacity.DARK }
        val position = try { SubtitlePosition.valueOf(prefs.getString(KEY_SUB_POSITION, SubtitlePosition.BOTTOM.name)!!) } catch (e: Exception) { SubtitlePosition.BOTTOM }
        return SubtitleSettings(fontSize, textColor, bgOpacity, position)
    }

    fun saveSubtitleSettings(settings: SubtitleSettings) {
        prefs.edit()
            .putString(KEY_SUB_FONT_SIZE, settings.fontSize.name)
            .putString(KEY_SUB_TEXT_COLOR, settings.textColor.name)
            .putString(KEY_SUB_BG_OPACITY, settings.bgOpacity.name)
            .putString(KEY_SUB_POSITION, settings.position.name)
            .apply()
    }

    fun getShowHookBanner(): Boolean = prefs.getBoolean(KEY_SHOW_HOOK_BANNER, true)
    fun saveShowHookBanner(show: Boolean) = prefs.edit().putBoolean(KEY_SHOW_HOOK_BANNER, show).apply()

    fun getShowSubtitlesBanner(): Boolean = prefs.getBoolean(KEY_SHOW_SUBTITLES_BANNER, true)
    fun saveShowSubtitlesBanner(show: Boolean) = prefs.edit().putBoolean(KEY_SHOW_SUBTITLES_BANNER, show).apply()

    fun getSelectedModel(): String = prefs.getString(KEY_SELECTED_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"
    fun saveSelectedModel(model: String) = prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()

    companion object {
        private const val PREFS_NAME = "clipforge_secure_prefs"
        private const val PREFS_NAME_FALLBACK = "clipforge_fallback_prefs"
        private const val KEY_GEMINI_API = "gemini_api_key_encrypted"
        private const val KEY_SUB_FONT_SIZE = "sub_font_size"
        private const val KEY_SUB_TEXT_COLOR = "sub_text_color"
        private const val KEY_SUB_BG_OPACITY = "sub_bg_opacity"
        private const val KEY_SUB_POSITION = "sub_position"
        private const val KEY_SHOW_HOOK_BANNER = "show_hook_banner"
        private const val KEY_SHOW_SUBTITLES_BANNER = "show_subtitles_banner"
        private const val KEY_SELECTED_MODEL = "selected_gemini_model"
    }
}
