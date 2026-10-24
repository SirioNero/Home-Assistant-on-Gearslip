package it.plancia.ha.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import it.plancia.ha.car.CarDeps
import it.plancia.ha.data.Settings

/** Quanto grande disegnare la plancia: più grande si legge meglio, più piccola ci sta di più. */
class ZoomScreen(carContext: CarContext, deps: CarDeps) : DrawerScreen(carContext, deps) {

    override fun content(): Template {
        val levels = Settings.ZOOM_LEVELS
        val list = ItemList.Builder()
        for (level in levels) {
            list.addItem(row("$level%", if (level == 100) "Normale" else null))
        }
        val selected = levels.indexOf(deps.settings.zoom)
        list.setSelectedIndex(if (selected >= 0) selected else levels.indexOf(100))
        list.setOnSelectedListener { index ->
            val level = levels.getOrNull(index)
            if (level != null && level != deps.settings.zoom) deps.display.setZoom(level)
            screenManager.pop()
        }
        return listTemplate("Dimensione", list.build())
    }
}
