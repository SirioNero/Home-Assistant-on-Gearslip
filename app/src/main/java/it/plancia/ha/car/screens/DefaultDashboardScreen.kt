package it.plancia.ha.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import it.plancia.ha.car.CarDeps
import it.plancia.ha.data.Panels

/** Quale plancia aprire salendo in auto. */
class DefaultDashboardScreen(carContext: CarContext, deps: CarDeps) : DrawerScreen(carContext, deps) {

    override fun content(): Template {
        val dashboards = deps.repo.dashboards.value
        val haDefault = deps.repo.haDefault.value
        val saved = deps.settings.defaultDashboard

        val options = mutableListOf<String?>(null)
        val list = ItemList.Builder()
        val haTitle = dashboards.firstOrNull { it.urlPath == haDefault }?.title
        list.addItem(row("Come in Home Assistant", haTitle ?: "La plancia che Home Assistant apre da solo"))
        for (dashboard in dashboards) {
            options += dashboard.urlPath
            list.addItem(row(dashboard.title, null))
        }
        if (saved != null && saved !in options) {
            options += saved
            list.addItem(row(Panels.title(saved, null), "Non è più nella barra laterale di Home Assistant"))
        }
        list.setSelectedIndex(options.indexOf(saved).coerceAtLeast(0))
        list.setOnSelectedListener { index ->
            deps.settings.defaultDashboard = options.getOrNull(index)
            screenManager.pop()
        }
        return listTemplate("Plancia predefinita", list.build())
    }
}
