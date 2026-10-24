package it.plancia.ha.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import it.plancia.ha.car.CarDeps
import it.plancia.ha.car.RowIcons
import it.plancia.ha.car.render.GroupTile
import it.plancia.ha.car.render.Scene
import it.plancia.ha.car.render.Tile
import it.plancia.ha.data.DashView
import it.plancia.ha.data.Dashboard
import it.plancia.ha.data.EntityState
import it.plancia.ha.logic.Entities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Una plancia di Home Assistant: le sue viste diventano stanze da aprire. */
class DashboardScreen(
    carContext: CarContext,
    deps: CarDeps,
    private val dashboard: Dashboard,
) : MapScreen(carContext, deps) {

    private var views: List<DashView>? = null
    private var error: String? = null

    init {
        load()
    }

    private fun load() {
        error = null
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                views = repo.loadViews(dashboard)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Impossibile leggere la plancia"
            }
            refresh(force = true)
        }
    }

    private fun members(view: DashView): List<EntityState> {
        val states = repo.states.value
        return view.entityIds.mapNotNull { states[it] }
    }

    override fun scene() = Scene(
        id = "dashboard:${dashboard.urlPath}",
        title = dashboard.title,
        tiles = views.orEmpty().mapIndexed { i, view -> GroupTile("v$i", view.title, view.entityIds) },
        emptyMessage = when {
            error != null -> "Non riesco a leggere questa plancia"
            views == null -> "Leggo la plancia"
            else -> "Questa plancia non contiene entità"
        },
    )

    override fun watchedIds() = views.orEmpty().flatMap { it.entityIds }

    override fun structureKey() = "${views?.size}|$error"

    override fun content(): Template {
        val current = views
        val problem = error
        return when {
            current != null -> listTemplate(
                dashboard.title,
                current.map { view ->
                    val members = members(view)
                    browsableRow(view.title, Entities.summarize(members), RowIcons.group(members)) { open(view) }
                },
                empty = "Questa plancia non contiene entità",
            )
            problem != null -> listTemplate(
                dashboard.title,
                listOf(infoRow("Non riesco a leggere la plancia", problem), actionRow("Riprova") { load() }),
            )
            else -> listTemplate(dashboard.title, emptyList(), loading = true)
        }
    }

    override fun onTileTapped(tile: Tile) {
        if (tile is GroupTile) {
            val index = tile.id.removePrefix("v").toIntOrNull() ?: return
            views?.getOrNull(index)?.let { open(it) }
        } else {
            super.onTileTapped(tile)
        }
    }

    /** Nelle viste si rispetta l'ordine che hai dato tu alle schede. */
    private fun open(view: DashView) {
        screenManager.push(EntitiesScreen(carContext, deps, view.title, view.entityIds, sorted = false, stripPrefix = null))
    }
}
