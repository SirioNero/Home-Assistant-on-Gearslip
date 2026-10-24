package it.plancia.ha.car.render

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import it.plancia.ha.data.Connection
import it.plancia.ha.data.EntityState
import it.plancia.ha.data.HaRepository
import it.plancia.ha.logic.Entities
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Disegna la casa sulla superficie che Gearslip presta ai template con mappa.
 * Lavora su un thread dedicato e ridisegna solo quando qualcosa cambia (più l'orologio, una volta al minuto).
 */
class SurfaceRenderer(
    private val carContext: CarContext,
    private val repo: HaRepository,
) : SurfaceCallback {

    private val thread = HandlerThread("plancia-render").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

    @Volatile private var surface: Surface? = null
    @Volatile private var dpi = 160
    @Volatile private var visibleArea: Rect? = null
    @Volatile private var stableArea: Rect? = null
    @Volatile private var scene: Scene = Scene.EMPTY
    @Volatile private var onTap: ((Tile) -> Unit)? = null
    @Volatile private var scrollY = 0f
    @Volatile private var maxScroll = 0f
    @Volatile private var targets: List<Pair<RectF, Tile>> = emptyList()
    @Volatile private var gridRect: RectF? = null
    @Volatile private var pressedKey: String? = null
    @Volatile private var flingVelocity = 0f
    @Volatile private var lastFlingStep = 0L
    @Volatile private var released = false

    // Usati solo dal thread di disegno.
    private var painter: TilePainter? = null
    private var painterKey = ""
    private var lastDraw = 0L

    private val drawTask = Runnable { drawThrottled() }
    private val flingTask = Runnable { stepFling() }
    private val clockTask = object : Runnable {
        override fun run() {
            drawNow()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 100L)
        }
    }

    init {
        handler.post(clockTask)
    }

    /** Mostra la scena di uno schermo. Lo scorrimento riparte da capo solo se cambia schermo. */
    fun show(newScene: Scene, tap: (Tile) -> Unit) {
        if (newScene.id != scene.id) {
            scrollY = 0f
            flingVelocity = 0f
        }
        scene = newScene
        onTap = tap
        requestDraw()
    }

    fun requestDraw() {
        if (released) return
        handler.removeCallbacks(drawTask)
        handler.post(drawTask)
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        synchronized(lock) { surface = null }
        thread.quitSafely()
    }

    // ---------- SurfaceCallback ----------

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        synchronized(lock) {
            surface = surfaceContainer.surface
            if (surfaceContainer.dpi > 0) dpi = surfaceContainer.dpi
        }
        requestDraw()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        synchronized(lock) { surface = null }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = Rect(visibleArea)
        requestDraw()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        this.stableArea = Rect(stableArea)
        requestDraw()
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        flingVelocity = 0f
        scrollY = (scrollY + distanceY).coerceIn(0f, maxScroll)
        requestDraw()
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        flingVelocity = -velocityY
        lastFlingStep = SystemClock.uptimeMillis()
        handler.removeCallbacks(flingTask)
        handler.post(flingTask)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        // Nessuno zoom: le tessere hanno già la dimensione giusta per lo schermo.
    }

    override fun onClick(x: Float, y: Float) {
        val grid = gridRect ?: return
        if (!grid.contains(x, y)) return
        val tile = targets.firstOrNull { it.first.contains(x, y) }?.second ?: return
        pressedKey = tile.key
        requestDraw()
        handler.postDelayed({
            pressedKey = null
            requestDraw()
        }, 220)
        val tap = onTap
        main.post { tap?.invoke(tile) }
    }

    // ---------- Disegno ----------

    private fun drawThrottled() {
        val wait = 33L - (SystemClock.uptimeMillis() - lastDraw)
        if (wait > 0) {
            handler.postDelayed(drawTask, wait)
            return
        }
        drawNow()
    }

    private fun stepFling() {
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFlingStep).coerceIn(1L, 50L) / 1000f
        lastFlingStep = now
        val velocity = flingVelocity
        if (abs(velocity) < 40f || maxScroll <= 0f) {
            flingVelocity = 0f
            return
        }
        val next = (scrollY + velocity * dt).coerceIn(0f, maxScroll)
        scrollY = next
        flingVelocity = if (next <= 0f || next >= maxScroll) 0f else velocity * (1f - 4f * dt).coerceAtLeast(0f)
        drawNow()
        if (flingVelocity != 0f) handler.postDelayed(flingTask, 16)
    }

    private fun drawNow() {
        lastDraw = SystemClock.uptimeMillis()
        synchronized(lock) {
            val s = surface ?: return
            if (!s.isValid) return
            val canvas: Canvas = try {
                s.lockCanvas(null)
            } catch (e: Exception) {
                Log.w(TAG, "Superficie non disponibile", e)
                return
            }
            try {
                render(canvas)
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel disegno", e)
            } finally {
                try {
                    s.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    Log.w(TAG, "Impossibile pubblicare il fotogramma", e)
                }
            }
        }
    }

    private fun render(c: Canvas) {
        val w = c.width
        val h = c.height
        val dark = carContext.isDarkMode
        val dp = dpi / 160f
        val key = "$dark|$dp"
        val p = painter.takeIf { painterKey == key } ?: TilePainter(Palette(dark), dp).also {
            painter = it
            painterKey = key
        }

        val states = repo.states.value
        val conn = repo.connection.value
        val current = scene
        val area = RectF(pickArea(w, h))
        val members = current.entityIds().mapNotNull { states[it] }
        val lightsOn = members.filter { it.domain == "light" && it.state == "on" }
        p.background(c, w, h, if (lightsOn.isEmpty()) null else Colors.mix(lightsOn.map(Colors::lightColor)), area)

        val pad = 18f * dp
        val inner = RectF(area.left + pad, area.top + pad * 0.8f, area.right - pad, area.bottom - pad * 0.6f)
        p.header(c, inner, current.title, subtitle(conn, states, members), LocalTime.now().format(clockFormat), statusColor(conn, p.palette))

        val grid = RectF(inner.left, inner.top + 60f * dp, inner.right, inner.bottom)
        gridRect = grid

        val notice = notice(conn, states)
        if (notice != null) {
            targets = emptyList()
            p.message(c, grid, notice.first, notice.second)
            return
        }

        val tiles = current.tiles.filter { it !is EntityTile || states.containsKey(it.entityId) }
        if (tiles.isEmpty()) {
            targets = emptyList()
            p.message(c, grid, current.emptyMessage, null)
            return
        }

        val gap = 12f * dp
        val columns = max(1, ((grid.width() + gap) / (150f * dp + gap)).toInt())
        val tileWidth = (grid.width() - gap * (columns - 1)) / columns
        val tileHeight = (tileWidth * 0.74f).coerceIn(116f * dp, 156f * dp)
        val rows = ceil(tiles.size / columns.toFloat()).toInt()
        val contentHeight = rows * tileHeight + (rows - 1) * gap
        maxScroll = max(0f, contentHeight - grid.height())
        val scroll = scrollY.coerceIn(0f, maxScroll)
        scrollY = scroll

        val hits = ArrayList<Pair<RectF, Tile>>(tiles.size)
        c.save()
        c.clipRect(area.left, grid.top - 2f * dp, area.right, area.bottom)
        tiles.forEachIndexed { i, tile ->
            val left = grid.left + (i % columns) * (tileWidth + gap)
            val top = grid.top + (i / columns) * (tileHeight + gap) - scroll
            val rect = RectF(left, top, left + tileWidth, top + tileHeight)
            if (rect.bottom < grid.top || rect.top > area.bottom) return@forEachIndexed
            val pressed = tile.key == pressedKey
            when (tile) {
                is EntityTile -> {
                    val e = states[tile.entityId] ?: return@forEachIndexed
                    p.entity(c, rect, e, Entities.displayName(e, current.stripPrefix), pressed)
                }
                is GroupTile -> p.group(c, rect, tile.title, tile.entityIds.mapNotNull { states[it] }, pressed)
            }
            hits += rect to tile
        }
        c.restore()
        p.scrollbar(c, inner.right + pad * 0.5f, grid.top, grid.height(), contentHeight, scroll)
        targets = hits
    }

    private fun pickArea(w: Int, h: Int): Rect {
        visibleArea?.let { if (!it.isEmpty) return it }
        stableArea?.let { if (!it.isEmpty) return it }
        return Rect(0, 0, w, h)
    }

    private fun subtitle(conn: Connection, states: Map<String, EntityState>, members: List<EntityState>): String =
        when (conn) {
            is Connection.Connected -> Entities.summarize(members)
            is Connection.Connecting -> if (states.isEmpty()) "Connessione in corso" else "Riconnessione in corso"
            is Connection.Failed -> if (states.isEmpty()) "Non connesso" else "Non connesso, dati dell’ultima lettura"
            is Connection.NotConfigured -> "Da configurare sul telefono"
        }

    private fun notice(conn: Connection, states: Map<String, EntityState>): Pair<String, String?>? = when {
        conn is Connection.NotConfigured -> "Plancia non è ancora configurata" to
            "Apri Plancia sul telefono e inserisci l’indirizzo di Home Assistant e un token di accesso."
        states.isNotEmpty() -> null
        conn is Connection.Failed -> "Home Assistant non risponde" to conn.message
        conn is Connection.Connecting -> "Mi collego a Home Assistant" to conn.url.ifBlank { null }
        else -> "Mi collego a Home Assistant" to null
    }

    private fun statusColor(conn: Connection, palette: Palette): Int = when (conn) {
        is Connection.Connected -> palette.ok
        is Connection.Connecting -> palette.warn
        else -> palette.bad
    }

    companion object {
        private const val TAG = "Plancia"
    }
}
