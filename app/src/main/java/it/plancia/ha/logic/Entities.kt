package it.plancia.ha.logic

import it.plancia.ha.car.render.Glyph
import it.plancia.ha.data.EntityState
import it.plancia.ha.data.Group
import it.plancia.ha.data.str
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Tutto ciò che serve per leggere, descrivere e comandare un'entità. */
object Entities {

    val OPENINGS = setOf("door", "window", "garage_door", "opening")
    val MOTION = setOf("motion", "occupancy", "presence")
    val ALARMS = setOf("smoke", "gas", "moisture", "safety", "problem", "carbon_monoxide")

    private val ROOM_SENSORS = setOf("temperature", "humidity", "carbon_dioxide")
    private val TOGGLE_DOMAINS = setOf("light", "switch", "fan", "input_boolean", "humidifier", "siren", "cover")
    private val INFO_DOMAINS = setOf("sensor", "weather", "climate", "water_heater")
    private val ORDER = listOf(
        "light", "cover", "climate", "media_player", "vacuum", "fan", "switch", "input_boolean",
        "humidifier", "lock", "alarm_control_panel", "valve", "binary_sensor", "sensor", "scene", "script",
    )

    // ---------- Selezione e ordinamento ----------

    /** Le entità che vale la pena mostrare in una stanza. */
    fun isInteresting(e: EntityState): Boolean = when (e.domain) {
        "light", "switch", "cover", "climate", "fan", "media_player", "vacuum", "lock",
        "input_boolean", "humidifier", "alarm_control_panel", "valve", "water_heater" -> true
        "binary_sensor" -> e.deviceClass in OPENINGS || e.deviceClass in MOTION || e.deviceClass in ALARMS
        "sensor" -> e.deviceClass in ROOM_SENSORS
        else -> false
    }

    fun sort(list: List<EntityState>): List<EntityState> = list.sortedWith(
        compareBy<EntityState>(
            { ORDER.indexOf(it.domain).let { i -> if (i < 0) ORDER.size else i } },
            { it.name.lowercase(Locale.ITALY) },
        )
    )

    /** Tiene solo le entità interessanti e scarta i gruppi rimasti vuoti. */
    fun cleanGroups(groups: List<Group>, states: Map<String, EntityState>): List<Group> =
        groups.map { g ->
            g.copy(entityIds = sort(g.entityIds.mapNotNull { states[it] }.filter(::isInteresting)).map { it.entityId })
        }.filter { it.entityIds.isNotEmpty() }

    /** "Cucina Faretti" dentro la stanza Cucina diventa "Faretti". */
    fun displayName(e: EntityState, prefix: String?): String {
        val name = e.name
        if (prefix.isNullOrBlank()) return name
        if (name.length > prefix.length + 1 && name.startsWith(prefix, ignoreCase = true)) {
            val rest = name.substring(prefix.length).trimStart(' ', '-', '_', ':')
            if (rest.isNotEmpty()) return rest.replaceFirstChar { it.uppercase() }
        }
        return name
    }

    // ---------- Stato ----------

    fun isActive(e: EntityState): Boolean = when (e.domain) {
        "light", "switch", "fan", "input_boolean", "humidifier", "siren", "automation", "script" -> e.state == "on"
        "cover", "valve" -> e.state == "open" || e.state == "opening" || e.state == "closing"
        "media_player" -> e.state == "playing"
        "vacuum" -> e.state == "cleaning" || e.state == "returning"
        "climate", "water_heater" -> e.state != "off" && !e.unavailable
        "lock" -> e.state == "unlocked" || e.state == "open"
        "binary_sensor" -> e.state == "on"
        "person", "device_tracker" -> e.state == "home"
        "alarm_control_panel" -> e.state.startsWith("armed") || e.state == "triggered"
        else -> false
    }

    fun isInformational(e: EntityState) = e.domain in INFO_DOMAINS

    fun brightness(e: EntityState): Float? {
        if (e.domain != "light" || e.state != "on") return null
        val raw = e.attributes.str("brightness")?.toDoubleOrNull() ?: return null
        return (raw / 255.0).toFloat().coerceIn(0f, 1f)
    }

    fun coverPosition(e: EntityState): Int? = e.attributes.str("current_position")?.toDoubleOrNull()?.roundToInt()

