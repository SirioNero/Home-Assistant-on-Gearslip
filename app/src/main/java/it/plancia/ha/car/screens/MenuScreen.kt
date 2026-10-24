package it.plancia.ha.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import it.plancia.ha.car.CarDeps
import it.plancia.ha.data.Connection
import it.plancia.ha.data.Dashboard
import it.plancia.ha.data.Panels
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Il menu a scomparsa: le plance di Home Assistant nell'ordine della sua barra laterale.
 * Scegliendone una il menu si chiude e la plancia si apre a tutto schermo.
 */
class MenuScreen(carContext: CarContext, deps: CarDeps) : DrawerScreen(carContext, deps) {

    init {
        deps.repo.refreshDashboards()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(deps.repo.dashboards, deps.repo.haDefault, deps.repo.connection) { dashboards, haDefault, connection ->
                    "$dashboards|$haDefault|${connection.javaClass.simpleName}"
                }.distinctUntilChanged().drop(1).collect { invalidate() }
            }
        }
    }

    override fun content(): Template {
        val repo = deps.repo
        val connection = repo.connection.value
        if (connection is Connection.NotConfigured) {
            val list = ItemList.Builder()
                .addItem(row("Configura Plancia", "Apri l’app sul telefono e inserisci indirizzo e token"))
                .build()
            return listTemplate(TITLE, list)
        }
        val dashboards = repo.dashboards.value
        if (dashboards.isEmpty() && connection !is Connection.Connected) return listTemplate(TITLE, null, loading = true)

        val haDefault = repo.haDefault.value
        val open = deps.display.current ?: haDefault
        val default = deps.settings.defaultDashboard ?: haDefault
        val list = ItemList.Builder()
        for (dashboard in dashboards) {
            val notes = listOfNotNull(
                "Aperta".takeIf { dashboard.urlPath == open },
                "Predefinita".takeIf { dashboard.urlPath == default },
            ).joinToString(" · ")
            list.addItem(row(dashboard.title, notes) { choose(dashboard.urlPath) })
        }
        list.addItem(row("Plancia predefinita", defaultLabel(dashboards, haDefault)) {
            screenManager.push(DefaultDashboardScreen(carContext, deps))
        })
        list.addItem(row("Dimensione", "${deps.settings.zoom}%") {
            screenManager.push(ZoomScreen(carContext, deps))
        })
        list.addItem(row("Ricarica la plancia", null) {
            deps.display.reload()
            screenManager.popToRoot()
        })
        return listTemplate(TITLE, list.build())
    }

    private fun choose(urlPath: String) {
        deps.display.open(urlPath)
        screenManager.popToRoot()
    }

    private fun defaultLabel(dashboards: List<Dashboard>, haDefault: String?): String {
        fun titleOf(urlPath: String) = dashboards.firstOrNull { it.urlPath == urlPath }?.title ?: Panels.title(urlPath, null)
        val saved = deps.settings.defaultDashboard
        if (saved != null) return titleOf(saved)
        return if (haDefault != null) "Come in Home Assistant: ${titleOf(haDefault)}" else "Come in Home Assistant"
    }

    private companion object {
        const val TITLE = "Plance"
    }
}
