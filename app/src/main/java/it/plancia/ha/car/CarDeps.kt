package it.plancia.ha.car

import it.plancia.ha.car.web.DashboardDisplay
import it.plancia.ha.data.HaRepository
import it.plancia.ha.data.Settings

/** Ciò che ogni schermata in auto condivide durante una sessione. */
class CarDeps(val repo: HaRepository, val settings: Settings, val display: DashboardDisplay)