    /** L'anello attorno all'icona: luminosità per le luci, apertura per le tapparelle. */
    fun progress(e: EntityState): Float? = when (e.domain) {
        "light" -> brightness(e)
        "cover" -> coverPosition(e)?.takeIf { e.state != "closed" }?.let { it / 100f }
        else -> null
    }

    fun glyph(e: EntityState): Glyph = when (e.domain) {
        "light" -> Glyph.LIGHT
        "cover", "valve" -> Glyph.COVER
        "switch", "fan", "input_boolean", "humidifier", "siren", "automation" -> Glyph.POWER
        "climate", "water_heater" -> Glyph.THERMO
        "media_player" -> Glyph.MEDIA
        "vacuum" -> Glyph.VACUUM
        "lock", "alarm_control_panel" -> Glyph.LOCK
        "binary_sensor" -> when (e.deviceClass) {
            in MOTION -> Glyph.MOTION
            in OPENINGS -> Glyph.DOOR
            else -> Glyph.DOT
        }
        "sensor" -> if (e.deviceClass == "temperature") Glyph.THERMO else Glyph.SENSOR
        "person", "device_tracker" -> Glyph.PERSON
        "scene" -> Glyph.SPARK
        "script", "button", "input_button" -> Glyph.PLAY
        "weather" -> Glyph.WEATHER
        else -> Glyph.DOT
    }

    // ---------- Comandi ----------

    data class Command(val domain: String, val service: String, val label: String, val confirm: Boolean)

    /** Cosa succede toccando l'entità. Null = solo lettura (serrature e allarmi compresi, per sicurezza). */
    fun tapCommand(e: EntityState): Command? {
        if (e.state == "unavailable") return null
        return when (e.domain) {
            "light", "switch", "fan", "input_boolean", "humidifier", "siren" -> Command(e.domain, "toggle", "Commuta", false)
            "cover" -> Command("cover", "toggle", "Apri o chiudi", false)
            "media_player" -> Command("media_player", "media_play_pause", "Riproduci o metti in pausa", false)
            "scene" -> Command("scene", "turn_on", "Attiva", false)
            "script" -> Command("script", "turn_on", "Avvia", true)
            "button" -> Command("button", "press", "Premi", true)
            "input_button" -> Command("input_button", "press", "Premi", true)
            "vacuum" -> if (e.state == "cleaning") {
                Command("vacuum", "return_to_base", "Rimanda alla base", true)
            } else {
                Command("vacuum", "start", "Avvia la pulizia", true)
            }
            "input_select" -> Command("input_select", "select_next", "Passa al successivo", false)
            "select" -> Command("select", "select_next", "Passa al successivo", false)
            else -> null
        }
    }

    fun supportsToggle(e: EntityState) = e.domain in TOGGLE_DOMAINS && !e.unavailable

    // ---------- Testi ----------

