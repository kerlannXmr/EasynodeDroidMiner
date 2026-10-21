package org.kerlann.easynode

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.torproject.jni.TorService
import java.io.File
import kotlin.coroutines.resume

/**
 * Tor intégré à l'application (bibliothèque tor-android du Guardian Project).
 * - crée un service onion (adresse .onion) qui donne accès au RPC restreint de monerod ;
 * - fournit un proxy SOCKS pour diffuser les transactions via Tor (--tx-proxy).
 * Orbot n'est pas nécessaire.
 */
object TorManager {
    /** Port du RPC restreint exposé sur l'adresse .onion (port standard des nœuds publics). */
    const val ONION_PORT = NodeService.RESTRICTED_PORT

    private const val PREFS = "easynode"
    private const val KEY_ONION = "onionAddress"

    private var connection: ServiceConnection? = null

    private fun torDir(ctx: Context): File = ctx.getDir("TorService", Context.MODE_PRIVATE)
    private fun serviceDir(ctx: Context): File = File(torDir(ctx), "onion_rpc")

    /** Dernière adresse .onion connue (elle ne change pas tant que les données de l'app sont gardées). */
    fun savedOnion(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ONION, null)

    /**
     * Démarre Tor et attend qu'il soit prêt.
     * @return Pair(port SOCKS, adresse .onion) ou null si Tor n'a pas démarré.
     */
    suspend fun start(ctx: Context): Pair<Int, String?>? {
        // Configuration du service onion : <onion>:18089 → 127.0.0.1:18089 (RPC restreint)
        TorService.getTorrc(ctx).writeText(
            "HiddenServiceDir ${serviceDir(ctx).absolutePath}\n" +
                "HiddenServicePort $ONION_PORT 127.0.0.1:$ONION_PORT\n" +
                // Plus de points d'introduction : connexion des wallets plus fiable
                "HiddenServiceNumIntroductionPoints 5\n"
        )

        val service = withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine<TorService> { cont ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val s = (binder as? TorService.LocalBinder)?.service
                        if (s != null && cont.isActive) cont.resume(s)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {}
                }
                connection = conn
                ctx.bindService(Intent(ctx, TorService::class.java), conn, Context.BIND_AUTO_CREATE)
            }
        } ?: return null

        // Attente du proxy SOCKS (Tor connecté au réseau)
        var waited = 0
        while (service.socksPort <= 0 && waited < 120) {
            delay(1_000); waited++
        }
        if (service.socksPort <= 0) return null

        // Lecture de l'adresse .onion générée par Tor
        val hostname = File(serviceDir(ctx), "hostname")
        waited = 0
        while (!hostname.exists() && waited < 60) {
            delay(1_000); waited++
        }
        val onion = if (hostname.exists()) hostname.readText().trim().ifBlank { null } else null
        if (onion != null) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ONION, onion).apply()
        }
        return service.socksPort to onion
    }

    fun stop(ctx: Context) {
        connection?.let {
            try {
                ctx.unbindService(it)
            } catch (_: Exception) {
            }
        }
        connection = null
    }
}
