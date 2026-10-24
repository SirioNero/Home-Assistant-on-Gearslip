package it.plancia.ha.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import it.plancia.ha.car.render.Colors
import it.plancia.ha.car.render.Glyph
import it.plancia.ha.car.render.Glyphs
import it.plancia.ha.data.EntityState
import it.plancia.ha.logic.Entities

/** Icone delle righe dell'elenco, generate al volo con lo stesso linguaggio delle tessere. */
object RowIcons {

    private const val SIZE = 96
    private const val C = SIZE / 2f
    private const val R = SIZE * 0.46f
    private val OFF_BG = 0xFF39424F.toInt()
    private val OFF_INK = 0xFFC3CBD8.toInt()
    private val cache = LruCache<String, CarIcon>(128)

    fun entity(e: EntityState): CarIcon {
        val active = Entities.isActive(e) && !e.unavailable
        val info = !active && Entities.isInformational(e) && !e.unavailable
        val accent = Colors.accent(e)
        val glyph = Entities.glyph(e)
        val detail = when (glyph) {
            Glyph.COVER -> Entities.coverPosition(e)?.toString() ?: e.state
            Glyph.LOCK, Glyph.MEDIA -> e.state
            else -> ""
        }
        val bg = when {
            active -> accent
            info -> Colors.blend(OFF_BG, accent, 0.4f)
            else -> OFF_BG
        }
        val ink = when {
            active -> Colors.inkOn(accent)
            info -> Colors.blend(accent, Color.WHITE, 0.3f)
            else -> OFF_INK
        }
        return disc("e/$glyph/$bg/$ink/$active/$detail", glyph, bg, ink, if (active) accent else null, e, active)
    }

    fun group(members: List<EntityState>): CarIcon {
        val on = members.filter { it.domain == "light" && it.state == "on" }
        if (on.isEmpty()) return disc("g/off", Glyph.LIGHT, OFF_BG, OFF_INK, null, null, false)
        val color = Colors.mix(on.map(Colors::lightColor))
        return disc("g/$color", Glyph.LIGHT, color, Colors.inkOn(color), color, null, true)
    }

    fun dashboard(): CarIcon = disc("d", Glyph.GRID, Colors.WARM, Colors.inkOn(Colors.WARM), null, null, true)
    fun rooms(): CarIcon = disc("r", Glyph.HOUSE, Colors.SKY, Colors.inkOn(Colors.SKY), null, null, true)
    fun labels(): CarIcon = disc("l", Glyph.TAG, Colors.LAVENDER, Colors.inkOn(Colors.LAVENDER), null, null, true)

    private fun disc(
        key: String, glyph: Glyph, bg: Int, ink: Int, glow: Int?, e: EntityState?, active: Boolean,
    ): CarIcon {
        cache.get(key)?.let { return it }
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
        var radius = R
        if (glow != null) {
            fill.shader = RadialGradient(C, C, SIZE / 2f, Colors.withAlpha(glow, 150), Colors.withAlpha(glow, 0), Shader.TileMode.CLAMP)
            canvas.drawCircle(C, C, SIZE / 2f, fill)
            fill.shader = null
            radius = R * 0.84f
        }
        fill.color = bg
        canvas.drawCircle(C, C, radius, fill)
        Glyphs.draw(canvas, glyph, C, C, radius * 0.55f, ink, e, active, stroke, fill)
        val icon = CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
        cache.put(key, icon)
        return icon
    }
}
