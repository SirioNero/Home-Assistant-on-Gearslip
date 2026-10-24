package it.plancia.ha.data

import android.content.Context

/** Indirizzi, token e preferenze per l'auto, salvati nelle preferenze private dell'app. */
class Settings(context: Context) {

    data class Config(val homeUrl: String, val remoteUrl: String, val token: String) {
        /** Prima l'indirizzo di casa, poi quello remoto. */
        val urls: List<String> get() = listOf(homeUrl, remoteUrl).filter { it.isNotBlank() }.distinct()
        val isComplete: Boolean get() = urls.isNotEmpty() && token.isNotBlank()
    }

    private val prefs = context.getSharedPreferences("plancia", Context.MODE_PRIVATE)

    fun load() = Config(
        homeUrl = prefs.getString(KEY_HOME, "").orEmpty(),
        remoteUrl = prefs.getString(KEY_REMOTE, "").orEmpty(),
        token = prefs.getString(KEY_TOKEN, "").orEmpty(),
    )

    fun save(config: Config) {
        prefs.edit()
            .putString(KEY_HOME, config.homeUrl)
            .putString(KEY_REMOTE, config.remoteUrl)
            .putString(KEY_TOKEN, config.token)
            .apply()
    }

    /** La plancia da aprire in auto (url_path); null = quella che apre Home Assistant da solo. */
    var defaultDashboard: String?
        get() = prefs.getString(KEY_DEFAULT_DASHBOARD, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_DEFAULT_DASHBOARD) else editor.putString(KEY_DEFAULT_DASHBOARD, value)
            editor.apply()
        }

    /** Quanto ingrandire la plancia sullo schermo dell'auto, in percentuale. */
    var zoom: Int
        get() = prefs.getInt(KEY_ZOOM, 100).coerceIn(ZOOM_LEVELS.first(), ZOOM_LEVELS.last())
        set(value) {
            prefs.edit().putInt(KEY_ZOOM, value).apply()
        }

    companion object {
        private const val KEY_HOME = "url_home"
        private const val KEY_REMOTE = "url_remote"
        private const val KEY_TOKEN = "token"
        private const val KEY_DEFAULT_DASHBOARD = "default_dashboard"
        private const val KEY_ZOOM = "zoom"

        val ZOOM_LEVELS = listOf(60, 70, 80, 90, 100, 115, 130, 150)

        fun normalizeUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (url.isEmpty()) return ""
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            return url
        }
    }
}
