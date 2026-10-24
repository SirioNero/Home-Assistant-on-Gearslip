package it.plancia.ha.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Riduce la configurazione di una plancia alle sue viste e alle entità che ciascuna mostra,
 * nell'ordine in cui compaiono. Funziona con viste classiche, a sezioni, schede annidate
 * (stack, griglie, condizionali) e badge.
 */
object Lovelace {

    private val ENTITY_ID = Regex("^[a-z_]+\\.[a-z0-9_]+$")

    fun views(config: JSONObject): List<DashView> {
        val array = config.optJSONArray("views") ?: return emptyList()
        val out = mutableListOf<DashView>()
        for (i in 0 until array.length()) {
            val view = array.optJSONObject(i) ?: continue
            if (view.has("strategy")) continue
            val ids = LinkedHashSet<String>()
            collect(view, ids)
            if (ids.isEmpty()) continue
            val title = view.str("title")
                ?: view.str("path")?.replace('-', ' ')?.replaceFirstChar { it.uppercase() }
                ?: "Vista ${i + 1}"
            out += DashView(title, ids.toList())
        }
        return out
    }

    private fun collect(node: Any?, out: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key)
                    when {
                        (key == "entity" || key == "entity_id") && value is String -> add(value, out)
                        key == "entities" && value is JSONArray -> {
                            for (j in 0 until value.length()) {
                                when (val item = value.opt(j)) {
                                    is String -> add(item, out)
                                    else -> collect(item, out)
                                }
                            }
                        }
                        else -> collect(value, out)
                    }
                }
            }
            is JSONArray -> for (j in 0 until node.length()) collect(node.opt(j), out)
        }
    }

    private fun add(candidate: String, out: MutableSet<String>) {
        if (ENTITY_ID.matches(candidate)) out += candidate
    }
}
