package it.plancia.ha.data

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class HaException(message: String, val code: String? = null) : Exception(message)

/**
 * Una singola connessione WebSocket a Home Assistant (/api/websocket).
 * Gestisce autenticazione, comandi con id e iscrizioni agli eventi.
 */
class HaClient(private val http: OkHttpClient) {

    private class Box(val value: Any?)

    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Box>>()
    private val handlers = ConcurrentHashMap<Int, (JSONObject) -> Unit>()
    private val auth = CompletableDeferred<String>()

    /** Si completa (con il motivo) quando la connessione si chiude. */
    val closed = CompletableDeferred<String>()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var token: String = ""

    /** Si collega e si autentica. Restituisce la versione di Home Assistant. */
    suspend fun connect(baseUrl: String, token: String, timeoutMs: Long = 10_000): String {
        this.token = token
        val request = try {
            Request.Builder().url(websocketUrl(baseUrl)).build()
        } catch (e: IllegalArgumentException) {
            throw HaException("Indirizzo non valido: $baseUrl")
        }
        socket = http.newWebSocket(request, listener)
        val version = try {
            withTimeoutOrNull(timeoutMs) { auth.await() }
        } catch (e: HaException) {
            close()
            throw e
        }
        if (version == null) {
            close()
            throw HaException("Nessuna risposta da $baseUrl")
        }
        return version.ifBlank { "?" }
    }

    suspend fun command(type: String, fill: JSONObject.() -> Unit = {}): Any? =
        send(ids.getAndIncrement(), type, fill)

    suspend fun subscribeEvents(eventType: String, onEvent: (JSONObject) -> Unit) {
        val id = ids.getAndIncrement()
        handlers[id] = onEvent
        try {
            send(id, "subscribe_events") { put("event_type", eventType) }
        } catch (e: Exception) {
            handlers.remove(id)
            throw e
        }
    }

    suspend fun callService(domain: String, service: String, entityId: String, data: JSONObject? = null) {
        command("call_service") {
            put("domain", domain)
            put("service", service)
            put("target", JSONObject().put("entity_id", entityId))
            if (data != null) put("service_data", data)
        }
    }

    fun close() {
        socket?.close(1000, null)
        shutdown("Connessione chiusa")
    }

    private suspend fun send(id: Int, type: String, fill: JSONObject.() -> Unit): Any? {
        val ws = socket
        if (ws == null || closed.isCompleted) throw HaException("Non connesso a Home Assistant")
        val message = JSONObject().put("id", id).put("type", type).apply(fill)
        val deferred = CompletableDeferred<Box>()
        pending[id] = deferred
        if (!ws.send(message.toString())) {
            pending.remove(id)
            throw HaException("Invio a Home Assistant non riuscito")
        }
        val box = withTimeoutOrNull(20_000) { deferred.await() }
        if (box == null) {
            pending.remove(id)
            throw HaException("Home Assistant non risponde")
        }
        return box.value
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val trimmed = text.trimStart()
                if (trimmed.startsWith("[")) {
                    val array = JSONArray(trimmed)
                    for (i in 0 until array.length()) array.optJSONObject(i)?.let { handle(webSocket, it) }
                } else {
                    handle(webSocket, JSONObject(trimmed))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Messaggio non valido da Home Assistant", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            shutdown("Connessione chiusa")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val reason = when {
                response?.code == 401 || response?.code == 403 -> "Accesso negato"
                response != null -> "Errore HTTP ${response.code}"
                else -> t.message ?: "Errore di rete"
            }
            shutdown(reason)
        }
    }

    private fun handle(ws: WebSocket, msg: JSONObject) {
        when (msg.optString("type")) {
            "auth_required" -> ws.send(JSONObject().put("type", "auth").put("access_token", token).toString())
            "auth_ok" -> auth.complete(msg.optString("ha_version"))
            "auth_invalid" -> auth.completeExceptionally(HaException("Token non valido"))
            "result" -> {
                val deferred = pending.remove(msg.optInt("id")) ?: return
                if (msg.optBoolean("success")) {
                    val result = msg.opt("result")
                    deferred.complete(Box(if (result == JSONObject.NULL) null else result))
                } else {
                    val error = msg.optJSONObject("error")
                    deferred.completeExceptionally(
                        HaException(error?.str("message") ?: "Comando rifiutato", error?.str("code"))
                    )
                }
            }
            "event" -> {
                val event = msg.optJSONObject("event") ?: return
                val handler = handlers[msg.optInt("id")] ?: return
                try {
                    handler(event)
                } catch (e: Exception) {
                    Log.w(TAG, "Errore nella gestione di un evento", e)
                }
            }
        }
    }

    private fun shutdown(reason: String) {
        auth.completeExceptionally(HaException(reason))
        val error = HaException(reason)
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        closed.complete(reason)
    }

    companion object {
        private const val TAG = "Plancia"

        fun websocketUrl(base: String): String {
            val trimmed = base.trim().trimEnd('/')
            val ws = when {
                trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
                trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
                else -> "ws://$trimmed"
            }
            return "$ws/api/websocket"
        }
    }
}
