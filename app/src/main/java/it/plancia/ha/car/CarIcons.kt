package it.plancia.ha.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

/** Icone disegnate al volo, bianche: l'host le colora per il tema del momento. */
object CarIcons {

    private const val SIZE = 96

    /** Le tre righe del menu, come il pulsante ☰ di Home Assistant. */
    val menu: CarIcon by lazy {
        icon { canvas, paint ->
            paint.strokeWidth = SIZE * 0.1f
            for (i in 0..2) {
                val y = SIZE * (0.3f + 0.2f * i)
                canvas.drawLine(SIZE * 0.2f, y, SIZE * 0.8f, y, paint)
            }
        }
    }

    private fun icon(draw: (Canvas, Paint) -> Unit): CarIcon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        draw(Canvas(bitmap), paint)
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).setTint(CarColor.DEFAULT).build()
    }
}
