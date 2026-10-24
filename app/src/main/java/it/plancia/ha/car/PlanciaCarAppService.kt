package it.plancia.ha.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
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

    // Le versioni recenti della libreria chiamano questa.
    override fun onCreateSession(sessionInfo: SessionInfo): Session = PlanciaSession()

    // Quella storica, ancora presente: implementata anche lei, così compila con qualsiasi versione 1.x.
    @Deprecated("Sostituita da onCreateSession(SessionInfo)")
    override fun onCreateSession(): Session = PlanciaSession()
}
