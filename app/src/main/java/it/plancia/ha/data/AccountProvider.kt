package it.plancia.ha.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import it.plancia.ha.PlanciaApp
import it.plancia.ha.R

/**
 * Passa l'account di Home Assistant di Plancia a myCar, che ha Plancia dentro di sé: indirizzi,
 * token, plancia predefinita e dimensione, così lì non si riscrive niente.
 *
 * Solo a myCar: il pacchetto che chiede e il digest SHA-256 del suo certificato di firma devono
 * stare nella stessa lista degli host ammessi (hosts_allowlist), fra le voci di myCar. A chiunque
 * altro, Gearslip compreso, non risponde.
 *
 * myCar chiama: contentResolver.call(Uri.parse("content://it.plancia.ha.account"), "account", null, null)
 */
class AccountProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_ACCOUNT) return null
        val context = context ?: return null
        val caller = try {
            callingPackage
        } catch (e: SecurityException) {
            null
        } ?: return null
        if (!isMyCar(context, caller)) {
            Log.w(TAG, "Account negato a $caller")
            return null
        }
        val settings = (context.applicationContext as PlanciaApp).settings
        val config = settings.load()
        // Plancia non ancora configurata: niente da passare, ma la risposta dice che Plancia c'è.
        if (!config.isComplete) return Bundle()
        Log.i(TAG, "Account passato a $caller")
        return Bundle().apply {
            putString("home", config.homeUrl)
            putString("remote", config.remoteUrl)
            putString("token", config.token)
            putString("dashboard", settings.defaultDashboard)
            putInt("zoom", settings.zoom)
        }
    }

    /** Il pacchetto è di myCar ed è firmato con un certificato della lista. */
    private fun isMyCar(context: Context, pkg: String): Boolean =
        context.resources.getStringArray(R.array.hosts_allowlist).any { entry ->
            val digest = entry.substringBefore(',').trim()
            val allowed = entry.substringAfter(',').trim()
            allowed == pkg && pkg.startsWith(MYCAR_PACKAGE) && signedWith(context, pkg, hex(digest))
        }

    /** myCar richiede Android 12: sui telefoni più vecchi non c'è da rispondere a nessuno. */
    private fun signedWith(context: Context, pkg: String, digest: ByteArray): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSigningCertificate(pkg, digest, PackageManager.CERT_INPUT_SHA256)

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { i -> text.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    // Nient'altro: si risponde solo a call().
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val TAG = "Plancia"
        const val METHOD_ACCOUNT = "account"
        const val MYCAR_PACKAGE = "io.github.sirionero.mycar"
    }
}
