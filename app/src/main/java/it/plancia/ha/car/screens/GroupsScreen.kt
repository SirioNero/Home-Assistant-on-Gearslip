package it.plancia.ha.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Template
import it.plancia.ha.car.CarDeps
import it.plancia.ha.car.RowIcons
import it.plancia.ha.car.render.GroupTile
import it.plancia.ha.car.render.Scene
import it.plancia.ha.data.Group
import it.plancia.ha.logic.Entities

/** Tutte le stanze (aree) o tutte le etichette. */
class GroupsScreen(carContext: CarContext, deps: CarDeps, private val kind: Kind) : MapScreen(carContext, deps) {

    enum class Kind(val title: String) { AREAS("Stanze"), LABELS("Etichette") }

    private fun groups(): List<Group> {
        val registry = repo.registry.value
        return Entities.cleanGroups(if (kind == Kind.AREAS) registry.areas else registry.labels, repo.states.value)
    }

    override fun scene() = Scene(
        id = "groups:${kind.name}",
        title = kind.title,
        tiles = groups().map { GroupTile(it.id, it.name, it.entityIds) },
    )

    override fun watchedIds() = groups().flatMap { it.entityIds }

    override fun structureKey() = groups().joinToString(",") { it.id }

    override fun content(): Template {
        val states = repo.states.value
        val rows = groups().map { group ->
            val members = group.entityIds.mapNotNull { states[it] }
            browsableRow(group.name, Entities.summarize(members), RowIcons.group(members)) {
                screenManager.push(
                    EntitiesScreen(carContext, deps, group.name, group.entityIds, sorted = true, stripPrefix = group.name)
                )
            }
        }
        return listTemplate(kind.title, rows)
    }
}
