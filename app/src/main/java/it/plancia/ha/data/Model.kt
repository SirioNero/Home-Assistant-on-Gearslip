package it.plancia.ha.data

import org.json.JSONObject

/** Lo stato di un'entità così come lo manda Home Assistant. */
data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JSONObject,
) {
    val domain: String get() = entityId.substringBefore('.')
    val name: String get() = attributes.str("friendly_name") ?: entityId.substringAfter('.').replace('_', ' ')
    val deviceClass: String? get() = attributes.str("device_class")
    val unavailable: Boolean get() = state == "unavailable" || state == "unknown"

    companion object {
        fun from(json: JSONObject) = EntityState(
            entityId = json.optString("entity_id"),
            state = json.optString("state"),
            attributes = json.optJSONObject("attributes") ?: JSONObject(),
        )
    }
}

/** Una stanza (area) o un'etichetta, con le entità che contiene. */
data class Group(val id: String, val name: String, val entityIds: List<String>)

data class Registry(val areas: List<Group>, val labels: List<Group>) {
    companion object {
        val EMPTY = Registry(emptyList(), emptyList())
    }
}

/** Una plancia di Home Assistant. urlPath nullo = la plancia predefinita. */
data class Dashboard(val urlPath: String?, val title: String)

/** Una vista (scheda) di una plancia, ridotta all'elenco delle entità che mostra. */
data class DashView(val title: String, val entityIds: List<String>)

sealed interface Connection {
    data object NotConfigured : Connection
    data class Connecting(val url: String) : Connection
    data class Connected(val url: String, val version: String) : Connection
    data class Failed(val message: String) : Connection
}

/** Come optString, ma restituisce null per valori assenti, null o vuoti (org.json restituirebbe "null"). */
fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
