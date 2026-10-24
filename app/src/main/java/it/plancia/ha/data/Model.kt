package it.plancia.ha.data

import org.json.JSONObject

/** Una plancia di Home Assistant, come compare nella sua barra laterale. urlPath è senza "/" iniziale. */
data class Dashboard(val urlPath: String, val title: String)

sealed interface Connection {
    data object NotConfigured : Connection
    data class Connecting(val url: String) : Connection
    data class Connected(val url: String, val version: String) : Connection
    data class Failed(val message: String) : Connection
}

/** Come optString, ma restituisce null per valori assenti, null o vuoti (org.json restituirebbe "null"). */
fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
