package com.personal.sinhalakeyboard

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "sinhala_keyboard_prefs"
    private const val KEY_API = "openrouter_api_key"
    private const val KEY_THEME = "keyboard_theme"

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
}
