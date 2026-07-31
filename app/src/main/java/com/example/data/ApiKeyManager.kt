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

    companion object {
        private const val PREFS_NAME = "clipforge_secure_prefs"
        private const val PREFS_NAME_FALLBACK = "clipforge_fallback_prefs"
        private const val KEY_GEMINI_API = "gemini_api_key_encrypted"
    }
}
