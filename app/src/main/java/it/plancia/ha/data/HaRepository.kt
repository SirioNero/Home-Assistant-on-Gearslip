package it.plancia.ha.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Tiene viva una connessione a Home Assistant mentre l'auto è collegata: dice quale indirizzo
 * risponde (casa o remoto) e quali plance ci sono. La plancia vera la disegna poi il frontend
 * di Home Assistant stesso, aperto su quell'indirizzo.
 */
class HaRepository(private val settings: Settings) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _dashboards = MutableStateFlow<List<Dashboard>>(emptyList())
    val dashboards: StateFlow<List<Dashboard>> = _dashboards.asStateFlow()

    /** La plancia che Home Assistant apre da solo all'avvio (url_path). */
    private val _haDefault = MutableStateFlow<String?>(null)
    val haDefault: StateFlow<String?> = _haDefault.asStateFlow()

    private val _connection = MutableStateFlow<Connection>(Connection.Connecting(""))
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    @Volatile private var client: HaClient? = null
    private var loop: Job? = null
    private var users = 0

    fun newClient() = HaClient(http)

    /** Chiamato quando parte una sessione in auto. */
    @Synchronized
    fun acquire() {
        users++
        if (loop?.isActive != true) loop = scope.launch { connectLoop() }
    }

    /** Chiamato quando la sessione in auto finisce: chiude la connessione. */
    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) {
            loop?.cancel()
            loop = null
            client?.close()
            client = null
        }
    }

    /** Riparte da capo, per esempio dopo aver cambiato indirizzo o token. */
    @Synchronized
    fun reconnect() {
        if (users == 0) return
        loop?.cancel()
        client?.close()
        loop = scope.launch { connectLoop() }
    }

    /** Rilegge l'elenco delle plance, per esempio quando si apre il menu. */
    fun refreshDashboards() {
        val c = client ?: return
        scope.launch { loadDashboards(c) }
    }

    private suspend fun connectLoop() {
        var failures = 0
        while (true) {
            val config = settings.load()
            if (!config.isComplete) {
                _connection.value = Connection.NotConfigured
                delay(4_000)
                continue
            }

            var session: HaClient? = null
            var version = ""
            var usedUrl = ""
            var lastError = "Home Assistant non raggiungibile"
            for (url in config.urls) {
                _connection.value = Connection.Connecting(url)
                val candidate = HaClient(http)
                try {
                    version = candidate.connect(url, config.token)
                    session = candidate
                    usedUrl = url
                    break
                } catch (e: CancellationException) {
                    candidate.close()
                    throw e
                } catch (e: Exception) {
                    candidate.close()
                    lastError = e.message ?: lastError
                }
            }

            if (session == null) {
                _connection.value = Connection.Failed(lastError)
                failures++
                delay((2_000L * failures).coerceAtMost(30_000L))
                continue
            }

            failures = 0
            val active: HaClient = session
            client = active
            try {
                loadDashboards(active)
                watchPanels(active)
                _connection.value = Connection.Connected(usedUrl, version)
                val reason = active.closed.await()
                _connection.value = Connection.Failed("Connessione persa ($reason), riprovo")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connection.value = Connection.Failed(e.message ?: "Errore di connessione")
            } finally {
                if (client === active) client = null
                active.close()
            }
            delay(1_500)
        }
    }

    /** Quando in Home Assistant si aggiunge o si toglie una plancia, l'elenco si aggiorna da solo. */
    private suspend fun watchPanels(c: HaClient) {
        try {
            c.subscribeEvents("panels_updated") { scope.launch { loadDashboards(c) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Aggiornamenti delle plance non disponibili: ${e.message}")
        }
    }

    /** Le plance come le mostra la barra laterale di Home Assistant, con l'ordine e le voci nascoste dell'utente. */
    private suspend fun loadDashboards(c: HaClient) {
        try {
            val panels = c.command("get_panels") as? JSONObject ?: return
            val sidebar = storedData(c, "frontend/get_user_data", "sidebar")
            val core = storedData(c, "frontend/get_user_data", "core")
            val system = storedData(c, "frontend/get_system_data", "core")
            val order = strings(sidebar?.optJSONArray("panelOrder"))
            val hidden = strings(sidebar?.optJSONArray("hiddenPanels")).toSet()
            val default = Panels.defaultPanel(panels, core?.str("default_panel"), system?.str("default_panel"))
            _haDefault.value = default
            _dashboards.value = Panels.dashboards(panels, order, hidden, default)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Impossibile leggere le plance: ${e.message}")
        }
    }

    /** Dati salvati dal frontend (ordine della barra laterale, plancia predefinita); assenti nelle versioni vecchie. */
    private suspend fun storedData(c: HaClient, type: String, key: String): JSONObject? = try {
        (c.command(type) { put("key", key) } as? JSONObject)?.optJSONObject("value")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$type $key non disponibile: ${e.message}")
        null
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i -> array.optString(i).takeIf { it.isNotEmpty() } }
    }

    companion object {
        private const val TAG = "Plancia"
    }
}
