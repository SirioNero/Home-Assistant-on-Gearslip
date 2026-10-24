package it.plancia.ha.car.render

/** Ciò che la superficie deve mostrare: un titolo e una griglia di tessere. */
sealed interface Tile {
    val key: String
}

data class EntityTile(val entityId: String) : Tile {
    override val key: String get() = entityId
}

/** Una stanza, un'etichetta o una vista di plancia: si apre toccandola. */
data class GroupTile(val id: String, val title: String, val entityIds: List<String>) : Tile {
    override val key: String get() = "group:$id"
}

data class Scene(
    val id: String,
    val title: String,
    val tiles: List<Tile>,
    val emptyMessage: String = "Niente da mostrare qui",
    val stripPrefix: String? = null,
) {
    fun entityIds(): List<String> = tiles.flatMap {
        when (it) {
            is EntityTile -> listOf(it.entityId)
            is GroupTile -> it.entityIds
        }
    }

    companion object {
        val EMPTY = Scene("empty", "Plancia", emptyList())
    }
}
