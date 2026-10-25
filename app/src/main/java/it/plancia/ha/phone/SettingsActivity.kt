package it.plancia.ha.phone

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import it.plancia.ha.PlanciaApp
import it.plancia.ha.R
import it.plancia.ha.data.HaException
import it.plancia.ha.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

/** La sola schermata sul telefono: lingua, indirizzi, token e una prova di connessione. */
class SettingsActivity : Activity() {

    private val scope = MainScope()
    private lateinit var status: TextView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Languages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val app = application as PlanciaApp
        val home = findViewById<EditText>(R.id.url_home)
        val remote = findViewById<EditText>(R.id.url_remote)
        val token = findViewById<EditText>(R.id.token)
        status = findViewById(R.id.status)
        setUpLanguage(findViewById(R.id.language))

        // Dopo un cambio di lingua l'Activity riparte: i campi tengono ciò che era già scritto.
        if (savedInstanceState == null) {
            app.settings.load().let {
                home.setText(it.homeUrl)
                remote.setText(it.remoteUrl)
                token.setText(it.token)
            }
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

    private fun setUpLanguage(spinner: Spinner) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, Languages.ALL.map { it.second })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val current = Languages.current(this)
        spinner.setSelection(Languages.ALL.indexOfFirst { it.first == current }.coerceAtLeast(0), false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val tag = Languages.ALL[position].first
                if (tag == Languages.current(this@SettingsActivity)) return
                Languages.save(this@SettingsActivity, tag)
                recreate()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun test(app: PlanciaApp, config: Settings.Config) {
        if (!config.isComplete) {
            status.text = getString(R.string.status_incomplete)
            return
        }
        status.text = getString(R.string.status_testing)
        scope.launch {
            val lines = mutableListOf<String>()
            val places = listOf(
                getString(R.string.place_home) to config.homeUrl,
                getString(R.string.place_remote) to config.remoteUrl,
            )
            for ((label, url) in places) {
                if (url.isBlank()) continue
                val client = app.repo.newClient()
                val line = try {
                    val version = client.connect(url, config.token)
                    val count = (client.command("get_states") as? JSONArray)?.length() ?: 0
                    resources.getQuantityString(R.plurals.status_ok, count, label, version, count)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    getString(R.string.status_failed, label, describe(e))
                } finally {
                    client.close()
                }
                lines += line
                status.text = lines.joinToString("\n")
            }
        }
    }

    /** L'errore nella lingua scelta; i messaggi che arrivano da Home Assistant restano come sono. */
    private fun describe(e: Exception): String {
        val unknown = getString(R.string.error_unknown)
        if (e !is HaException) return e.message ?: unknown
        val detail = e.detail.orEmpty()
        return when (e.reason) {
            HaException.Reason.BAD_ADDRESS -> getString(R.string.error_bad_address, detail)
            HaException.Reason.NO_RESPONSE ->
                if (detail.isNotEmpty()) getString(R.string.error_no_answer_from, detail) else getString(R.string.error_not_responding)
            HaException.Reason.INVALID_TOKEN -> getString(R.string.error_invalid_token)
            HaException.Reason.ACCESS_DENIED -> getString(R.string.error_access_denied)
            HaException.Reason.HTTP_ERROR -> getString(R.string.error_http, detail)
            HaException.Reason.NETWORK -> if (detail.isNotEmpty()) getString(R.string.error_network, detail) else unknown
            HaException.Reason.CLOSED -> getString(R.string.error_closed)
            HaException.Reason.OTHER -> e.message ?: unknown
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
