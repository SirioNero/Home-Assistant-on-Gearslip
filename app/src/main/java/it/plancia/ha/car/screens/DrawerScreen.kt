package it.plancia.ha.car.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import it.plancia.ha.car.CarDeps

/**
 * Un pannello a sinistra sopra la plancia, come un cassetto: la plancia resta visibile a destra
 * e un tocco su di essa chiude il pannello.
 */
abstract class DrawerScreen(carContext: CarContext, protected val deps: CarDeps) : Screen(carContext) {

    private val closeOnTap: (Float, Float) -> Boolean = { _, _ ->
        screenManager.popToRoot()
        true
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                deps.display.tapInterceptor = closeOnTap
            }

            override fun onStop(owner: LifecycleOwner) {
                if (deps.display.tapInterceptor === closeOnTap) deps.display.tapInterceptor = null
            }
        })
    }

    /** L'elenco del pannello. */
    protected abstract fun content(): Template

    final override fun onGetTemplate(): Template =
        MapWithContentTemplate.Builder().setContentTemplate(content()).build()

    protected fun listTemplate(title: String, list: ItemList?, loading: Boolean = false): Template {
        val builder = ListTemplate.Builder().setTitle(title).setHeaderAction(Action.BACK)
        if (loading || list == null) builder.setLoading(true) else builder.setSingleList(list)
        return builder.build()
    }

    protected fun row(title: String, text: String?, onClick: (() -> Unit)? = null): Row {
        val builder = Row.Builder().setTitle(title)
        if (!text.isNullOrBlank()) builder.addText(text)
        if (onClick != null) builder.setOnClickListener { onClick() }
        return builder.build()
    }
}
