package it.plancia.ha.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import it.plancia.ha.car.CarDeps
import it.plancia.ha.car.RowIcons
import it.plancia.ha.car.render.GroupTile
import it.plancia.ha.car.render.Scene
import it.plancia.ha.data.Connection
import it.plancia.ha.data.Group
import it.plancia.ha.logic.Entities

/** Prima schermata: le plance a sinistra, la casa stanza per stanza a destra. */
class HomeScreen(carContext: CarContext, deps: CarDeps) : MapScreen(carContext, deps) {

    private fun rooms(): List<Group> {
        val registry = repo.registry.value
        val states = repo.states.value
        return Entities.cleanGroups(registry.areas, states).ifEmpty { Entities.cleanGroups(registry.labels, states) }
    }

    override fun scene() = Scene(
        id = "home",
        title = "Casa",
        tiles = rooms().map { GroupTile(it.id, it.name, it.entityIds) },
        emptyMessage = "Nessuna stanza: assegna le entità alle aree in Home Assistant",
    )

    override fun watchedIds() = rooms().flatMap { it.entityIds }

    override fun structureKey(): String =
        repo.dashboards.value.joinToString(",") { it.title } + "|" + rooms().joinToString(",") { it.id } +
            "|" + repo.registry.value.labels.size

    override fun content(): Template {
        val conn = repo.connection.value
        val states = repo.states.value
        if (conn is Connection.NotConfigured) {
            return listTemplate(
                TITLE,
                listOf(infoRow("Configura Plancia", "Apri l’app sul telefono e inserisci indirizzo e token")),
                root = true,
            )
        }
        if (states.isEmpty()) {
            return if (conn is Connection.Failed) {
                listTemplate(TITLE, listOf(infoRow("Home Assistant non risponde", conn.message), actionRow("Riprova") { repo.reconnect() }), root = true)
            } else {
                listTemplate(TITLE, emptyList(), loading = true, root = true)
            }
        }

        val registry = repo.registry.value
        val extras = mutableListOf<Row>()
        val areas = Entities.cleanGroups(registry.areas, states)
        if (areas.isNotEmpty()) {
            val all = areas.flatMap { it.entityIds }.mapNotNull { states[it] }
            extras += browsableRow("Stanze", Entities.summarize(all), RowIcons.rooms()) {
                screenManager.push(GroupsScreen(carContext, deps, GroupsScreen.Kind.AREAS))
            }
        }
        val labels = Entities.cleanGroups(registry.labels, states)
        if (labels.isNotEmpty()) {
            extras += browsableRow("Etichette", "${labels.size} etichette", RowIcons.labels()) {
                screenManager.push(GroupsScreen(carContext, deps, GroupsScreen.Kind.LABELS))
            }
        }
        val room = (listLimit - extras.size).coerceAtLeast(1)
        val boards = repo.dashboards.value.take(room).map { dashboard ->
            browsableRow(dashboard.title, "Plancia", RowIcons.dashboard()) {
                screenManager.push(DashboardScreen(carContext, deps, dashboard))
            }
        }
        return listTemplate(TITLE, boards + extras, root = true)
    }

    override fun actionStrip(): ActionStrip = ActionStrip.Builder()
        .addAction(
            Action.Builder()
                .setTitle("Aggiorna")
                .setOnClickListener {
                    repo.reconnect()
                    toast("Rileggo Home Assistant")
                }
                .build()
        )
        .build()

    private companion object {
        const val TITLE = "Plancia"
    }
}
