package com.personal.sinhalakeyboard

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "sinhala_keyboard_prefs"
    private const val KEY_API = "openrouter_api_key"
    private const val KEY_THEME = "keyboard_theme"
    private const val KEY_AUTO_CORRECT = "auto_correct_on_space"
    private const val KEY_CLOUD_SUGGESTIONS = "cloud_suggestions"
    private const val KEY_CONTINUOUS_VOICE = "continuous_voice"
    private const val KEY_ENGLISH_TONE = "english_tone"
    private const val KEY_RECENT_EMOJIS = "recent_emojis"
    private const val KEY_HAPTIC = "haptic_feedback"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String =
        prefs(context).getString(KEY_API, "").orEmpty()

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API, key.trim()).apply()
    }

    fun getTheme(context: Context): KeyboardTheme =
        KeyboardTheme.fromId(prefs(context).getString(KEY_THEME, KeyboardTheme.WHITE.id))

    fun setTheme(context: Context, theme: KeyboardTheme) {
        prefs(context).edit().putString(KEY_THEME, theme.id).apply()
    }

    fun isAutoCorrectOnSpace(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CORRECT, false)

    fun setAutoCorrectOnSpace(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CORRECT, enabled).apply()
    }

    fun isCloudSuggestionsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLOUD_SUGGESTIONS, false)

    fun setCloudSuggestionsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLOUD_SUGGESTIONS, enabled).apply()
    }

    fun isContinuousVoice(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONTINUOUS_VOICE, false)

    fun setContinuousVoice(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONTINUOUS_VOICE, enabled).apply()
    }

    fun getEnglishTone(context: Context): EnglishTone =
        EnglishTone.fromId(prefs(context).getString(KEY_ENGLISH_TONE, EnglishTone.PROFESSIONAL.id))

    fun setEnglishTone(context: Context, tone: EnglishTone) {
        prefs(context).edit().putString(KEY_ENGLISH_TONE, tone.id).apply()
    }

    fun getRecentEmojis(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_RECENT_EMOJIS, "").orEmpty()
        if (raw.isEmpty()) return emptyList()
        return raw.split("\u0001").filter { it.isNotEmpty() }
    }

    fun addRecentEmoji(context: Context, emoji: String) {
        val current = getRecentEmojis(context).toMutableList()
        current.remove(emoji)
        current.add(0, emoji)
        prefs(context).edit()
            .putString(KEY_RECENT_EMOJIS, current.take(32).joinToString("\u0001"))
            .apply()
    }

    fun isHapticEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAPTIC, true)

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAPTIC, enabled).apply()
    }
}
