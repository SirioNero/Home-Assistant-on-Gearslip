package it.plancia.ha.car.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import it.plancia.ha.data.EntityState
import it.plancia.ha.logic.Entities
import kotlin.math.cos
import kotlin.math.sin

enum class Glyph {
    LIGHT, COVER, POWER, THERMO, MEDIA, VACUUM, LOCK, SENSOR, DOOR, MOTION,
    PERSON, SPARK, PLAY, WEATHER, HOUSE, TAG, GRID, DOT,
}

/**
 * Pittogrammi disegnati con primitive: nessun font di icone, restano nitidi a ogni densità.
 * (x, y) è il centro, s il mezzo lato del quadrato in cui stanno.
 */
object Glyphs {

    fun draw(
        c: Canvas, glyph: Glyph, x: Float, y: Float, s: Float, ink: Int,
        e: EntityState?, active: Boolean, stroke: Paint, fill: Paint,
    ) {
        stroke.reset()
        stroke.isAntiAlias = true
        stroke.style = Paint.Style.STROKE
        stroke.color = ink
        stroke.strokeWidth = s * 0.2f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        fill.reset()
        fill.isAntiAlias = true
        fill.style = Paint.Style.FILL
        fill.color = ink

        when (glyph) {
            Glyph.LIGHT -> {
                stroke.strokeWidth = s * 0.16f
                if (active) {
                    c.drawCircle(x, y, s * 0.42f, fill)
                    for (i in 0 until 8) {
                        val a = Math.toRadians(i * 45.0)
                        val dx = cos(a).toFloat()
                        val dy = sin(a).toFloat()
                        c.drawLine(x + dx * s * 0.68f, y + dy * s * 0.68f, x + dx * s * 0.98f, y + dy * s * 0.98f, stroke)
                    }
                } else {
                    c.drawCircle(x, y, s * 0.5f, stroke)
                }
            }

            Glyph.COVER -> {
                stroke.strokeWidth = s * 0.14f
                val l = x - s * 0.8f
                val t = y - s * 0.8f
                val r = x + s * 0.8f
                val b = y + s * 0.8f
                c.drawRoundRect(l, t, r, b, s * 0.15f, s * 0.15f, stroke)
                val pos = e?.let { Entities.coverPosition(it) ?: if (it.state == "closed") 0 else 100 } ?: 0
                val closed = 1f - pos / 100f
                val inset = s * 0.3f
                val span = b - t - inset * 2
                for (i in 0 until 5) {
                    val f = (i + 0.5f) / 5f
                    if (f <= closed + 0.02f) {
                        val sy = t + inset + span * (i / 4f)
                        c.drawLine(l + inset, sy, r - inset, sy, stroke)
                    }
                }
            }

            Glyph.POWER -> {
                val r = s * 0.72f
                c.drawArc(x - r, y - r, x + r, y + r, -60f, 300f, false, stroke)
                c.drawLine(x, y - s * 0.95f, x, y - s * 0.2f, stroke)
            }

            Glyph.THERMO -> {
                stroke.strokeWidth = s * 0.28f
                c.drawLine(x, y - s * 0.8f, x, y + s * 0.2f, stroke)
                c.drawCircle(x, y + s * 0.5f, s * 0.36f, fill)
            }

            Glyph.MEDIA -> if (active) {
                val w = s * 0.26f
                c.drawRoundRect(x - s * 0.5f, y - s * 0.62f, x - s * 0.5f + w, y + s * 0.62f, w / 2, w / 2, fill)
                c.drawRoundRect(x + s * 0.5f - w, y - s * 0.62f, x + s * 0.5f, y + s * 0.62f, w / 2, w / 2, fill)
            } else {
                triangle(c, x + s * 0.08f, y, s * 0.7f, fill)
            }

            Glyph.VACUUM -> {
                stroke.strokeWidth = s * 0.16f
                c.drawCircle(x, y, s * 0.82f, stroke)
                c.drawCircle(x, y - s * 0.18f, s * 0.24f, fill)
                c.drawLine(x - s * 0.42f, y + s * 0.38f, x + s * 0.42f, y + s * 0.38f, stroke)
            }

            Glyph.LOCK -> {
                val locked = e != null &&
                    (e.state == "locked" || (e.domain == "alarm_control_panel" && e.state.startsWith("armed")))
                c.drawRoundRect(x - s * 0.62f, y - s * 0.1f, x + s * 0.62f, y + s * 0.8f, s * 0.18f, s * 0.18f, fill)
                stroke.strokeWidth = s * 0.18f
                val lift = if (locked) 0f else -s * 0.28f
                c.drawArc(x - s * 0.4f, y - s * 0.8f + lift, x + s * 0.4f, y + s * 0.1f + lift, 180f, 180f, false, stroke)
                c.drawLine(x - s * 0.4f, y - s * 0.35f + lift, x - s * 0.4f, y - s * 0.1f, stroke)
                if (locked) c.drawLine(x + s * 0.4f, y - s * 0.35f, x + s * 0.4f, y - s * 0.1f, stroke)
            }

            Glyph.SENSOR -> {
                val w = s * 0.3f
                val base = y + s * 0.72f
                val heights = floatArrayOf(0.62f, 1.36f, 0.98f)
                for (i in 0..2) {
                    val cx = x - s * 0.56f + i * s * 0.56f
                    c.drawRoundRect(cx - w / 2, base - heights[i] * s, cx + w / 2, base, w / 2, w / 2, fill)
                }
            }

            Glyph.DOOR -> {
                stroke.strokeWidth = s * 0.16f
                c.drawRoundRect(x - s * 0.5f, y - s * 0.85f, x + s * 0.5f, y + s * 0.85f, s * 0.1f, s * 0.1f, stroke)
                c.drawCircle(x + s * 0.22f, y + s * 0.05f, s * 0.12f, fill)
            }

            Glyph.MOTION -> {
                val ox = x - s * 0.45f
                c.drawCircle(ox, y, s * 0.2f, fill)
                stroke.strokeWidth = s * 0.15f
                for (i in 1..2) {
                    val r = s * (0.35f + 0.38f * i)
                    c.drawArc(ox - r, y - r, ox + r, y + r, -45f, 90f, false, stroke)
                }
            }

            Glyph.PERSON -> {
                c.drawCircle(x, y - s * 0.38f, s * 0.32f, fill)
                c.drawArc(x - s * 0.7f, y + s * 0.05f, x + s * 0.7f, y + s * 1.35f, 180f, 180f, true, fill)
            }

            Glyph.SPARK -> star(c, x, y, s * 0.92f, fill)

            Glyph.PLAY -> triangle(c, x + s * 0.1f, y, s * 0.7f, fill)

            Glyph.WEATHER -> {
                c.drawCircle(x, y, s * 0.38f, fill)
                stroke.strokeWidth = s * 0.14f
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0 + 22.5)
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    c.drawLine(x + dx * s * 0.62f, y + dy * s * 0.62f, x + dx * s * 0.9f, y + dy * s * 0.9f, stroke)
                }
            }