    fun describe(e: EntityState): String {
        if (e.state == "unavailable") return "Non disponibile"
        val a = e.attributes
        return when (e.domain) {
            "light" -> when (e.state) {
                "on" -> brightness(e)?.let { "Accesa al ${(it * 100).roundToInt()}%" } ?: "Accesa"
                "off" -> "Spenta"
                else -> cap(e.state)
            }
            "switch", "fan", "input_boolean", "humidifier", "siren", "automation" -> when (e.state) {
                "on" -> "Acceso"
                "off" -> "Spento"
                else -> cap(e.state)
            }
            "cover", "valve" -> {
                val pos = coverPosition(e)
                when (e.state) {
                    "open" -> if (pos != null && pos in 1..99) "Aperta al $pos%" else "Aperta"
                    "closed" -> "Chiusa"
                    "opening" -> "In apertura"
                    "closing" -> "In chiusura"
                    else -> cap(e.state)
                }
            }
            "climate", "water_heater" -> {
                val current = a.str("current_temperature")?.let { number(it) }
                val target = a.str("temperature")?.let { number(it) }
                val mode = hvac(e.state)
                when {
                    current != null && target != null && e.state != "off" -> "$mode, $current° su $target°"
                    current != null -> "$mode, $current°"
                    else -> mode
                }
            }
            "media_player" -> when (e.state) {
                "playing" -> a.str("media_title") ?: "In riproduzione"
                "paused" -> a.str("media_title")?.let { "In pausa: $it" } ?: "In pausa"
                "idle" -> "Inattivo"
                "off" -> "Spento"
                "on" -> "Acceso"
                "standby" -> "In standby"
                else -> cap(e.state)
            }
            "vacuum" -> when (e.state) {
                "cleaning" -> "Sta pulendo"
                "docked" -> "Alla base"
                "returning" -> "Torna alla base"
                "paused" -> "In pausa"
                "idle" -> "Fermo"
                "error" -> "Errore"
                else -> cap(e.state)
            }
            "lock" -> when (e.state) {
                "locked" -> "Chiusa a chiave"
                "unlocked", "open" -> "Aperta"
                "locking" -> "Si sta chiudendo"
                "unlocking" -> "Si sta aprendo"
                "jammed" -> "Bloccata"
                else -> cap(e.state)
            }
            "binary_sensor" -> {
                val on = e.state == "on"
                when (e.deviceClass) {
                    in OPENINGS -> if (on) "Aperta" else "Chiusa"
                    in MOTION -> if (on) "Movimento rilevato" else "Nessun movimento"
                    "moisture" -> if (on) "Acqua rilevata" else "Asciutto"
                    in ALARMS -> if (on) "Allarme" else "Tutto a posto"
                    else -> if (on) "Attivo" else "Inattivo"
                }
            }
            "sensor" -> if (e.state == "unknown") "Nessun dato" else {
                number(e.state)?.let { withUnit(it, a.str("unit_of_measurement")) } ?: cap(e.state)
            }
            "person", "device_tracker" -> when (e.state) {
                "home" -> "A casa"
                "not_home" -> "Fuori casa"
                else -> cap(e.state)
            }
            "scene" -> "Tocca per attivarla"
            "script" -> if (e.state == "on") "In esecuzione" else "Tocca per avviarlo"
            "button", "input_button" -> "Tocca per premerlo"
            "alarm_control_panel" -> when (e.state) {
                "disarmed" -> "Disinserito"
                "armed_away" -> "Inserito, fuori casa"
                "armed_home" -> "Inserito, in casa"
                "armed_night" -> "Inserito per la notte"
                "armed_vacation" -> "Inserito, in vacanza"
                "armed_custom_bypass" -> "Inserito"
                "arming" -> "Inserimento in corso"
                "pending" -> "In attesa"
                "triggered" -> "Allarme scattato"
                else -> cap(e.state)
            }
            "weather" -> {
                val t = a.str("temperature")?.let { number(it) }
                listOfNotNull(weather(e.state), t?.let { "$it°" }).joinToString(", ")
            }
            else -> if (e.state == "unknown") "Sconosciuto" else cap(e.state)
        }
    }

    /** Il testo breve sotto il nome nella tessera (il valore grande è già in alto a destra). */
    fun caption(e: EntityState): String = when {
        e.state == "unavailable" -> "Non disponibile"
        e.domain == "sensor" && bigValue(e) != null -> sensorLabel(e.deviceClass)
        e.domain == "weather" -> weather(e.state)
        e.domain == "climate" || e.domain == "water_heater" -> {
            val target = e.attributes.str("temperature")?.let { number(it) }
            if (target != null && e.state != "off") "${hvac(e.state)} a $target°" else hvac(e.state)
        }
        else -> describe(e)
    }

    /** Il numero grande in alto a destra della tessera. */
    fun bigValue(e: EntityState): String? {
        if (e.unavailable) return null
        val a = e.attributes
        return when (e.domain) {
            "sensor" -> number(e.state)?.let { n ->
                val unit = a.str("unit_of_measurement")
                when {
                    unit == null -> n
                    unit.startsWith("°") -> "$n°"
                    unit == "%" -> "$n%"
                    unit.length <= 4 -> "$n $unit"
                    else -> n
                }
            }
            "climate", "water_heater" -> a.str("current_temperature")?.let { number(it) }?.let { "$it°" }
            "weather" -> a.str("temperature")?.let { number(it) }?.let { "$it°" }
            else -> null
        }
    }

