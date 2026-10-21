package org.kerlann.easynode

import android.app.ActivityManager
import android.content.Context

/**
 * Réglages du minage solo (mineur intégré à monerod).
 * Rangés à part de NodeSettings : chaque écran n'enregistre que ses propres réglages.
 */
data class MiningSettings(
    val enabled: Boolean = false,
    val address: String = "",
    val threads: Int = 1,
    val onlyCharging: Boolean = true,
    val pauseHot: Boolean = true,
    /** Mode rapide de RandomX (≈ 2,3 Go de mémoire) : seulement sur les téléphones avec assez de mémoire. */
    val fastMode: Boolean = false,
) {
    val addressValid: Boolean
        get() = isValidMiningAddress(address)
}

/** Adresse principale mainnet (95 caractères, commence par 4). monerod refuse les sous-adresses (8…). */
private val MAIN_ADDRESS = Regex("^4[1-9AB][1-9A-HJ-NP-Za-km-z]{93}$")

fun isValidMiningAddress(a: String): Boolean = MAIN_ADDRESS.matches(a.trim())

/** Un téléphone « 8 Go » annonce un peu moins (environ 7,5 Go) : on accepte à partir de 7 Go. */
private const val FAST_MODE_MIN_RAM = 7_000_000_000L

/** Mémoire totale du téléphone (octets). */
fun totalRam(ctx: Context): Long {
    val mi = ActivityManager.MemoryInfo()
    ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
    return mi.totalMem
}

/** Le mode rapide n'est proposé qu'aux téléphones d'au moins 8 Go de mémoire. */
fun fastModeAllowed(ctx: Context): Boolean = totalRam(ctx) >= FAST_MODE_MIN_RAM

/** Nombre de cœurs du processeur (maximum pour le minage). */
fun maxMiningThreads(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

object MiningStore {
    private const val PREFS = "easynode"

    fun load(ctx: Context): MiningSettings {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = MiningSettings()
        return MiningSettings(
            enabled = p.getBoolean("mineEnabled", d.enabled),
            address = p.getString("mineAddress", d.address) ?: d.address,
            threads = p.getInt("mineThreads", d.threads),
            onlyCharging = p.getBoolean("mineOnlyCharging", d.onlyCharging),
            pauseHot = p.getBoolean("minePauseHot", d.pauseHot),
            fastMode = p.getBoolean("mineFastMode", d.fastMode),
        )
    }

    fun save(ctx: Context, m: MiningSettings) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("mineEnabled", m.enabled)
            .putString("mineAddress", m.address)
            .putInt("mineThreads", m.threads)
            .putBoolean("mineOnlyCharging", m.onlyCharging)
            .putBoolean("minePauseHot", m.pauseHot)
            .putBoolean("mineFastMode", m.fastMode)
            .apply()
    }

    fun blocksFound(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("mineBlocksFound", 0)

    fun addBlockFound(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putInt("mineBlocksFound", p.getInt("mineBlocksFound", 0) + 1).apply()
    }
}
