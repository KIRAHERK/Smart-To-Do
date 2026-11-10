package io.github.kiraherk.smarttodo.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app language is chosen inside the app (Arabic by default), independent of the phone's
 * language, because many phones in the region are set to English. Stored in plain
 * SharedPreferences because it must be read synchronously before the first screen draws.
 */
object LocaleHelper {
    private const val PREFS = "ui_prefs"
    private const val KEY_LANGUAGE = "language"
    val SUPPORTED = listOf("ar", "en")

    fun language(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "ar")?.takeIf { it in SUPPORTED } ?: "ar"

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun locale(context: Context): Locale = Locale.forLanguageTag(language(context))

    fun isArabic(context: Context): Boolean = language(context) == "ar"

    /** A context whose resources use the app language. Use it for every user-visible string. */
    fun wrap(base: Context): Context {
        val locale = locale(base)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
