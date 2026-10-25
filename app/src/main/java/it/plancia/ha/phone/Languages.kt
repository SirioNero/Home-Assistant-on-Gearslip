package it.plancia.ha.phone

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Le lingue della schermata sul telefono. Senza una scelta salvata si parte dall'inglese. */
object Languages {

    /** Tag della lingua e nome nella lingua stessa, nell'ordine del menu. */
    val ALL = listOf(
        "en" to "English",
        "it" to "Italiano",
        "es" to "Español",
        "de" to "Deutsch",
        "fr" to "Français",
        "pt" to "Português",
        "el" to "Ελληνικά",
        "ru" to "Русский",
        "zh-CN" to "中文",
    )

    private const val DEFAULT = "en"
    private const val PREFS = "plancia"
    private const val KEY = "language"

    fun current(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return saved?.takeIf { tag -> ALL.any { it.first == tag } } ?: DEFAULT
    }

    fun save(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    /** Il contesto con le risorse nella lingua scelta. */
    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(current(base)))
        return base.createConfigurationContext(config)
    }
}
