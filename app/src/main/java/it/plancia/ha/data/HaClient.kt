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

/**
 * [message] è in italiano, per l'auto; [reason] e [detail] servono al telefono per dire la stessa cosa
 * nella lingua scelta.
 */
class HaException(
    message: String,
    val code: String? = null,
    val reason: Reason = Reason.OTHER,
    val detail: String? = null,
) : Exception(message) {
    enum class Reason { BAD_ADDRESS, NO_RESPONSE, INVALID_TOKEN, ACCESS_DENIED, HTTP_ERROR, NETWORK, CLOSED, OTHER }
}

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
            throw HaException("Indirizzo non valido: $baseUrl", reason = HaException.Reason.BAD_ADDRESS, detail = baseUrl)
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
            throw HaException("Nessuna risposta da $baseUrl", reason = HaException.Reason.NO_RESPONSE, detail = baseUrl)
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

    fun close() {
        socket?.close(1000, null)
        shutdown(HaException("Connessione chiusa", reason = HaException.Reason.CLOSED))
    }

    private suspend fun send(id: Int, type: String, fill: JSONObject.() -> Unit): Any? {
        val ws = socket
        if (ws == null || closed.isCompleted) throw HaException("Non connesso a Home Assistant", reason = HaException.Reason.CLOSED)
        val message = JSONObject().put("id", id).put("type", type).apply(fill)
        val deferred = CompletableDeferred<Box>()
        pending[id] = deferred
        if (!ws.send(message.toString())) {
            pending.remove(id)
            throw HaException("Invio a Home Assistant non riuscito", reason = HaException.Reason.CLOSED)
        }
        val box = withTimeoutOrNull(20_000) { deferred.await() }
        if (box == null) {
            pending.remove(id)
            throw HaException("Home Assistant non risponde", reason = HaException.Reason.NO_RESPONSE)
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
            shutdown(HaException("Connessione chiusa", reason = HaException.Reason.CLOSED))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val error = when {
                response?.code == 401 || response?.code == 403 ->
                    HaException("Accesso negato", reason = HaException.Reason.ACCESS_DENIED)
                response != null ->
                    HaException("Errore HTTP ${response.code}", reason = HaException.Reason.HTTP_ERROR, detail = "${response.code}")
                else ->
                    HaException(t.message ?: "Errore di rete", reason = HaException.Reason.NETWORK, detail = t.message)
            }
            shutdown(error)
        }
    }

    private fun handle(ws: WebSocket, msg: JSONObject) {
        when (msg.optString("type")) {
            "auth_required" -> ws.send(JSONObject().put("type", "auth").put("access_token", token).toString())
            "auth_ok" -> auth.complete(msg.optString("ha_version"))
            "auth_invalid" -> auth.completeExceptionally(HaException("Token non valido", reason = HaException.Reason.INVALID_TOKEN))
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

    private fun shutdown(error: HaException) {
        auth.completeExceptionally(error)
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        closed.complete(error.message.orEmpty())
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
