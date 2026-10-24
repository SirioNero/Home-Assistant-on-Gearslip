package it.plancia.ha.data

import org.json.JSONObject
import java.text.Collator
import java.util.Locale

/**
 * Le plance da mostrare nel menu, nello stesso ordine della barra laterale di Home Assistant:
 * prima quelle che hai riordinato tu, poi la predefinita, le altre plance in ordine alfabetico,
 * Energia, Mappa, Attività e Cronologia, infine il resto. Le regole sono quelle del suo frontend.
 */
object Panels {

    /** Pannelli che non sono plance: impostazioni, profilo e pagine di servizio. */
    private val EXCLUDED = setOf("profile", "config", "notfound", "_my_redirect", "app", "developer-tools")

    private val BUILT_IN_ORDER = mapOf("energy" to 1, "map" to 2, "logbook" to 3, "history" to 4)

    /** I pannelli integrati hanno per titolo una chiave di traduzione del frontend. */
    private val TITLES = mapOf(
        "home" to "Panoramica",
        "states" to "Panoramica",
        "energy" to "Energia",
        "map" to "Mappa",
        "logbook" to "Attività",
        "history" to "Cronologia",
        "calendar" to "Calendario",
        "todo" to "Liste di cose da fare",
        "shopping_list" to "Lista della spesa",
        "media_browser" to "Media",
        "light" to "Luci",
        "security" to "Sicurezza",
        "climate" to "Clima",
        "maintenance" to "Manutenzione",
        "apps" to "App",
        "hassio" to "Supervisore",
    )

    /** La plancia che Home Assistant apre da solo all'avvio. */
    fun defaultPanel(panels: JSONObject, userDefault: String?, systemDefault: String?): String {
        val hasHome = panels.has("home")
        val chosen = userDefault ?: systemDefault ?: if (hasHome) "home" else "lovelace"
        // Dove non c'è più la vecchia Panoramica, "lovelace" rimanda alla nuova.
        val legacyOverview = panels.optJSONObject("lovelace")?.optJSONObject("config") != null
        return if (chosen == "lovelace" && hasHome && !legacyOverview) "home" else chosen
    }

    fun dashboards(panels: JSONObject, order: List<String>, hidden: Set<String>, defaultPanel: String): List<Dashboard> {
        val entries = mutableListOf<Entry>()
        val keys = panels.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val panel = panels.optJSONObject(key) ?: continue
            val urlPath = panel.str("url_path") ?: key
            if (urlPath in EXCLUDED) continue
            val rawTitle = panel.str("title")
            val shown = rawTitle != null &&
                panel.optBoolean("show_in_sidebar", true) &&
                urlPath !in hidden &&
                (panel.optBoolean("default_visible", true) || urlPath in order)
            if (urlPath != defaultPanel && !shown) continue
            entries += Entry(urlPath, title(urlPath, rawTitle), panel.str("component_name") == "lovelace")
        }

        val collator = Collator.getInstance(Locale.ITALIAN)
        val reverse = order.reversed()
        return entries.sortedWith { a, b ->
            val ia = reverse.indexOf(a.urlPath)
            val ib = reverse.indexOf(b.urlPath)
            val sa = BUILT_IN_ORDER[a.urlPath]
            val sb = BUILT_IN_ORDER[b.urlPath]
            when {
                ia != ib -> if (ia < ib) 1 else -1
                a.urlPath == defaultPanel -> -1
                b.urlPath == defaultPanel -> 1
                a.lovelace && b.lovelace -> collator.compare(a.title, b.title)
                a.lovelace -> -1
                b.lovelace -> 1
                sa != null && sb != null -> sa - sb
                sa != null -> -1
                sb != null -> 1
                else -> collator.compare(a.title, b.title)
            }
        }.map { Dashboard(it.urlPath, it.title) }
    }

    fun title(urlPath: String, rawTitle: String?): String = when {
        rawTitle != null -> TITLES[rawTitle] ?: rawTitle
        urlPath == "lovelace" -> "Panoramica"
        else -> urlPath.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private class Entry(val urlPath: String, val title: String, val lovelace: Boolean)
}
