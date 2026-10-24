package it.plancia.ha.car.screens

import androidx.activity.OnBackPressedCallback
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import it.plancia.ha.car.CarDeps
import it.plancia.ha.car.CarIcons

/**
 * La plancia di Home Assistant a tutto schermo: nessun pannello sopra, solo il pulsante delle plance
 * in alto a destra, che Gearslip sbiadisce quando non si tocca niente.
 */
class DashboardScreen(carContext: CarContext, private val deps: CarDeps) : Screen(carContext) {

    init {
        deps.display.onMenuRequested = { openMenu() }
        // Indietro chiude un dialogo o esce da una sottovista della plancia.
        carContext.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                deps.display.goBack()
            }
        })
    }

    private fun openMenu() {
        if (screenManager.top === this) screenManager.push(MenuScreen(carContext, deps))
    }

    override fun onGetTemplate(): Template {
        val menu = Action.Builder()
            .setTitle("Plance")
            .setIcon(CarIcons.menu)
            .setOnClickListener { openMenu() }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(menu).build())
            .build()
    }
}
