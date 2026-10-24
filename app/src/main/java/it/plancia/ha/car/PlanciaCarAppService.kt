package it.plancia.ha.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import it.plancia.ha.BuildConfig
import it.plancia.ha.R

class PlanciaCarAppService : CarAppService() {

    /**
     * Build debug: qualsiasi host (comodo mentre si prova).
     * Build release: solo Gearslip ufficiale, come chiede la sua documentazione.
     */
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(R.array.hosts_allowlist)
                .build()
        }

    override fun onCreateSession(): Session = PlanciaSession()
}