    /** "3 luci accese, 2 tapparelle aperte". */
    fun summarize(list: List<EntityState>): String {
        val lights = list.filter { it.domain == "light" }
        val lightsOn = lights.count { it.state == "on" }
        val coversOpen = list.count { it.domain == "cover" && (it.state == "open" || it.state == "opening") }
        val openings = list.count { it.domain == "binary_sensor" && it.deviceClass in OPENINGS && it.state == "on" }
        val alarms = list.count { it.domain == "binary_sensor" && it.deviceClass in ALARMS && it.state == "on" }
        val playing = list.any { it.domain == "media_player" && it.state == "playing" }
        val cleaning = list.any { it.domain == "vacuum" && it.state == "cleaning" }

        val parts = mutableListOf<String>()
        if (alarms > 0) parts += if (alarms == 1) "un allarme attivo" else "$alarms allarmi attivi"
        if (lightsOn > 0) parts += if (lightsOn == 1) "una luce accesa" else "$lightsOn luci accese"
        if (coversOpen > 0) parts += if (coversOpen == 1) "una tapparella aperta" else "$coversOpen tapparelle aperte"
        if (openings > 0) parts += if (openings == 1) "una porta o finestra aperta" else "$openings porte o finestre aperte"
        if (playing) parts += "musica in riproduzione"
        if (cleaning) parts += "il robot sta pulendo"

        if (parts.isEmpty()) {
            return when {
                lights.isNotEmpty() -> "Luci spente"
                list.isEmpty() -> "Niente da mostrare"
                list.size == 1 -> "Un elemento"
                else -> "${list.size} elementi"
            }
        }
        return parts.take(2).joinToString(", ").replaceFirstChar { it.uppercase() }
    }

    fun temperatureOf(list: List<EntityState>): String? {
        list.firstOrNull { it.domain == "sensor" && it.deviceClass == "temperature" && number(it.state) != null }
            ?.let { return "${number(it.state)}°" }
        return list.firstOrNull { it.domain == "climate" }
            ?.attributes?.str("current_temperature")?.let { number(it) }?.let { "$it°" }
    }

    /** Impronta di ciò che si vede di un'entità: se non cambia, non si ridisegna. */
    fun fingerprint(e: EntityState): String {
        val a = e.attributes
        return buildString {
            append(e.state)
            for (key in FINGERPRINT_KEYS) append('|').append(a.opt(key))
        }
    }

    private val FINGERPRINT_KEYS = listOf(
        "brightness", "rgb_color", "hs_color", "color_temp_kelvin", "current_position",
        "media_title", "current_temperature", "temperature", "friendly_name",
    )

    fun number(raw: String): String? {
        val d = raw.toDoubleOrNull() ?: return null
        if (d.isNaN() || d.isInfinite()) return null
        return if (abs(d) >= 100 || d == floor(d)) {
            String.format(Locale.ITALY, "%.0f", d)
        } else {
            String.format(Locale.ITALY, "%.1f", d)
        }
    }

    private fun withUnit(n: String, unit: String?): String = when {
        unit == null -> n
        unit.startsWith("°") || unit == "%" -> "$n$unit"
        else -> "$n $unit"
    }

    private fun sensorLabel(deviceClass: String?): String = when (deviceClass) {
        "temperature" -> "Temperatura"
        "humidity" -> "Umidità"
        "power" -> "Potenza"
        "energy" -> "Energia"
        "carbon_dioxide" -> "Anidride carbonica"
        "pm25" -> "Polveri sottili"
        "illuminance" -> "Luminosità"
        "battery" -> "Batteria"
        "pressure" -> "Pressione"
        else -> "Sensore"
    }

    private fun hvac(state: String): String = when (state) {
        "off" -> "Spento"
        "heat" -> "Riscaldamento"
        "cool" -> "Raffrescamento"
        "heat_cool", "auto" -> "Automatico"
        "dry" -> "Deumidifica"
        "fan_only" -> "Ventilazione"
        "eco" -> "Eco"
        "electric", "performance", "heat_pump", "gas" -> "Acceso"
        else -> cap(state)
    }

    private fun weather(state: String): String = when (state) {
        "clear-night" -> "Sereno"
        "sunny" -> "Soleggiato"
        "partlycloudy" -> "Poco nuvoloso"
        "cloudy" -> "Nuvoloso"
        "rainy" -> "Pioggia"
        "pouring" -> "Rovesci"
        "lightning" -> "Temporale"
        "lightning-rainy" -> "Temporale con pioggia"
        "snowy" -> "Neve"
        "snowy-rainy" -> "Pioggia e neve"
        "fog" -> "Nebbia"
        "hail" -> "Grandine"
        "windy", "windy-variant" -> "Ventoso"
        "exceptional" -> "Condizioni eccezionali"
        else -> cap(state)
    }

    private fun cap(s: String) = s.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
