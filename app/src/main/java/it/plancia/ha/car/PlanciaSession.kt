package it.plancia.ha.car

import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import it.plancia.ha.PlanciaApp
import it.plancia.ha.car.render.SurfaceRenderer
import it.plancia.ha.car.screens.HomeScreen

class PlanciaSession : Session() {

    private var renderer: SurfaceRenderer? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val repo = (carContext.applicationContext as PlanciaApp).repo
        val surfaceRenderer = SurfaceRenderer(carContext, repo)
        renderer = surfaceRenderer
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceRenderer)
        } catch (e: Exception) {
            // Senza superficie resta comunque l'elenco a sinistra.
            Log.w("Plancia", "L'host non concede la superficie", e)
        }
        repo.acquire()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                repo.release()
                surfaceRenderer.release()
            }
        })
        return HomeScreen(carContext, CarDeps(repo, surfaceRenderer))
    }

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        // Giorno/notte: la superficie cambia palette al prossimo fotogramma.
        renderer?.requestDraw()
    }
}
