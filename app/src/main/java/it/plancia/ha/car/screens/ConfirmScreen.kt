package it.plancia.ha.car.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/** Per script, pulsanti e aspirapolvere: un tocco per sbaglio alla guida non deve far partire niente. */
class ConfirmScreen(
    carContext: CarContext,
    private val name: String,
    private val actionLabel: String,
    private val onConfirm: () -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template =
        MessageTemplate.Builder("$actionLabel: $name?")
            .setTitle("Conferma")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(actionLabel)
                    .setFlags(Action.FLAG_PRIMARY)
                    .setOnClickListener {
                        onConfirm()
                        screenManager.pop()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Annulla")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
}
