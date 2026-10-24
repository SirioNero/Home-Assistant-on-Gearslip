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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Tiene viva la connessione a Home Assistant mentre l'auto è collegata,
 * e mette a disposizione stati, stanze, etichette e plance come StateFlow.
 */
class HaRepository(private val settings: Settings) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _states = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val states: StateFlow<Map<String, EntityState>> = _states.asStateFlow()

    private val _registry = MutableStateFlow(Registry.EMPTY)
    val registry: StateFlow<Registry> = _registry.asStateFlow()

    private val _dashboards = MutableStateFlow<List<Dashboard>>(emptyList())
    val dashboards: StateFlow<List<Dashboard>> = _dashboards.asStateFlow()

    private val _connection = MutableStateFlow<Connection>(Connection.Connecting(""))
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    @Volatile private var client: HaClient? = null
    private var loop: Job? = null
    private var users = 0
    private val viewCache = ConcurrentHashMap<String, List<DashView>>()

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

    fun call(domain: String, service: String, entityId: String, onError: (String) -> Unit) {
        scope.launch {
            val c = client
            if (c == null) {
                onError("Home Assistant non è connesso")
                return@launch
            }
            try {
                c.callService(domain, service, entityId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "Comando non riuscito")
            }
        }
    }

    /** Legge le viste di una plancia. Le plance generate automaticamente diventano una vista per stanza. */
    suspend fun loadViews(dashboard: Dashboard): List<DashView> = withContext(Dispatchers.IO) {
        val key = dashboard.urlPath ?: ""
        viewCache[key]?.let { return@withContext it }
        val c = client ?: throw HaException("Home Assistant non è connesso")
        val config = try {
            c.command("lovelace/config") { dashboard.urlPath?.let { put("url_path", it) } } as? JSONObject
        } catch (e: HaException) {
            if (e.code == "config_not_found") null else throw e
        }
        val views = if (config == null || config.has("strategy")) {
            areaViews()
        } else {
            Lovelace.views(config).ifEmpty { areaViews() }
        }
        viewCache[key] = views
        views
    }

    private fun areaViews(): List<DashView> =
        _registry.value.areas.filter { it.entityIds.isNotEmpty() }.map { DashView(it.name, it.entityIds) }

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
                bootstrap(active)
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

    private suspend fun bootstrap(c: HaClient) {
        val array = c.command("get_states") as? JSONArray ?: JSONArray()
        val map = HashMap<String, EntityState>(array.length() * 2)
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            val entity = EntityState.from(json)
            map[entity.entityId] = entity
        }
        _states.value = map
        c.subscribeEvents("state_changed") { event -> onStateChanged(event) }
        _registry.value = loadRegistry(c)
        _dashboards.value = loadDashboards(c)
        viewCache.clear()
    }

    private fun onStateChanged(event: JSONObject) {
        val data = event.optJSONObject("data") ?: return
        val id = data.optString("entity_id")
        if (id.isEmpty()) return
        val newState = data.optJSONObject("new_state")
        _states.update { current ->
            if (newState == null) current - id else current + (id to EntityState.from(newState))
        }
    }

    private suspend fun optional(c: HaClient, type: String): JSONArray? = try {
        c.command(type) as? JSONArray
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$type non disponibile: ${e.message}")
        null
    }

    private suspend fun loadRegistry(c: HaClient): Registry {
        val areas = optional(c, "config/area_registry/list") ?: JSONArray()
        val devices = optional(c, "config/device_registry/list") ?: JSONArray()
        val entities = optional(c, "config/entity_registry/list") ?: JSONArray()
        val labels = optional(c, "config/label_registry/list") ?: JSONArray()

        val deviceArea = HashMap<String, String>()
        for (i in 0 until devices.length()) {
            val device = devices.optJSONObject(i) ?: continue
            val id = device.str("id") ?: continue
            val area = device.str("area_id") ?: continue
            deviceArea[id] = area
        }

        val byArea = LinkedHashMap<String, MutableList<String>>()
        val byLabel = LinkedHashMap<String, MutableList<String>>()
        for (i in 0 until entities.length()) {
            val entry = entities.optJSONObject(i) ?: continue
            val id = entry.str("entity_id") ?: continue
            if (entry.str("disabled_by") != null || entry.str("hidden_by") != null) continue
            val technical = entry.str("entity_category") != null
            val area = entry.str("area_id") ?: entry.str("device_id")?.let { deviceArea[it] }
            if (area != null && !technical) byArea.getOrPut(area) { mutableListOf() }.add(id)
            val entityLabels = entry.optJSONArray("labels") ?: continue
            for (j in 0 until entityLabels.length()) {
                val label = entityLabels.optString(j)
                if (label.isNotEmpty()) byLabel.getOrPut(label) { mutableListOf() }.add(id)
            }
        }

        val areaGroups = ArrayList<Group>()
        for (i in 0 until areas.length()) {
            val area = areas.optJSONObject(i) ?: continue
            val id = area.str("area_id") ?: continue
            areaGroups += Group(id, area.str("name") ?: id, byArea[id].orEmpty())
        }
        val labelGroups = ArrayList<Group>()
        for (i in 0 until labels.length()) {
            val label = labels.optJSONObject(i) ?: continue
            val id = label.str("label_id") ?: continue
            labelGroups += Group(id, label.str("name") ?: id, byLabel[id].orEmpty())
        }
        return Registry(
            areas = areaGroups.sortedBy { it.name.lowercase() },
            labels = labelGroups.sortedBy { it.name.lowercase() },
        )
    }

    private suspend fun loadDashboards(c: HaClient): List<Dashboard> {
        val list = mutableListOf(Dashboard(null, "Panoramica"))
        val array = optional(c, "lovelace/dashboards/list") ?: return list
        for (i in 0 until array.length()) {
            val dashboard = array.optJSONObject(i) ?: continue
            val path = dashboard.str("url_path") ?: continue
            list += Dashboard(path, dashboard.str("title") ?: path)
        }
        return list
    }

    companion object {
        private const val TAG = "Plancia"
    }
}
