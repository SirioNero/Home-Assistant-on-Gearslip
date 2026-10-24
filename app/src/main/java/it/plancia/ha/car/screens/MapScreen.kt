package it.plancia.ha.car.screens

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import it.plancia.ha.car.CarDeps
import it.plancia.ha.car.RowIcons
import it.plancia.ha.car.render.EntityTile
import it.plancia.ha.car.render.GroupTile
import it.plancia.ha.car.render.Scene
import it.plancia.ha.car.render.Tile
import it.plancia.ha.data.EntityState
import it.plancia.ha.data.HaRepository
import it.plancia.ha.logic.Entities
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Base di ogni schermata: a sinistra un elenco (template), a destra la superficie disegnata.
 * Ascolta Home Assistant e aggiorna entrambi solo quando cambia qualcosa che si vede.
 */
abstract class MapScreen(carContext: CarContext, protected val deps: CarDeps) : Screen(carContext) {

    protected val repo: HaRepository get() = deps.repo

    private val main = Handler(Looper.getMainLooper())
    private var lastSignature: String? = null
    private var lastInvalidate = 0L
    private var invalidatePending = false

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                showScene()
                merge<Any>(repo.states, repo.connection, repo.registry, repo.dashboards)
                    .conflate()
                    .collect {
                        refresh()
                        delay(350)
                    }
            }
        }
    }

    /** La griglia da disegnare sulla superficie. */
    protected abstract fun scene(): Scene

    /** L'elenco a sinistra (un ListTemplate). */
    protected abstract fun content(): Template

    /** Le entità il cui stato si vede in questa schermata. */
    protected abstract fun watchedIds(): Collection<String>

    /** Cambia quando cambia la struttura (righe in più o in meno). */
    protected open fun structureKey(): String = ""

    protected open fun actionStrip(): ActionStrip? = null

    final override fun onGetTemplate(): Template {
        lastSignature = signature()
        val builder = MapWithContentTemplate.Builder().setContentTemplate(content())
        actionStrip()?.let { builder.setActionStrip(it) }
        return builder.build()
    }

    protected fun refresh(force: Boolean = false) {
        val signature = signature()
        if (!force && signature == lastSignature) return
        lastSignature = signature
        if (screenManager.top === this) showScene()
        scheduleInvalidate()
    }

    private fun showScene() {
        deps.renderer.show(scene()) { tile -> onTileTapped(tile) }
    }

    /** Al massimo un aggiornamento del template al secondo: gli host contano i refresh. */
    private fun scheduleInvalidate() {
        if (invalidatePending) return
        val wait = 1_000L - (SystemClock.uptimeMillis() - lastInvalidate)
        if (wait <= 0) {
            lastInvalidate = SystemClock.uptimeMillis()
            invalidate()
        } else {
            invalidatePending = true
            main.postDelayed({
                invalidatePending = false
                lastInvalidate = SystemClock.uptimeMillis()
                invalidate()
            }, wait)
        }
    }

    private fun signature(): String {
        val states = repo.states.value
        val sb = StringBuilder(structureKey())
            .append('#').append(repo.connection.value.javaClass.simpleName)
            .append('#').append(states.isEmpty())
            .append('#')
        for (id in watchedIds()) {
            sb.append(id).append('=').append(states[id]?.let(Entities::fingerprint) ?: "-").append(';')
        }
        return sb.toString()
    }

    // ---------- Tocchi ----------

    protected open fun onTileTapped(tile: Tile) {
        when (tile) {
            is EntityTile -> repo.states.value[tile.entityId]?.let { activate(it) }
            is GroupTile -> screenManager.push(
                EntitiesScreen(carContext, deps, tile.title, tile.entityIds, sorted = true, stripPrefix = tile.title)
            )
        }
    }

    protected fun activate(e: EntityState) {
        val command = Entities.tapCommand(e) ?: run {
            toast("${e.name}: solo lettura")
            return
        }
        if (command.confirm) {
            screenManager.push(ConfirmScreen(carContext, e.name, command.label) {
                runCommand(e, command.domain, command.service)
            })
        } else {
            runCommand(e, command.domain, command.service)
        }
    }

    protected fun runCommand(e: EntityState, domain: String, service: String) {
        repo.call(domain, service, e.entityId) { message -> toast("${e.name}: $message") }
    }

    protected fun toast(message: String) {
        main.post { CarToast.makeText(carContext, message, CarToast.LENGTH_LONG).show() }
    }

    // ---------- Costruzione dell'elenco ----------

    protected val listLimit: Int
        get() = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
                .coerceAtLeast(1)
        } catch (e: Exception) {
            6
        }

    protected fun listTemplate(
        title: String,
        rows: List<Row>,
        loading: Boolean = false,
        root: Boolean = false,
        empty: String = "Nessun elemento",
    ): Template {
        val builder = ListTemplate.Builder().setTitle(title)
        if (!root) builder.setHeaderAction(Action.BACK)
        if (loading) {
            builder.setLoading(true)
        } else {
            val list = ItemList.Builder()
            rows.take(listLimit).forEach { list.addItem(it) }
            list.setNoItemsMessage(empty)
            builder.setSingleList(list.build())
        }
        return builder.build()
    }

    /** Una riga per un'entità: toccandola si comanda (le azioni delicate chiedono conferma). */
    protected fun entityRow(e: EntityState, stripPrefix: String?): Row {
        val builder = Row.Builder()
            .setTitle(Entities.displayName(e, stripPrefix))
            .addText(Entities.describe(e))
            .setImage(RowIcons.entity(e), Row.IMAGE_TYPE_SMALL)
        if (Entities.tapCommand(e) != null) builder.setOnClickListener { activate(e) }
        return builder.build()
    }

    protected fun browsableRow(title: String, text: String?, icon: CarIcon?, onClick: () -> Unit): Row {
        val builder = Row.Builder()
            .setTitle(title)
            .setBrowsable(true)
            .setOnClickListener { onClick() }
        if (!text.isNullOrBlank()) builder.addText(text)
        if (icon != null) builder.setImage(icon, Row.IMAGE_TYPE_SMALL)
        return builder.build()
    }

    protected fun infoRow(title: String, text: String?): Row {
        val builder = Row.Builder().setTitle(title)
        if (!text.isNullOrBlank()) builder.addText(text)
        return builder.build()
    }

    protected fun actionRow(title: String, onClick: () -> Unit): Row =
        Row.Builder().setTitle(title).setOnClickListener { onClick() }.build()
}
