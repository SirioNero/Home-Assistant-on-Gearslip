package it.plancia.ha

import android.app.Application
import it.plancia.ha.data.HaRepository
import it.plancia.ha.data.Settings

class PlanciaApp : Application() {
    val settings: Settings by lazy { Settings(this) }
    val repo: HaRepository by lazy { HaRepository(settings) }
}
