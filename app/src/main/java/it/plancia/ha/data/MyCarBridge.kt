package it.plancia.ha.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.system.Os
import android.system.OsConstants
import android.util.Log
import it.plancia.ha.BuildConfig
import it.plancia.ha.PlanciaApp
import it.plancia.ha.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Il ponte per myCar, che ha Plancia dentro di sé. Per collegarsi all'auto senza fili, myCar va
 * lasciata fuori da Tailscale (dentro una VPN Android non le dà il Wi-Fi dell'auto), e allora non
 * raggiunge più Home Assistant su un indirizzo Tailscale; Plancia, dentro Tailscale, sì. Così myCar
 * si lega a questo servizio durante il viaggio e gli chiede le sue connessioni: Plancia apre quella
 * verso Home Assistant e la passa a myCar come socket locale, copiando i dati nei due versi.
 * Il legame tiene sveglia Plancia quanto myCar, che in auto è in primo piano.
 *
 * Solo a myCar firmata con un certificato della lista degli host ammessi, come [AccountProvider],
 * e solo verso gli indirizzi di Home Assistant scritti qui: non è una porta verso il resto della
 * rete. La build di prova di Plancia accetta anche la build di prova di myCar, che ha un'altra
 * firma: serve alle prove in CI.
 *
 * myCar fa: bindService(Intent("io.github.sirionero.mycar.BRIDGE").setPackage("it.plancia.ha"), ...),
 * poi transact(OPEN) con l'interfaccia [DESCRIPTOR], host e porta; risposta 1 e il socket, o 0 e il motivo.
 */
class MyCarBridge : Service() {

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != OPEN) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            val host = data.readString().orEmpty()
            val port = data.readInt()
            val result = runCatching { open(Binder.getCallingUid(), host, port) }
            if (reply == null) {
                result.getOrNull()?.close()
                return true
            }
            result.onSuccess { socket ->
                reply.writeInt(1)
                socket.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
            }.onFailure { e ->
                Log.w(TAG, "Ponte per myCar verso $host:$port: ${e.message}")
                reply.writeInt(0)
                reply.writeString(e.message ?: e.javaClass.simpleName)
            }
            return true
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    /** La connessione verso [host]:[port], per myCar: l'altro capo di un socket locale. */
    private fun open(uid: Int, host: String, port: Int): ParcelFileDescriptor {
        val caller = packageManager.getPackagesForUid(uid).orEmpty().firstOrNull { isMyCar(this, it) }
            ?: throw SecurityException("il ponte è solo per myCar")
        val urls = (applicationContext as PlanciaApp).settings.load().urls
        if (!allowed(urls, host, port)) throw IOException("$host:$port non è l'Home Assistant di Plancia")
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.tcpNoDelay = true
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw IOException("Home Assistant a $host:$port non risponde a Plancia (${e.message})", e)
        }
        val (mine, theirs) = ParcelFileDescriptor.createSocketPair()
        relay(socket, mine)
        Log.i(TAG, "Ponte per $caller verso $host:$port")
        return theirs
    }

    /** Copia i dati tra il socket di Home Assistant e quello locale finché uno dei due si chiude. */
    private fun relay(socket: Socket, local: ParcelFileDescriptor) {
        val closed = AtomicBoolean(false)
        val ends = AtomicInteger(0)
        fun closeAll() {
            if (closed.compareAndSet(false, true)) {
                runCatching { socket.close() }
                runCatching { local.close() }
            }
        }
        fun ended() {
            if (ends.incrementAndGet() == 2) closeAll()
        }
        thread(name = "plancia-ponte-su", isDaemon = true) {
            // Da myCar a Home Assistant; finito quello che myCar manda, lo si dice al server.
            try {
                copy(FileInputStream(local.fileDescriptor), socket.getOutputStream())
                socket.shutdownOutput()
            } catch (e: IOException) {
                closeAll()
            } finally {
                ended()
            }
        }
        thread(name = "plancia-ponte-giu", isDaemon = true) {
            // Da Home Assistant a myCar; chiuso il server, myCar legge la fine.
            try {
                copy(socket.getInputStream(), FileOutputStream(local.fileDescriptor))
                Os.shutdown(local.fileDescriptor, OsConstants.SHUT_WR)
            } catch (e: Exception) {
                closeAll()
            } finally {
                ended()
            }
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return
            output.write(buffer, 0, n)
        }
    }

    companion object {
        private const val TAG = "Plancia"

        /** L'interfaccia del ponte, la stessa in Xonos e in myCar. */
        const val DESCRIPTOR = "io.github.sirionero.mycar.Bridge"

        /** Apri una connessione: host (String), porta (Int). */
        const val OPEN = IBinder.FIRST_CALL_TRANSACTION

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val BUFFER = 16 * 1024
        private const val MYCAR_PACKAGE = "io.github.sirionero.mycar"
        private const val MYCAR_DEV = "io.github.sirionero.mycar.dev"

        /**
         * Il pacchetto è di myCar ed è firmato con un certificato della lista degli host ammessi,
         * come per l'account; la prova di Plancia accetta anche quella di myCar.
         */
        fun isMyCar(context: Context, pkg: String): Boolean {
            if (!pkg.startsWith(MYCAR_PACKAGE)) return false
            if (BuildConfig.DEBUG && pkg == MYCAR_DEV) return true
            return context.resources.getStringArray(R.array.hosts_allowlist).any { entry ->
                val digest = entry.substringBefore(',').trim()
                entry.substringAfter(',').trim() == pkg && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    context.packageManager.hasSigningCertificate(pkg, hex(digest), PackageManager.CERT_INPUT_SHA256)
            }
        }

        private fun hex(text: String): ByteArray =
            ByteArray(text.length / 2) { i -> text.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

        /** Se [host]:[port] è uno degli indirizzi di Home Assistant scritti in Plancia (la porta di default dallo schema). */
        fun allowed(urls: List<String>, host: String, port: Int): Boolean = urls.any { url ->
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return@any false
            val name = uri.host?.removeSurrounding("[", "]")?.lowercase() ?: return@any false
            val own = if (uri.port > 0) uri.port else if (uri.scheme.equals("https", true) || uri.scheme.equals("wss", true)) 443 else 80
            name == host.removeSurrounding("[", "]").lowercase() && own == port
        }
    }
}