            Glyph.HOUSE -> {
                stroke.strokeWidth = s * 0.16f
                val p = Path()
                p.moveTo(x - s * 0.78f, y - s * 0.05f)
                p.lineTo(x, y - s * 0.8f)
                p.lineTo(x + s * 0.78f, y - s * 0.05f)
                p.moveTo(x - s * 0.55f, y - s * 0.25f)
                p.lineTo(x - s * 0.55f, y + s * 0.78f)
                p.lineTo(x + s * 0.55f, y + s * 0.78f)
                p.lineTo(x + s * 0.55f, y - s * 0.25f)
                c.drawPath(p, stroke)
                c.drawCircle(x, y + s * 0.3f, s * 0.17f, fill)
            }

            Glyph.TAG -> {
                stroke.strokeWidth = s * 0.15f
                val p = Path()
                p.moveTo(x - s * 0.8f, y - s * 0.8f)
                p.lineTo(x + s * 0.05f, y - s * 0.8f)
                p.lineTo(x + s * 0.85f, y)
                p.lineTo(x + s * 0.05f, y + s * 0.8f)
                p.lineTo(x - s * 0.8f, y - s * 0.05f)
                p.close()
                c.drawPath(p, stroke)
                c.drawCircle(x - s * 0.38f, y - s * 0.38f, s * 0.13f, fill)
            }

            Glyph.GRID -> {
                val q = s * 0.66f
                val g = s * 0.22f
                val r = s * 0.16f
                c.drawRoundRect(x - g / 2 - q, y - g / 2 - q, x - g / 2, y - g / 2, r, r, fill)
                c.drawRoundRect(x + g / 2, y - g / 2 - q, x + g / 2 + q, y - g / 2, r, r, fill)
                c.drawRoundRect(x - g / 2 - q, y + g / 2, x - g / 2, y + g / 2 + q, r, r, fill)
                c.drawRoundRect(x + g / 2, y + g / 2, x + g / 2 + q, y + g / 2 + q, r, r, fill)
            }

            Glyph.DOT -> c.drawCircle(x, y, s * 0.36f, fill)
        }
    }

    private fun triangle(c: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val p = Path()
        p.moveTo(cx - r * 0.55f, cy - r * 0.85f)
        p.lineTo(cx + r * 0.85f, cy)
        p.lineTo(cx - r * 0.55f, cy + r * 0.85f)
        p.close()
        c.drawPath(p, paint)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val i = r * 0.26f
        val p = Path()
        p.moveTo(cx, cy - r)
        p.lineTo(cx + i, cy - i)
        p.lineTo(cx + r, cy)
        p.lineTo(cx + i, cy + i)
        p.lineTo(cx, cy + r)
        p.lineTo(cx - i, cy + i)
        p.lineTo(cx - r, cy)
        p.lineTo(cx - i, cy - i)
        p.close()
        c.drawPath(p, paint)
    }
}
