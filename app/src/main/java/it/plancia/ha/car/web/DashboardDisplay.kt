package it.plancia.ha.car.web

import android.annotation.SuppressLint
import android.app.Presentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import it.plancia.ha.BuildConfig
import it.plancia.ha.data.Connection
import it.plancia.ha.data.HaRepository
import it.plancia.ha.data.Settings
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Mostra il frontend di Home Assistant, la plancia vera, sulla superficie che Gearslip presta all'app.
 *
 * La superficie diventa un display virtuale con sopra una Presentation che contiene una WebView:
 * è lo stesso meccanismo con cui Gearslip disegna la propria interfaccia. La pagina si autentica
 * con il token come nell'app ufficiale (window.externalApp), quindi non chiede mai il login.
 *
 * L'host non manda tocchi veri, solo tap, scorrimenti e lanci. Il tap diventa un tocco nel punto
 * esatto; lo scorrimento lo esegue la pagina in JavaScript, così trascinare il dito non sposta
 * mai per sbaglio un cursore della luminosità.
 *
 * Tutto gira sul thread principale: è lì che la libreria dell'auto chiama la SurfaceCallback.
 */
class DashboardDisplay(
    private val carContext: CarContext,
    private val repo: HaRepository,
    private val appSettings: Settings,
) : SurfaceCallback {

    private val main = Handler(Looper.getMainLooper())

    // La superficie prestata dall'host.
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceDpi = 160
    private var visibleArea: Rect? = null

    // Il display virtuale e ciò che ci gira sopra.
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var web: WebView? = null
    private var statusBox: View? = null
    private var statusTitle: TextView? = null
    private var statusDetail: TextView? = null
    private var builtKey = ""
    private var tearingDown = false
    private var released = false

    // La pagina caricata.
    private var base: String? = null
    private var pageError: String? = null
    private var frontendReady = false
    private var generation = 0
    private var commandId = 1_000
    private var pendingNavigate = 0

    // Lo scorrimento arriva a ogni movimento del dito: si raccoglie e si passa alla pagina a ogni fotogramma.
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var scrollScheduled = false
    private val scrollTask = Runnable { flushScroll() }

    /** La plancia da mostrare: url_path, oppure null per quella che sceglie Home Assistant. */
    var current: String? = appSettings.defaultDashboard
        private set

    /** Il pulsante ☰ di Home Assistant è stato toccato. */
    var onMenuRequested: (() -> Unit)? = null

    /** Se restituisce true il tap è già gestito (per esempio chiude il menu) e non arriva alla pagina. */
    var tapInterceptor: ((Float, Float) -> Boolean)? = null

    /** Apre una plancia (null = quella che sceglie Home Assistant). */
    fun open(urlPath: String?) {
        current = urlPath
        showTarget(reload = false)
    }

    /** Ricarica la pagina da capo. */
    fun reload() {
        showTarget(reload = true)
    }

    /** Torna indietro nella pagina: chiude un dialogo o esce da una sottovista. */
    fun goBack(): Boolean {
        val w = web ?: return false
        if (!w.canGoBack()) return false
        w.goBack()
        return true
    }

    /** Cambia l'ingrandimento: la pagina si ridisegna con la nuova densità. */
    fun setZoom(percent: Int) {
        appSettings.zoom = percent
        rebuild()
    }

    /** Giorno e notte: il tema della pagina segue quello dell'auto. */
    fun onCarConfigurationChanged() {
        if (presentation != null && buildKey() != builtKey) rebuild()
    }

    /** Home Assistant ha cambiato stato o indirizzo (casa o remoto). */
    fun onConnectionChanged() {
        val connected = (repo.connection.value as? Connection.Connected)?.url
        if (web != null && connected != null && (connected != base || pageError != null)) {
            hardLoad(connected)
        }
        updateStatus()
    }

    fun release() {
        released = true
        main.removeCallbacksAndMessages(null)
        teardown()
        surface = null
    }

    // ---------- SurfaceCallback ----------

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val s = surfaceContainer.surface ?: return
        surface = s
        surfaceWidth = surfaceContainer.width
        surfaceHeight = surfaceContainer.height
        if (surfaceContainer.dpi > 0) surfaceDpi = surfaceContainer.dpi
        val vd = virtualDisplay
        if (vd != null && buildKey() == builtKey) {
            // Stessa misura: la pagina resta com'è, cambia solo dove viene disegnata.
            vd.surface = s
            // Una plancia ferma non ridisegnerebbe niente: la nuova superficie resterebbe nera.
            presentation?.window?.decorView?.invalidate()
            web?.invalidate()
        } else {
            rebuild()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        surface = null
        virtualDisplay?.surface = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = Rect(visibleArea)
    }

    override fun onStableAreaChanged(stableArea: Rect) = Unit

    override fun onClick(x: Float, y: Float) {
        if (tapInterceptor?.invoke(x, y) == true) return
        if (statusBox?.visibility == View.VISIBLE) {
            // Sul messaggio d'errore un tocco riprova.
            if (pageError != null) reload()
            return
        }
        tap(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        pendingDx += distanceX
        pendingDy += distanceY
        if (!scrollScheduled) {
            scrollScheduled = true
            main.postDelayed(scrollTask, FRAME_MS)
        }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        flushScroll()
        val dpr = dpr()
        // La velocità è quella del dito: la pagina va nel verso opposto, come trascinandola.
        scrollPage(-velocityX * FLING_SECONDS / dpr, -velocityY * FLING_SECONDS / dpr, smooth = true)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        // L'ingrandimento si sceglie dal menu: un pizzico per sbaglio non deve ridisegnare la plancia.
    }

    // ---------- Costruzione ----------

    private fun displayDpi(): Int = (surfaceDpi * appSettings.zoom / 100f).roundToInt().coerceIn(MIN_DPI, MAX_DPI)

    private fun dpr(): Float = displayDpi() / 160f

    /** Gearslip copre la parte alta della superficie con la sua barra (nome dell'app e Stop). */
    private fun topInset(): Int = (GEARSLIP_BAR_DP * surfaceDpi / 160f).roundToInt().coerceAtMost(surfaceHeight / 3)

    private fun buildKey(): String = "${surfaceWidth}x$surfaceHeight@${displayDpi()}/${carContext.isDarkMode}"

    private fun rebuild() {
        if (released) return
        teardown()
        val s = surface ?: return
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || !s.isValid) return
        build(s)
    }

    private fun build(s: Surface) {
        val dark = carContext.isDarkMode
        val colors = if (dark) DARK else LIGHT
        val manager = carContext.getSystemService(DisplayManager::class.java) ?: return
        val vd = try {
            manager.createVirtualDisplay(
                "plancia", surfaceWidth, surfaceHeight, displayDpi(), s,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile creare il display virtuale", e)
            null
        } ?: return
        virtualDisplay = vd

        try {
            val theme = if (dark) {
                android.R.style.Theme_Material_NoActionBar_Fullscreen
            } else {
                android.R.style.Theme_Material_Light_NoActionBar_Fullscreen
            }
            val p = Presentation(carContext, vd.display, theme)
            presentation = p
            p.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            p.window?.setBackgroundDrawable(ColorDrawable(colors.background))
            p.setOnDismissListener {
                // Una Presentation si chiude da sola se il display cambia: la si ricostruisce.
                if (presentation === p && !tearingDown && !released) main.post { rebuild() }
            }

            val context = p.context
            val root = FrameLayout(context)
            root.setBackgroundColor(colors.background)
            val area = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            area.topMargin = topInset()

            val webView = createWebView(p, colors)
            web = webView
            root.addView(webView, area)

            val title = TextView(context).apply {
                textSize = 22f
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                setTextColor(colors.text)
                gravity = Gravity.CENTER
            }
            val detail = TextView(context).apply {
                textSize = 15f
                setTextColor(colors.muted)
                gravity = Gravity.CENTER
                setPadding(0, (8 * dpr()).roundToInt(), 0, 0)
            }
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(colors.background)
                val pad = (32 * dpr()).roundToInt()
                setPadding(pad, pad, pad, pad)
                addView(title)
                addView(detail)
            }
            root.addView(box, FrameLayout.LayoutParams(area))
            statusBox = box
            statusTitle = title
            statusDetail = detail

            p.setContentView(root)
            p.show()
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile mostrare la plancia sullo schermo dell'auto", e)
            teardown()
            return
        }

        builtKey = buildKey()
        Log.i(TAG, "Plancia su ${surfaceWidth}x$surfaceHeight a ${displayDpi()} dpi")
        showTarget(reload = true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(p: Presentation, colors: Colors): WebView {
        val webView = WebView(p.context)
        webView.setBackgroundColor(colors.background)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        webView.addJavascriptInterface(ExternalAppBridge(), "externalApp")
        webView.addJavascriptInterface(PlanciaBridge(), "plancia")

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // Su ogni origine: si naviga solo su Home Assistant, anche dopo un rimando da http a https.
            WebViewCompat.addDocumentStartJavaScript(webView, PageScripts.SETUP, setOf("*"))
        }
        return webView
    }

    private fun teardown() {
        tearingDown = true
        main.removeCallbacks(scrollTask)
        scrollScheduled = false
        pendingDx = 0f
        pendingDy = 0f
        web?.let { w ->
            (w.parent as? ViewGroup)?.removeView(w)
            w.stopLoading()
            w.destroy()
        }
        web = null
        presentation?.let { p -> runCatching { p.dismiss() } }
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
        statusBox = null
        statusTitle = null
        statusDetail = null
        builtKey = ""
        base = null
        frontendReady = false
        pendingNavigate = 0
        tearingDown = false
    }

    // ---------- Pagina ----------

    /** Il percorso della plancia da aprire; "/" lascia scegliere a Home Assistant. */
    private fun targetPath(): String = "/" + (current ?: repo.haDefault.value ?: "")

    private fun showTarget(reload: Boolean) {
        val connected = (repo.connection.value as? Connection.Connected)?.url
        if (web == null || connected == null) {
            updateStatus()
            return
        }
        if (!reload && connected == base && pageError == null && navigateInPage(targetPath())) return
        hardLoad(connected)
    }

    private fun hardLoad(baseUrl: String) {
        val w = web ?: return
        generation++
        base = baseUrl
        pageError = null
        frontendReady = false
        pendingNavigate = 0
        w.loadUrl(baseUrl.trimEnd('/') + targetPath())
        updateStatus()
    }

    /** Cambia plancia dentro la pagina già aperta, senza ricaricarla: è il comando dell'app ufficiale. */
    private fun navigateInPage(path: String): Boolean {
        if (!frontendReady || path == "/") return false
        val id = ++commandId
        pendingNavigate = id
        val message = JSONObject()
            .put("type", "command")
            .put("command", "navigate")
            .put("id", id)
            .put("payload", JSONObject().put("path", path).put("options", JSONObject().put("replace", true)))
        runInPage("window.externalBus && window.externalBus($message);")
        main.postDelayed({
            // Nessuna risposta: si apre la plancia ricaricando la pagina.
            if (pendingNavigate == id) {
                pendingNavigate = 0
                base?.let { hardLoad(it) }
            }
        }, NAVIGATE_TIMEOUT_MS)
        return true
    }

    private fun runInPage(script: String) {
        web?.evaluateJavascript(script, null)
    }

    private fun updateStatus() {
        val box = statusBox ?: return
        val conn = repo.connection.value
        val message: Pair<String, String?>? = when {
            pageError != null -> "Non riesco ad aprire la plancia" to "$pageError\nTocca qui per riprovare."
            base != null -> null
            conn is Connection.NotConfigured -> "Plancia non è ancora configurata" to
                "Apri Plancia sul telefono e inserisci l’indirizzo di Home Assistant e un token di accesso."
            conn is Connection.Failed -> "Home Assistant non risponde" to conn.message
            conn is Connection.Connecting -> "Mi collego a Home Assistant" to conn.url.ifBlank { null }
            else -> "Mi collego a Home Assistant" to null
        }
        if (message == null) {
            box.visibility = View.GONE
            return
        }
        statusTitle?.text = message.first
        statusDetail?.text = message.second.orEmpty()
        statusDetail?.visibility = if (message.second.isNullOrBlank()) View.GONE else View.VISIBLE
        box.visibility = View.VISIBLE
    }

    private fun isHomeAssistantPage(url: String?): Boolean = sameHost(url, base)

    // ---------- Gesti ----------

    private fun tap(x: Float, y: Float) {
        val target = presentation?.window?.decorView ?: return
        val down = SystemClock.uptimeMillis()
        touch(target, MotionEvent.ACTION_DOWN, down, down, x, y)
        main.postDelayed({ touch(target, MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), x, y) }, TAP_MS)
    }

    private fun touch(target: View, action: Int, downTime: Long, eventTime: Long, px: Float, py: Float) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = px
            y = py
            pressure = 1f
            size = 1f
        })
        val event = MotionEvent.obtain(
            downTime, eventTime, action, 1, properties, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        try {
            target.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun flushScroll() {
        scrollScheduled = false
        main.removeCallbacks(scrollTask)
        val dx = pendingDx
        val dy = pendingDy
        pendingDx = 0f
        pendingDy = 0f
        if (dx == 0f && dy == 0f) return
        val dpr = dpr()
        scrollPage(dx / dpr, dy / dpr, smooth = false)
    }

    /** [dx] e [dy] in pixel CSS; si scorre l'elemento sotto il centro della parte visibile. */
    private fun scrollPage(dx: Float, dy: Float, smooth: Boolean) {
        if (web == null || base == null) return
        val dpr = dpr()
        val top = topInset()
        val area = Rect(0, top, surfaceWidth, surfaceHeight)
        val seen = visibleArea
        if (seen != null && !seen.isEmpty) area.intersect(seen)
        val x = area.exactCenterX() / dpr
        val y = (area.exactCenterY() - top) / dpr
        runInPage("window.__planciaScroll && window.__planciaScroll($dx, $dy, $x, $y, $smooth);")
    }

    // ---------- Ponte con il frontend ----------

    /** Quello che il frontend di Home Assistant si aspetta dall'app ufficiale. Arriva su un thread della WebView. */
    private inner class ExternalAppBridge {
        @JavascriptInterface
        fun getExternalAuth(payload: String) {
            val callback = callbackName(payload, AUTH_CALLBACK)
            main.post { answerAuth(callback) }
        }

        @JavascriptInterface
        fun revokeExternalAuth(payload: String) {
            // Il token a lungo termine non si revoca da qui: si conferma e basta.
            val callback = callbackName(payload, REVOKE_CALLBACK)
            main.post { if (isHomeAssistantPage(web?.url)) runInPage("window.$callback && window.$callback(true);") }
        }

        @JavascriptInterface
        fun externalBus(message: String) {
            main.post { onBusMessage(message) }
        }
    }

    private inner class PlanciaBridge {
        @JavascriptInterface
        fun openMenu() {
            main.post { if (isHomeAssistantPage(web?.url)) onMenuRequested?.invoke() }
        }
    }

    private fun answerAuth(callback: String) {
        val w = web ?: return
        // Il token va solo alla pagina di Home Assistant, mai a un altro sito.
        if (!isHomeAssistantPage(w.url)) {
            Log.w(TAG, "Token negato a ${w.url}")
            return
        }
        val token = appSettings.load().token
        val data = JSONObject().put("access_token", token).put("expires_in", TOKEN_SECONDS)
        w.evaluateJavascript("window.$callback && window.$callback(true, $data);", null)
    }

    private fun onBusMessage(raw: String) {
        if (!isHomeAssistantPage(web?.url)) return
        val message = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            return
        }
        when (message.optString("type")) {
            "config/get" -> {
                val answer = JSONObject()
                    .put("id", message.optInt("id"))
                    .put("type", "result")
                    .put("success", true)
                    .put("result", externalConfig())
                runInPage("window.externalBus && window.externalBus($answer);")
            }
            "frontend/loaded" -> frontendReady = true
            "sidebar/show" -> onMenuRequested?.invoke()
            "result" -> if (pendingNavigate != 0 && message.optInt("id") == pendingNavigate) {
                pendingNavigate = 0
                if (!message.optBoolean("success")) base?.let { hardLoad(it) }
            }
        }
    }

    /** Cosa sa fare "l'app": niente di ciò che serve a un telefono, la barra laterale la gestisce Plancia. */
    private fun externalConfig(): JSONObject = JSONObject()
        .put("hasSettingsScreen", false)
        .put("hasSidebar", false)
        .put("canWriteTag", false)
        .put("hasExoPlayer", false)
        .put("canCommissionMatter", false)
        .put("canImportThreadCredentials", false)
        .put("hasAssist", false)
        .put("hasBarCodeScanner", 0)
        .put("appVersion", "Plancia ${BuildConfig.VERSION_NAME}")

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Si resta su Home Assistant: altri siti non si aprono, e non vedono il token.
            if (!request.isForMainFrame) return false
            return !sameHost(request.url.toString(), base)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (view !== web) return
            pageError = null
            frontendReady = false
            updateStatus()
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (view !== web) return
            view.evaluateJavascript(PageScripts.SETUP, null)
            view.evaluateJavascript(PageScripts.HIDE_SIDEBAR, null)
            // Le versioni di Home Assistant che non avvisano quando sono pronte lo sono comunque dopo un po'.
            val loaded = generation
            main.postDelayed({ if (generation == loaded && pageError == null) frontendReady = true }, READY_FALLBACK_MS)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (view !== web || !request.isForMainFrame) return
            pageError = error.description?.toString()?.ifBlank { null } ?: "Errore ${error.errorCode}"
            updateStatus()
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (view !== web || !request.isForMainFrame || errorResponse.statusCode < 500) return
            pageError = "Home Assistant risponde con l’errore ${errorResponse.statusCode}"
            updateStatus()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "La pagina si è chiusa (crash: ${detail.didCrash()}), la riapro")
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            if (view === web) {
                web = null
                main.post { rebuild() }
            }
            return true
        }
    }

    private class Chrome : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                Log.w(TAG, "Pagina: ${consoleMessage.message()}")
            }
            return true
        }
    }

    private class Colors(val background: Int, val text: Int, val muted: Int)

    companion object {
        private const val TAG = "Plancia"

        private const val AUTH_CALLBACK = "externalAuthSetToken"
        private const val REVOKE_CALLBACK = "externalAuthRevokeToken"
        private const val TOKEN_SECONDS = 1800

        /** Altezza della barra che Gearslip disegna sopra le app (CarAppStage.CHROME_BAR). */
        private const val GEARSLIP_BAR_DP = 44f

        private const val MIN_DPI = 80
        private const val MAX_DPI = 640
        private const val FRAME_MS = 16L
        private const val TAP_MS = 40L
        private const val FLING_SECONDS = 0.35f
        private const val NAVIGATE_TIMEOUT_MS = 2_000L
        private const val READY_FALLBACK_MS = 4_000L

        private val DARK = Colors(0xFF0E1420.toInt(), 0xFFEEF2F7.toInt(), 0xFF8592A6.toInt())
        private val LIGHT = Colors(0xFFEEF2F7.toInt(), 0xFF131B2A.toInt(), 0xFF5F6B7E.toInt())

        /** Solo i due nomi che usa il frontend: un altro nome non deve poter diventare codice da eseguire. */
        private fun callbackName(payload: String, fallback: String): String {
            val name = try {
                JSONObject(payload).optString("callback")
            } catch (e: JSONException) {
                ""
            }
            return if (name == AUTH_CALLBACK || name == REVOKE_CALLBACK) name else fallback
        }

        /** Stesso server di Home Assistant, anche se un rimando passa da http a https. */
        private fun sameHost(a: String?, b: String?): Boolean {
            if (a == null || b == null) return false
            val host = Uri.parse(a).host ?: return false
            return host.equals(Uri.parse(b).host, ignoreCase = true)
        }
    }
}
