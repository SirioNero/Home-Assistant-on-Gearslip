package it.plancia.ha.car

import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import it.plancia.ha.PlanciaApp
import it.plancia.ha.car.screens.DashboardScreen
import it.plancia.ha.car.web.DashboardDisplay
import kotlinx.coroutines.launch

class PlanciaSession : Session() {

    private var display: DashboardDisplay? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val app = carContext.applicationContext as PlanciaApp
        val repo = app.repo
        val dashboard = DashboardDisplay(carContext, repo, app.settings)
        display = dashboard
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(dashboard)
        } catch (e: Exception) {
            Log.w("Plancia", "L'host non concede la superficie", e)
        }
        repo.acquire()
        lifecycleScope.launch {
            // Connesso, disconnesso, passato dall'indirizzo di casa a quello remoto: la pagina segue.
            repo.connection.collect { dashboard.onConnectionChanged() }
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                repo.release()
                dashboard.release()
            }
        })
        return DashboardScreen(carContext, CarDeps(repo, app.settings, dashboard))
    }

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        display?.onCarConfigurationChanged()
    }
}
