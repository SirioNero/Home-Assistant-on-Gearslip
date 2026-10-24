package it.plancia.ha.car.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import it.plancia.ha.data.EntityState
import it.plancia.ha.logic.Entities
import kotlin.math.max

/**
 * Disegna intestazione e tessere sulla superficie dell'auto.
 * Tipografia da quadro strumenti: nomi in condensato, valori in cifre sottili.
 * L'unico elemento che brilla è la luce vera delle lampade.
 */
class TilePainter(val palette: Palette, private val dp: Float) {

    private val p = palette
    private val entityRadius = 16f * dp
    private val groupRadius = 22f * dp

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyphFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG)

    private val nameFont = text(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    private val headFont = text(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    private val bodyFont = text(Typeface.create("sans-serif", Typeface.NORMAL))
    private val numberFont = text(Typeface.create("sans-serif-light", Typeface.NORMAL))

    private fun text(face: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face }

    // ---------- Sfondo e intestazione ----------

    fun background(c: Canvas, w: Int, h: Int, ambient: Int?, area: RectF) {
        fill.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), p.bgTop, p.bgBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        if (ambient != null) {
            val radius = max(area.width(), area.height()) * 0.95f
            fill.shader = RadialGradient(
                area.right, area.top, radius,
                Colors.withAlpha(ambient, if (p.dark) 64 else 52), Colors.withAlpha(ambient, 0),
                Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        }
        fill.shader = null
    }

    fun header(c: Canvas, r: RectF, title: String, subtitle: String, clock: String, status: Int) {
        numberFont.textSize = 24f * dp
        numberFont.color = p.text
        numberFont.textAlign = Paint.Align.RIGHT
        val clockWidth = numberFont.measureText(clock)
        c.drawText(clock, r.right, r.top + 24f * dp, numberFont)
        numberFont.textAlign = Paint.Align.LEFT

        fill.color = status
        c.drawCircle(r.right - clockWidth - 12f * dp, r.top + 16f * dp, 4f * dp, fill)

        headFont.textSize = 24f * dp
        headFont.color = p.text
        c.drawText(ellipsize(title, headFont, r.width() - clockWidth - 36f * dp), r.left, r.top + 24f * dp, headFont)

        bodyFont.textSize = 14f * dp
        bodyFont.color = p.muted
        c.drawText(ellipsize(subtitle, bodyFont, r.width()), r.left, r.top + 45f * dp, bodyFont)
    }

    fun message(c: Canvas, r: RectF, head: String, detail: String?) {
        headFont.textSize = 21f * dp
        headFont.color = p.text
        headFont.textAlign = Paint.Align.CENTER
        val cy = r.centerY() - 10f * dp
        c.drawText(ellipsize(head, headFont, r.width()), r.centerX(), cy, headFont)
        headFont.textAlign = Paint.Align.LEFT
        if (!detail.isNullOrBlank()) {
            bodyFont.textSize = 14f * dp
            bodyFont.color = p.muted
            val width = (r.width() * 0.85f).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(detail, 0, detail.length, bodyFont, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(4)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            c.save()
            c.translate(r.centerX() - width / 2f, cy + 12f * dp)
            layout.draw(c)
            c.restore()
        }
    }

    fun scrollbar(c: Canvas, x: Float, top: Float, height: Float, contentHeight: Float, scroll: Float) {
        if (contentHeight <= height || height <= 0f) return
        val thumb = (height * height / contentHeight).coerceAtLeast(28f * dp)
        val t = top + (height - thumb) * (scroll / (contentHeight - height)).coerceIn(0f, 1f)
        line.strokeCap = Paint.Cap.ROUND
        line.strokeWidth = 3f * dp
        line.color = Colors.withAlpha(p.muted, 50)
        c.drawLine(x, top, x, top + height, line)
        line.color = Colors.withAlpha(p.muted, 210)
        c.drawLine(x, t, x, t + thumb, line)
    }

    // ---------- Tessera di un'entità ----------

    fun entity(c: Canvas, r: RectF, e: EntityState, name: String, pressed: Boolean) {
        val active = Entities.isActive(e) && !e.unavailable
        val info = !active && Entities.isInformational(e) && !e.unavailable
        val accent = Colors.accent(e)
        base(c, r, entityRadius, pressed)

        val pad = 14f * dp
        val gr = 19f * dp
        val gx = r.left + pad + gr
        val gy = r.top + pad + gr

        if (active) {
            val strength = if (e.domain == "light") 0.35f + 0.65f * (Entities.brightness(e) ?: 1f) else 0.5f
            glow(c, r, entityRadius, gx, gy, accent, strength)
        }

        when {
            active -> {
                fill.shader = RadialGradient(
                    gx - gr * 0.4f, gy - gr * 0.4f, gr * 1.6f,
                    Colors.blend(accent, Color.WHITE, 0.45f), accent, Shader.TileMode.CLAMP,
                )
                c.drawCircle(gx, gy, gr, fill)
                fill.shader = null
            }
            info -> {
                fill.color = Colors.withAlpha(accent, if (p.dark) 46 else 52)
                c.drawCircle(gx, gy, gr, fill)
            }
            else -> {
                fill.color = p.glyphOff
                c.drawCircle(gx, gy, gr, fill)
            }
        }
        val ink = when {
            active -> Colors.inkOn(accent)
            info -> if (p.dark) accent else Colors.blend(accent, Color.BLACK, 0.35f)
            else -> p.glyphOffInk
        }
        Glyphs.draw(c, Entities.glyph(e), gx, gy, gr * 0.56f, ink, e, active, glyphStroke, glyphFill)

        Entities.progress(e)?.let { progress ->
            val rr = gr + 5f * dp
            line.strokeWidth = 3f * dp
            line.strokeCap = Paint.Cap.ROUND
            line.color = Colors.withAlpha(accent, 56)
            c.drawCircle(gx, gy, rr, line)
            line.color = if (p.dark) accent else Colors.blend(accent, Color.BLACK, 0.2f)
            c.drawArc(gx - rr, gy - rr, gx + rr, gy + rr, -90f, 360f * progress, false, line)
        }

        Entities.bigValue(e)?.let { value ->
            numberFont.textSize = 28f * dp
            numberFont.color = p.text
            numberFont.textAlign = Paint.Align.RIGHT
            val avail = r.width() - pad * 2 - gr * 2 - 12f * dp
            c.drawText(ellipsize(value, numberFont, avail), r.right - pad, gy + 10f * dp, numberFont)
            numberFont.textAlign = Paint.Align.LEFT
        }

        val avail = r.width() - pad * 2
        val stateY = r.bottom - pad
        bodyFont.textSize = 13f * dp
        bodyFont.color = if (active) Colors.stateInk(accent, p.dark) else p.muted
        c.drawText(ellipsize(Entities.caption(e), bodyFont, avail), r.left + pad, stateY, bodyFont)
        nameFont.textSize = 17f * dp
        nameFont.color = if (e.unavailable) p.muted else p.text
        c.drawText(ellipsize(name, nameFont, avail), r.left + pad, stateY - 19f * dp, nameFont)
    }

    // ---------- Tessera di una stanza o vista ----------

    fun group(c: Canvas, r: RectF, name: String, members: List<EntityState>, pressed: Boolean) {
        base(c, r, groupRadius, pressed)
        val pad = 15f * dp
        val lightsOn = members.filter { it.domain == "light" && it.state == "on" }
        val mixed = if (lightsOn.isEmpty()) null else Colors.mix(lightsOn.map(Colors::lightColor))
        if (mixed != null) {
            glow(
                c, r, groupRadius, r.left + r.width() * 0.22f, r.bottom - r.height() * 0.18f,
                mixed, (0.5f + 0.1f * lightsOn.size).coerceAtMost(0.95f),
            )
        }

        headFont.textSize = 19f * dp
        headFont.color = p.text
        c.drawText(ellipsize(name, headFont, r.width() - pad * 2 - 16f * dp), r.left + pad, r.top + pad + 17f * dp, headFont)
        chevron(c, r.right - pad - 3f * dp, r.top + pad + 11f * dp, 4.5f * dp)

        bodyFont.textSize = 13f * dp
        bodyFont.color = if (mixed != null) Colors.stateInk(mixed, p.dark) else p.muted
        c.drawText(ellipsize(Entities.summarize(members), bodyFont, r.width() - pad * 2), r.left + pad, r.top + pad + 37f * dp, bodyFont)

        var right = r.right - pad
        Entities.temperatureOf(members)?.let { temperature ->
            numberFont.textSize = 26f * dp
            numberFont.color = p.text
            numberFont.textAlign = Paint.Align.RIGHT
            c.drawText(temperature, right, r.bottom - pad, numberFont)
            right -= numberFont.measureText(temperature) + 10f * dp
            numberFont.textAlign = Paint.Align.LEFT
        }

        // Una fila di piccoli indicatori: le luci col loro colore, poi gli altri dispositivi.
        val chipRadius = 7f * dp
        val step = chipRadius * 2 + 8f * dp
        val y = r.bottom - pad - chipRadius
        val room = ((right - (r.left + pad)) / step).toInt().coerceAtLeast(0)
        val chips = members.filter { it.domain != "sensor" && it.domain != "weather" }
            .sortedWith(compareBy<EntityState>({ it.domain != "light" }, { !Entities.isActive(it) }))
        val shown = if (chips.size > room) chips.take((room - 1).coerceAtLeast(0)) else chips
        var x = r.left + pad + chipRadius
        for (member in shown) {
            chip(c, x, y, chipRadius, member)
            x += step
        }
        if (shown.size < chips.size && room > 0) {
            bodyFont.textSize = 12f * dp
            bodyFont.color = p.muted
            c.drawText("+${chips.size - shown.size}", x - chipRadius, y + 4f * dp, bodyFont)
        }
    }

    // ---------- Parti comuni ----------

    private fun base(c: Canvas, r: RectF, radius: Float, pressed: Boolean) {
        fill.shader = LinearGradient(r.left, r.top, r.left, r.bottom, p.tileTop, p.tile, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, fill)
        fill.shader = null
        line.strokeWidth = max(1f, dp)
        line.color = p.stroke
        c.drawRoundRect(r, radius, radius, line)
        if (pressed) {
            fill.color = p.pressed
            c.drawRoundRect(r, radius, radius, fill)
        }
    }

    private fun glow(c: Canvas, r: RectF, radius: Float, cx: Float, cy: Float, color: Int, strength: Float) {
        val reach = max(r.width(), r.height()) * 1.05f
        fill.shader = RadialGradient(
            cx, cy, reach,
            intArrayOf(
                Colors.withAlpha(color, (strength * (if (p.dark) 150 else 120)).toInt()),
                Colors.withAlpha(color, (strength * 40).toInt()),
                Colors.withAlpha(color, 0),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(r, radius, radius, fill)
        fill.shader = null
    }

    private fun chip(c: Canvas, x: Float, y: Float, radius: Float, e: EntityState) {
        val active = Entities.isActive(e) && !e.unavailable
        val accent = Colors.accent(e)
        if (active) {
            fill.shader = RadialGradient(x, y, radius * 2.1f, Colors.withAlpha(accent, 110), Colors.withAlpha(accent, 0), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, radius * 2.1f, fill)
            fill.shader = RadialGradient(
                x - radius * 0.35f, y - radius * 0.35f, radius * 1.5f,
                Colors.blend(accent, Color.WHITE, 0.5f), accent, Shader.TileMode.CLAMP,
            )
            c.drawCircle(x, y, radius, fill)
            fill.shader = null
        } else {
            fill.color = p.glyphOff
            c.drawCircle(x, y, radius, fill)
        }
        if (e.domain != "light") {
            Glyphs.draw(
                c, Entities.glyph(e), x, y, radius * 0.62f,
                if (active) Colors.inkOn(accent) else p.glyphOffInk, e, active, glyphStroke, glyphFill,
            )
        }
    }

    private fun chevron(c: Canvas, x: Float, y: Float, s: Float) {
        line.strokeWidth = 2f * dp
        line.strokeCap = Paint.Cap.ROUND
        line.strokeJoin = Paint.Join.ROUND
        line.color = p.muted
        val path = Path()
        path.moveTo(x - s * 0.5f, y - s)
        path.lineTo(x + s * 0.5f, y)
        path.lineTo(x - s * 0.5f, y + s)
        c.drawPath(path, line)
    }

    private fun ellipsize(s: String, paint: TextPaint, width: Float): String =
        if (width <= 0f) "" else TextUtils.ellipsize(s, paint, width, TextUtils.TruncateAt.END).toString()
}
