package org.kerlann.easynode

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class UpdatePhase { IDLE, DOWNLOADING, STOPPING_NODE, INSTALLING, WAITING_USER, ERROR }

data class UpdateState(val phase: UpdatePhase = UpdatePhase.IDLE, val progress: Float = 0f, val message: String? = null)

/** L'activité principale est-elle à l'écran ? (pour savoir si on peut ouvrir la fenêtre de confirmation) */
object AppVisibility {
    @Volatile var foreground = false
}

/**
 * Mise à jour intégrée : téléchargement → vérification de la signature →
 * arrêt propre du nœud → installation (PackageInstaller) → redémarrage du nœud.
 */
object Updater {
    private const val PREFS = "easynode"
    private const val KEY_RESUME = "resumeNodeAfterUpdate"
    private const val CHANNEL_ID = "updates"
    private const val CONFIRM_NOTIF_ID = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state

    val busy: Boolean
        get() = _state.value.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.STOPPING_NODE, UpdatePhase.INSTALLING)

    /**
     * @param interactive true si l'utilisateur a touché le bouton (on peut ouvrir les Réglages Android) ;
     *                    false si lancé en arrière-plan par le service.
     */
    fun start(context: Context, info: UpdateInfo, interactive: Boolean) {
        val ctx = context.applicationContext
        if (busy) return
        val pm = ctx.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !pm.canRequestPackageInstalls()) {
            _state.value = UpdateState(UpdatePhase.ERROR, message = ctx.getString(R.string.upd_need_permission))
            if (interactive) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        scope.launch { run(ctx, info) }
    }

    private suspend fun run(ctx: Context, info: UpdateInfo) {
        try {
            // 1. Téléchargement
            _state.value = UpdateState(UpdatePhase.DOWNLOADING, 0f)
            val apk = File(ctx.cacheDir, "update.apk")
            if (!download(info.apkUrl, apk)) {
                _state.value = UpdateState(UpdatePhase.ERROR, message = ctx.getString(R.string.upd_err_download))
                return
            }

            // 2. Vérification : même application, version plus récente, MÊME clé de signature
            if (!verify(ctx, apk)) {
                apk.delete()
                _state.value = UpdateState(UpdatePhase.ERROR, message = ctx.getString(R.string.upd_err_signature))
                return
            }

            // 3. Arrêt propre du nœud (la mise à jour tue l'application)
            val status = NodeState.status.value
            if (status == NodeStatus.RUNNING || status == NodeStatus.STARTING) {
                _state.value = UpdateState(UpdatePhase.STOPPING_NODE)
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_RESUME, true).commit()
                NodeService.stop(ctx)
                var waited = 0
                while (NodeState.status.value != NodeStatus.STOPPED && NodeState.status.value != NodeStatus.ERROR && waited < 150) {
                    delay(1_000); waited++
                }
            }

            // 4. Installation
            _state.value = UpdateState(UpdatePhase.INSTALLING)
            install(ctx, apk)
        } catch (e: Exception) {
            fail(ctx, e.message ?: e.toString())
        }
    }

    private fun download(url: String, target: File): Boolean = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        if (conn.responseCode != 200) {
            conn.disconnect()
            false
        } else {
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = UpdateState(UpdatePhase.DOWNLOADING, done.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
            total <= 0 || done == total
        }
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun verify(ctx: Context, apk: File): Boolean {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        if (archive.packageName != ctx.packageName) return false
        if (versionCode(archive) <= BuildConfig.VERSION_CODE) return false
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        val a = signatures(archive)
        val b = signatures(installed)
        return a.isNotEmpty() && a == b
    }

    @Suppress("DEPRECATION")
    private fun versionCode(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signatures(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else p.signatures
        return sigs.orEmpty().map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ : pas de confirmation si l'app est déjà l'installateur d'elle-même
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(ctx, sessionId, Intent(ctx, InstallResultReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallResult(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                _state.value = UpdateState(UpdatePhase.WAITING_USER, message = ctx.getString(R.string.upd_confirm))
                if (AppVisibility.foreground) {
                    ctx.startActivity(confirm)
                } else {
                    notifyConfirm(ctx, confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // L'application va être relancée par Android ; PackageReplacedReceiver redémarre le nœud.
                _state.value = UpdateState()
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "?"
                fail(ctx, ctx.getString(R.string.upd_err_install, msg))
            }
        }
    }

    private fun fail(ctx: Context, message: String) {
        _state.value = UpdateState(UpdatePhase.ERROR, message = message)
        resumeNodeIfNeeded(ctx)
    }

    /** Redémarre le nœud s'il tournait avant la mise à jour. */
    fun resumeNodeIfNeeded(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(KEY_RESUME, false)) {
            p.edit().putBoolean(KEY_RESUME, false).commit()
            try {
                NodeService.start(ctx)
            } catch (_: Exception) {
            }
        }
    }

    /** Mise à jour automatique autorisée maintenant ? (réglage + pas sur données mobiles sauf permission) */
    fun autoAllowed(ctx: Context): Boolean {
        val s = SettingsStore.load(ctx)
        if (!s.autoInstall) return false
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return s.allowMetered || !cm.isActiveNetworkMetered
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.update_notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun notifyConfirm(ctx: Context, confirm: Intent) {
        ensureChannel(ctx)
        val pi = PendingIntent.getActivity(
            ctx, 3, confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ctx.getSystemService(NotificationManager::class.java).notify(
            CONFIRM_NOTIF_ID,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_node)
                .setContentTitle(ctx.getString(R.string.update_notif_title))
                .setContentText(ctx.getString(R.string.upd_notif_confirm))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
    }
}

/** Reçoit le résultat de l'installation. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Updater.onInstallResult(context, intent)
    }
}

/** Appelé par Android juste après la mise à jour de l'application. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Updater.resumeNodeIfNeeded(context)
        }
    }
}
