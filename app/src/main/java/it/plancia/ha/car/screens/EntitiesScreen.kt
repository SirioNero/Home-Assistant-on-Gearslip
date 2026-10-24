package it.plancia.ha.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Template
import it.plancia.ha.car.CarDeps
import it.plancia.ha.car.render.EntityTile
import it.plancia.ha.car.render.Scene
import it.plancia.ha.data.EntityState
import it.plancia.ha.logic.Entities

/** Le entità di una stanza, di un'etichetta o di una vista: tessere a destra, righe a sinistra. */
class EntitiesScreen(
    carContext: CarContext,
    deps: CarDeps,
    private val screenTitle: String,
    private val ids: List<String>,
    private val sorted: Boolean,
    private val stripPrefix: String?,
) : MapScreen(carContext, deps) {

    private fun entities(): List<EntityState> {
        val states = repo.states.value
        val list = ids.mapNotNull { states[it] }
        return if (sorted) Entities.sort(list) else list
    }

    override fun scene() = Scene(
        id = "entities:$screenTitle:${ids.hashCode()}",
        title = screenTitle,
        tiles = entities().map { EntityTile(it.entityId) },
        emptyMessage = "Nessuna entità disponibile",
        stripPrefix = stripPrefix,
    )

    override fun watchedIds() = ids

    override fun structureKey() = entities().size.toString()

    override fun content(): Template =
        listTemplate(screenTitle, entities().map { entityRow(it, stripPrefix) }, empty = "Nessuna entità disponibile")
}
