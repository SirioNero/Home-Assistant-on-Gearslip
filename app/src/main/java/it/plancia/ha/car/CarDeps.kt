package it.plancia.ha.car

import it.plancia.ha.car.render.SurfaceRenderer
import it.plancia.ha.data.HaRepository

/** Ciò che ogni schermata in auto condivide durante una sessione. */
class CarDeps(val repo: HaRepository, val renderer: SurfaceRenderer)
