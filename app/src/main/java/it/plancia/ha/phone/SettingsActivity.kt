package it.plancia.ha.phone

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import it.plancia.ha.PlanciaApp
import it.plancia.ha.R
import it.plancia.ha.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

/** La sola schermata sul telefono: indirizzi, token e una prova di connessione. */
class SettingsActivity : Activity() {

    private val scope = MainScope()
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val app = application as PlanciaApp
        val home = findViewById<EditText>(R.id.url_home)
        val remote = findViewById<EditText>(R.id.url_remote)
        val token = findViewById<EditText>(R.id.token)
        status = findViewById(R.id.status)

        app.settings.load().let {
            home.setText(it.homeUrl)
            remote.setText(it.remoteUrl)
            token.setText(it.token)
        }

        findViewById<Button>(R.id.save).setOnClickListener { button ->
            val config = Settings.Config(
                homeUrl = Settings.normalizeUrl(home.text.toString()),
                remoteUrl = Settings.normalizeUrl(remote.text.toString()),
                token = token.text.toString().trim(),
            )
            home.setText(config.homeUrl)
            remote.setText(config.remoteUrl)
            app.settings.save(config)
            app.repo.reconnect()
            getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(button.windowToken, 0)
            test(app, config)
        }
    }

    private fun test(app: PlanciaApp, config: Settings.Config) {
        if (!config.isComplete) {
            status.text = "Servono almeno un indirizzo e il token."
            return
        }
        status.text = "Provo la connessione…"
        scope.launch {
            val lines = mutableListOf<String>()
            for ((label, url) in listOf("Casa" to config.homeUrl, "Fuori casa" to config.remoteUrl)) {
                if (url.isBlank()) continue
                val client = app.repo.newClient()
                val line = try {
                    val version = client.connect(url, config.token)
                    val count = (client.command("get_states") as? JSONArray)?.length() ?: 0
                    "✓ $label: Home Assistant $version, $count entità"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "✗ $label: ${e.message ?: "errore sconosciuto"}"
                } finally {
                    client.close()
                }
                lines += line
                status.text = lines.joinToString("\n")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
