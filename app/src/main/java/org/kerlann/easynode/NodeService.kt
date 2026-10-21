package org.kerlann.easynode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Service de premier plan qui exécute monerod (équivalent de start_blockchain()).
 * monerod est embarqué dans l'APK sous le nom libmonerod.so : c'est le seul
 * emplacement depuis lequel Android 10+ autorise l'exécution d'un binaire.
 */
class NodeService : Service() {

    companion object {
        const val ACTION_START = "org.kerlann.easynode.START"
        const val ACTION_STOP = "org.kerlann.easynode.STOP"
        const val RPC_PORT = 18081
        /** RPC restreint : wallets du réseau local et adresse .onion */
        const val RESTRICTED_PORT = 18089
        const val P2P_PORT = 18080
        private const val CHANNEL_ID = "node"
        private const val NOTIF_ID = 1
        private const val UPDATE_CHANNEL_ID = "updates"
        private const val UPDATE_NOTIF_ID = 2
        private const val UPDATE_INTERVAL_MS = 12L * 3600 * 1000

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, NodeService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, NodeService::class.java).setAction(ACTION_STOP))
        }

        fun binary(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir, "libmonerod.so")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var process: Process? = null
    @Volatile private var stopping = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopNode()
            else -> {
                goForeground(getString(R.string.notif_starting))
                if (job?.isActive != true) {
                    stopping = false
                    job = scope.launch { runNode() }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        process?.destroy()
        TorManager.stop(this)
        releaseLocks()
        scope.cancel()
        super.onDestroy()
    }

    // ── Exécution ───────────────────────────────────────────────────────────

    private suspend fun runNode() {
        val s = SettingsStore.load(this)
        NodeState.setStatus(NodeStatus.STARTING)
        var failed = false
        try {
            val bin = binary(this)
            if (!bin.exists()) throw IllegalStateException(getString(R.string.err_no_binary))

            val dataDir = Storage.selected(this, s).dir
            dataDir.mkdirs()
            if (!dataDir.canWrite()) throw IllegalStateException(getString(R.string.err_not_writable, dataDir.path))

            var banFile: File? = null
            if (s.useBanList) {
                NodeState.appendLog(getString(R.string.log_banlist_download))
                val f = File(filesDir, "ban_list.txt")
                val (ok, count) = BanList.update(f)
                NodeState.appendLog(
                    if (ok) getString(R.string.log_banlist_ok, count) else getString(R.string.log_banlist_fail, count)
                )
                if (count > 0) banFile = f
            }

            if (stopping) return

            // Tor intégré : adresse .onion pour les wallets + transactions via Tor
            var torSocks: Int? = null
            if (s.torTx) {
                NodeState.appendLog(getString(R.string.log_tor_start))
                val tor = TorManager.start(this) ?: throw IllegalStateException(getString(R.string.err_tor))
                torSocks = tor.first
                NodeState.appendLog(getString(R.string.log_tor_ready, tor.first))
                tor.second?.let { NodeState.appendLog(getString(R.string.log_tor_onion, it, TorManager.ONION_PORT)) }
            }

            if (stopping) return

            val args = buildArgs(bin, dataDir, s, banFile, torSocks)
            val shown = args.joinToString(" ").let { line ->
                s.rpcCredentials?.let { line.replace(it.second, "****") } ?: line
            }
            NodeState.appendLog("$ $shown")

            val pb = ProcessBuilder(args).redirectErrorStream(true).directory(dataDir)
            pb.environment()["HOME"] = filesDir.absolutePath
            // Minage : par défaut on interdit le « mode rapide » de RandomX (≈ 2,3 Go de mémoire) qui fait
            // fermer l'app par Android sur la plupart des téléphones. Le mineur utilise alors le mode léger
            // (≈ 256 Mo). 4 = RANDOMX_FLAG_FULL_MEM. Sans effet sur le nœud lui-même (déjà en mode léger).
            // Le mode rapide n'est autorisé que si l'utilisateur l'a choisi ET que le téléphone a ≥ 8 Go.
            val fast = MiningStore.load(this).fastMode && fastModeAllowed(this)
            if (!fast) pb.environment()["MONERO_RANDOMX_UMASK"] = "4"
            NodeState.setFastModeStarted(fast)
            if (fast) NodeState.appendLog(getString(R.string.log_mine_fast))
            val proc = pb.start()
            process = proc
            acquireLocks()
            NodeState.setStatus(NodeStatus.RUNNING)
            updateNotification(getString(R.string.notif_running))

            val reader = scope.launch {
                try {
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach {
                            val line = stripAnsi(it)
                            NodeState.appendLog(line)
                            // Message de monerod quand le mineur intégré trouve un bloc
                            if (FOUND_BLOCK.containsMatchIn(line)) {
                                MiningStore.addBlockFound(this@NodeService)
                                NodeState.appendLog(getString(R.string.log_mine_block))
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            val poller = scope.launch { pollLoop(s) }

            val exit = runInterruptible { proc.waitFor() }
            poller.cancel()
            withTimeoutOrNull(3_000) { reader.join() }
            process = null

            if (!stopping && exit != 0) {
                failed = true
                NodeState.fail(getString(R.string.err_exit, exit))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
            NodeState.fail(e.message ?: e.toString())
        } finally {
            process = null
            TorManager.stop(this)
            releaseLocks()
            if (!failed) NodeState.setStatus(NodeStatus.STOPPED)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Traduction de bitmonero.conf (configure_monero) en options de ligne de commande. */
    private fun buildArgs(bin: File, dataDir: File, s: NodeSettings, banFile: File?, torSocks: Int?): List<String> {
        val a = mutableListOf(
            bin.absolutePath,
            "--data-dir", dataDir.absolutePath,
            "--log-file", File(dataDir, "bitmonero.log").absolutePath,
            "--max-log-file-size", "10000000",
            "--max-log-files", "2",
            "--log-level", "0",
            "--non-interactive",
            "--no-zmq",
            "--p2p-bind-ip", "0.0.0.0",
            "--p2p-bind-port", P2P_PORT.toString(),
            "--rpc-bind-port", RPC_PORT.toString(),
            // Avec Tor : moins de pairs et envoi P2P limité, pour laisser la bande passante
            // de la connexion internet aux wallets qui passent par l'adresse .onion
            "--out-peers", if (torSocks != null) "8" else "16",
            "--in-peers", if (torSocks != null) "8" else "32",
            "--disable-dns-checkpoints",
        )
        // RPC complet sur 127.0.0.1 uniquement (l'app et un wallet sur ce téléphone).
        // Le RPC restreint masque le nombre de pairs, la version, etc. : l'app ne doit pas l'utiliser.
        a += listOf("--rpc-bind-ip", "127.0.0.1")
        // RPC restreint sur le port 18089 : réseau local (0.0.0.0) et/ou adresse .onion (127.0.0.1)
        if (s.rpcLan || torSocks != null) {
            a += listOf(
                "--rpc-restricted-bind-ip", if (s.rpcLan) "0.0.0.0" else "127.0.0.1",
                "--rpc-restricted-bind-port", RESTRICTED_PORT.toString(),
            )
            if (s.rpcLan) a += "--confirm-external-bind"
        }
        s.rpcCredentials?.let { (u, p) -> a += listOf("--rpc-login", "$u:$p") }
        // Toujours élaguée : une base complète existante est élaguée par monerod au démarrage
        a += listOf("--prune-blockchain", "--sync-pruned-blocks")
        if (banFile != null) a += listOf("--ban-list", banFile.absolutePath)
        if (torSocks != null) {
            a += listOf("--limit-rate-up", "512") // ko/s
            // Transactions diffusées via Tor
            a += listOf("--tx-proxy", "tor,127.0.0.1:$torSocks,16")
        }
        return a
    }

    private suspend fun pollLoop(s: NodeSettings) {
        val rpc = RpcClient(RPC_PORT, s.rpcCredentials)
        val miner = MiningController(this)
        var lastUpdateCheck = 0L
        delay(8_000)
        while (scope.isActive) {
            if (System.currentTimeMillis() - lastUpdateCheck > UPDATE_INTERVAL_MS) {
                lastUpdateCheck = System.currentTimeMillis()
                UpdateChecker.check(this)?.let { info ->
                    if (UpdateChecker.shouldNotify(this, info)) {
                        val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
                        // Mise à jour 100 % automatique si autorisée, sinon simple notification
                        if (Updater.autoAllowed(this) && canInstall) Updater.start(this, info, interactive = false)
                        else notifyUpdate(info)
                    }
                }
            }
            val r = rpc.getInfo()
            if (r != null) {
                val info = NodeInfo(
                    height = r.optLong("height"),
                    targetHeight = r.optLong("target_height"),
                    incoming = r.optInt("incoming_connections_count"),
                    outgoing = r.optInt("outgoing_connections_count"),
                    synchronized = r.optBoolean("synchronized"),
                    databaseSize = r.optLong("database_size"),
                    // "0.18.4.5-release" → "0.18.4.5" (évite un retour à la ligne sur petits écrans)
                    version = r.optString("version").substringBefore("-"),
                )
                NodeState.setInfo(info)
                miner.tick(rpc, info.synchronized)
                val peers = info.incoming + info.outgoing
                val text = if (info.synchronized) getString(R.string.notif_synced, info.height, peers)
                else getString(R.string.notif_sync, percent(info.progress), peers)
                val m = NodeState.mining.value
                updateNotification(
                    if (m?.active == true) text + " · " + getString(R.string.notif_mining, m.speed) else text
                )
            }
            // 10 s, ou tout de suite si un réglage du minage vient de changer
            withTimeoutOrNull(10_000) { NodeState.miningWake.first() }
        }
    }

    // ── Arrêt (équivalent CTRL+C / option 9) ─────────────────────────────────

    private fun stopNode() {
        stopping = true
        val p = process
        if (p == null) {
            job?.cancel()
            NodeState.setStatus(NodeStatus.STOPPED)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        NodeState.setStatus(NodeStatus.STOPPING)
        updateNotification(getString(R.string.notif_stopping))
        scope.launch {
            p.destroy() // SIGTERM : monerod ferme proprement la base LMDB
            val done = runInterruptible { p.waitFor(120, TimeUnit.SECONDS) }
            if (!done) p.destroyForcibly()
        }
    }

    // ── Notification / verrous ──────────────────────────────────────────────

    private fun goForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(text), type)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, NodeService::class.java).setAction(ACTION_STOP), flags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_node)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.btn_stop), stop)
            .build()
    }

    private fun notifyUpdate(info: UpdateInfo) {
        Updater.ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        getSystemService(NotificationManager::class.java).notify(
            UPDATE_NOTIF_ID,
            NotificationCompat.Builder(this, UPDATE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_node)
                .setContentTitle(getString(R.string.update_notif_title))
                .setContentText(info.name)
                .setStyle(NotificationCompat.BigTextStyle().bigText(info.name + "\n" + getString(R.string.upd_notif_open)))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EasyNode::monerod")
                .apply { setReferenceCounted(false); acquire() }
        }
        if (wifiLock == null) {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                ?.createWifiLock(mode, "EasyNode::wifi")
                ?.apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }
}

private val FOUND_BLOCK = Regex("Found block .* at height")

private val ANSI = Regex("\u001B\\[[;\\d]*[A-Za-z]")
fun stripAnsi(s: String) = s.replace(ANSI, "")

fun percent(f: Float): String = String.format(Locale.getDefault(), "%.1f %%", f * 100)
