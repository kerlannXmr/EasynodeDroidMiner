package org.kerlann.easynode

import androidx.annotation.StringRes
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class NodeStatus { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

data class NodeInfo(
    val height: Long,
    val targetHeight: Long,
    val incoming: Int,
    val outgoing: Int,
    val synchronized: Boolean,
    val databaseSize: Long,
    val version: String,
) {
    val progress: Float
        get() {
            val target = maxOf(targetHeight, height)
            return if (target <= 0) 0f else (height.toFloat() / target).coerceIn(0f, 1f)
        }
}

/** Raison pour laquelle le minage est en pause. */
enum class MiningPause(@StringRes val text: Int) {
    BAD_ADDRESS(R.string.mine_pause_address),
    NOT_SYNCED(R.string.mine_pause_sync),
    NOT_CHARGING(R.string.mine_pause_charging),
    TOO_HOT(R.string.mine_pause_hot),
}

data class MiningInfo(
    val active: Boolean,
    val speed: Long,       // H/s
    val threads: Int,
    val difficulty: Long,  // difficulté du réseau
    val pause: MiningPause?,
)

/** État partagé entre le service (qui fait tourner monerod) et l'interface. */
object NodeState {
    private const val MAX_LOG_LINES = 3000

    private val _status = MutableStateFlow(NodeStatus.STOPPED)
    val status: StateFlow<NodeStatus> = _status

    private val _info = MutableStateFlow<NodeInfo?>(null)
    val info: StateFlow<NodeInfo?> = _info

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val _mining = MutableStateFlow<MiningInfo?>(null)
    val mining: StateFlow<MiningInfo?> = _mining

    /** Réveille tout de suite la boucle du service après un changement de réglage du minage. */
    private val _miningWake = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val miningWake: SharedFlow<Unit> = _miningWake

    fun setStatus(s: NodeStatus) {
        _status.value = s
        if (s == NodeStatus.STARTING) _error.value = null
        if (s == NodeStatus.STOPPED || s == NodeStatus.ERROR) {
            _info.value = null
            _mining.value = null
            _fastModeStarted.value = null
        }
    }

    fun setMining(m: MiningInfo?) { _mining.value = m }

    /** Mode du minage choisi au démarrage de monerod (true = rapide) ; null = nœud arrêté. */
    private val _fastModeStarted = MutableStateFlow<Boolean?>(null)
    val fastModeStarted: StateFlow<Boolean?> = _fastModeStarted

    fun setFastModeStarted(v: Boolean?) { _fastModeStarted.value = v }

    fun wakeMining() { _miningWake.tryEmit(Unit) }

    fun setInfo(i: NodeInfo?) { _info.value = i }

    fun fail(message: String) {
        _error.value = message
        appendLog("!! $message")
        setStatus(NodeStatus.ERROR)
    }

    fun setError(message: String?) { _error.value = message }

    fun appendLog(line: String) {
        _log.update { (it + line).takeLast(MAX_LOG_LINES) }
    }
}
