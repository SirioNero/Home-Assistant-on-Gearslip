package it.plancia.ha.data

import android.content.Context

/** Indirizzi e token di Home Assistant, salvati nelle preferenze private dell'app. */
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

    companion object {
        private const val KEY_HOME = "url_home"
        private const val KEY_REMOTE = "url_remote"
        private const val KEY_TOKEN = "token"

        fun normalizeUrl(raw: String): String {
            var url = raw.trim().trimEnd('/')
            if (url.isEmpty()) return ""
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            return url
        }
    }
}
