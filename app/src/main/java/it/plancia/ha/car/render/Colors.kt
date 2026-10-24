package it.plancia.ha.car.render

import android.graphics.Color
import it.plancia.ha.data.EntityState
import it.plancia.ha.data.str
import it.plancia.ha.logic.Entities
import kotlin.math.ln
import kotlin.math.pow

/**
 * I colori vengono dalla casa: una luce accesa illumina la sua tessera del colore che ha davvero.
 * Gli altri domini hanno un tono fisso, sobrio, che non compete con le luci.
 */
object Colors {

    val WARM = 0xFFFFC27A.toInt()
    val SKY = 0xFF6FB6FF.toInt()
    val LAVENDER = 0xFFB59BFF.toInt()
    private val AMBER = 0xFFFFC857.toInt()
    private val HEAT = 0xFFFF8F5A.toInt()
    private val COOL = 0xFF5CC8FF.toInt()
    private val TEAL = 0xFF6EE0CF.toInt()
    private val VIOLET = 0xFFB98CFF.toInt()
    private val GREEN = 0xFF5BD98C.toInt()
    private val RED = 0xFFFF6B6B.toInt()
    private val CORAL = 0xFFFF8466.toInt()
    private val ORANGE = 0xFFFFB347.toInt()
    private val ICE = 0xFF8FD3F5.toInt()
    private val PINK = 0xFFFF86C8.toInt()
    private val BLUE = 0xFF7AA7FF.toInt()
    private val SUN = 0xFFFFD36E.toInt()
    private val SLATE = 0xFF9AA6B8.toInt()

    fun accent(e: EntityState): Int = when (e.domain) {
        "light" -> lightColor(e)
        "cover", "valve" -> SKY
        "switch", "input_boolean", "fan", "humidifier", "siren", "automation" -> AMBER
        "climate", "water_heater" -> when (e.state) {
            "cool" -> COOL
            "heat" -> HEAT
            else -> TEAL
        }
        "media_player" -> VIOLET
        "vacuum" -> GREEN
        "lock" -> if (e.state == "locked") GREEN else RED
        "binary_sensor" -> when (e.deviceClass) {
            in Entities.OPENINGS -> CORAL
            in Entities.ALARMS -> RED
            else -> ORANGE
        }
        "sensor" -> ICE
        "person", "device_tracker" -> GREEN
        "scene" -> PINK
        "script", "button", "input_button" -> BLUE
        "alarm_control_panel" -> if (e.state == "triggered") RED else ORANGE
        "weather" -> SUN
        else -> SLATE
    }

    /** Il colore reale della luce: rgb, poi tonalità/saturazione, poi temperatura colore. */
    fun lightColor(e: EntityState): Int {
        val a = e.attributes
        a.optJSONArray("rgb_color")?.let { rgb ->
            if (rgb.length() >= 3) return vivid(Color.rgb(rgb.optInt(0), rgb.optInt(1), rgb.optInt(2)))
        }
        a.optJSONArray("hs_color")?.let { hs ->
            if (hs.length() >= 2) {
                val hue = hs.optDouble(0, 0.0).toFloat()
                val sat = (hs.optDouble(1, 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                return Color.HSVToColor(floatArrayOf(hue, sat, 1f))
            }
        }
        a.str("color_temp_kelvin")?.toDoubleOrNull()?.let { return kelvin(it.toInt()) }
        return WARM
    }

    /** Porta la luminosità al massimo: il colore resta quello, l'intensità la dà l'alone. */
    private fun vivid(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[1] < 0.06f) return 0xFFFFF4E2.toInt()
        hsv[2] = 1f
        return Color.HSVToColor(hsv)
    }

    /** Approssimazione di Tanner Helland per il colore del corpo nero. */
    fun kelvin(kelvin: Int): Int {
        val t = kelvin.coerceIn(1000, 40000) / 100.0
        val r = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)
        val g = if (t <= 66) 99.4708025861 * ln(t) - 161.1195681661 else 288.1221695283 * (t - 60).pow(-0.0755148492)
        val b = when {
            t >= 66 -> 255.0
            t <= 19 -> 0.0
            else -> 138.5177312231 * ln(t - 10) - 305.0447927307
        }
        return Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }

    fun mix(colors: List<Int>): Int {
        if (colors.isEmpty()) return WARM
        var r = 0; var g = 0; var b = 0
        for (c in colors) { r += Color.red(c); g += Color.green(c); b += Color.blue(c) }
        val n = colors.size
        return vivid(Color.rgb(r / n, g / n, b / n))
    }

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    fun blend(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(a: Int, b: Int) = (a + (b - a) * k).toInt()
        return Color.argb(
            ch(Color.alpha(from), Color.alpha(to)),
            ch(Color.red(from), Color.red(to)),
            ch(Color.green(from), Color.green(to)),
            ch(Color.blue(from), Color.blue(to)),
        )
    }

    private fun luminance(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0

    /** Inchiostro leggibile sopra un disco del colore dato. */
    fun inkOn(color: Int): Int = if (luminance(color) > 0.62) 0xFF1B1F27.toInt() else Color.WHITE

    /** Il testo di stato di una tessera accesa: il colore dell'entità, reso leggibile sullo sfondo. */
    fun stateInk(color: Int, dark: Boolean): Int =
        if (dark) blend(color, Color.WHITE, 0.25f) else blend(color, Color.BLACK, 0.45f)
}
