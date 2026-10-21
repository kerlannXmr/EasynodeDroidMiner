package org.kerlann.easynode

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Pilote le mineur intégré à monerod via le RPC complet (127.0.0.1:18081) :
 * /start_mining, /stop_mining, /mining_status.
 * Appelé toutes les 10 s par NodeService (et tout de suite après un changement de réglage).
 */
class MiningController(private val ctx: Context) {

    private companion object {
        const val HOT_C = 60f   // pause à partir de 60 °C, reprise dès que c'est en dessous
    }

    private var hot = false
    private var lastPause: MiningPause? = null
    private var lastError: String? = null

    fun tick(rpc: RpcClient, synced: Boolean) {
        val m = MiningStore.load(ctx)
        val st = rpc.other("/mining_status")
        val active = st?.optBoolean("active") == true

        if (!m.enabled) {
            if (active) stop(rpc)
            lastPause = null
            lastError = null
            NodeState.setMining(null)
            return
        }

        val threads = m.threads.coerceIn(1, maxMiningThreads())
        val address = m.address.trim()
        val (charging, tempC) = battery()
        updateHot(m.pauseHot, tempC)

        val pause = when {
            !isValidMiningAddress(address) -> MiningPause.BAD_ADDRESS
            !synced -> MiningPause.NOT_SYNCED
            m.onlyCharging && !charging -> MiningPause.NOT_CHARGING
            m.pauseHot && hot -> MiningPause.TOO_HOT
            else -> null
        }
        if (pause != lastPause) {
            pause?.let { NodeState.appendLog(ctx.getString(it.text)) }
            lastPause = pause
        }

        var running = active
        if (pause != null) {
            if (active) { stop(rpc); running = false }
        } else {
            // Nombre de cœurs ou adresse changés : on relance le mineur avec les nouveaux réglages
            if (active && (st?.optInt("threads_count") != threads || st?.optString("address") != address)) {
                stop(rpc)
                running = false
            }
            if (!running) running = start(rpc, address, threads)
        }

        NodeState.setMining(
            MiningInfo(
                active = running,
                speed = if (running && active) st?.optLong("speed") ?: 0L else 0L,
                threads = threads,
                difficulty = st?.optLong("difficulty") ?: 0L,
                pause = pause,
            )
        )
    }

    private fun start(rpc: RpcClient, address: String, threads: Int): Boolean {
        val body = JSONObject()
            .put("miner_address", address)
            .put("threads_count", threads)
            .put("do_background_mining", false)
            .put("ignore_battery", true)
        val status = rpc.other("/start_mining", body)?.optString("status") ?: "no answer"
        if (status == "OK") {
            lastError = null
            NodeState.appendLog(ctx.getString(R.string.log_mine_started, threads))
            return true
        }
        if (status != lastError) {
            lastError = status
            NodeState.appendLog(ctx.getString(R.string.log_mine_failed, status))
        }
        return false
    }

    private fun stop(rpc: RpcClient) {
        rpc.other("/stop_mining")
        NodeState.appendLog(ctx.getString(R.string.log_mine_stopped))
    }

    /** Branché sur secteur ? Température de la batterie (°C, 0 si inconnue). */
    private fun battery(): Pair<Boolean, Float> {
        val i = ContextCompat.registerReceiver(
            ctx, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        ) ?: return true to 0f
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        return plugged to temp
    }

    /**
     * Pause si la batterie atteint 60 °C, ou si Android signale lui-même une surchauffe grave
     * (sécurité). Pas de température de reprise : le minage repart dès que c'est redescendu.
     */
    private fun updateHot(enabled: Boolean, tempC: Float) {
        if (!enabled) { hot = false; return }
        val severe = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ctx.getSystemService(PowerManager::class.java).currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        hot = severe || tempC >= HOT_C
    }
}
