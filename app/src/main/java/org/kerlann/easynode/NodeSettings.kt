package org.kerlann.easynode

import android.content.Context

/** Équivalent de bitmonero.conf : réglages choisis par l'utilisateur. */
data class NodeSettings(
    val storageIndex: Int = 0,
    val useBanList: Boolean = true,
    val rpcLan: Boolean = false,
    val rpcLogin: Boolean = false,
    val rpcUser: String = "easynode",
    val rpcPassword: String = "",
    val torTx: Boolean = false,
    val allowMetered: Boolean = false,
    val checkUpdates: Boolean = true,
    val autoInstall: Boolean = true,
) {
    val rpcCredentials: Pair<String, String>?
        get() = if (rpcLogin && rpcUser.isNotBlank() && rpcPassword.isNotEmpty()) rpcUser to rpcPassword else null

    /** Espace disque approximatif requis (blockchain toujours élaguée). */
    val requiredBytes: Long
        get() = 115L * 1_000_000_000
}

/**
 * Option « Redémarrage automatique » de l'accueil (modifiable même quand le nœud tourne).
 * Activée par défaut dans la Miner : pendant le minage, le téléphone peut tuer monerod (code 137).
 */
object AutoRestartStore {
    private const val PREFS = "easynode"
    private const val KEY = "autoRestart"

    fun get(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun set(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}

object SettingsStore {
    private const val PREFS = "easynode"

    fun load(ctx: Context): NodeSettings {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = NodeSettings()
        return NodeSettings(
            storageIndex = p.getInt("storageIndex", d.storageIndex),
            useBanList = p.getBoolean("useBanList", d.useBanList),
            rpcLan = p.getBoolean("rpcLan", d.rpcLan),
            rpcLogin = p.getBoolean("rpcLogin", d.rpcLogin),
            rpcUser = p.getString("rpcUser", d.rpcUser) ?: d.rpcUser,
            rpcPassword = p.getString("rpcPassword", d.rpcPassword) ?: d.rpcPassword,
            torTx = p.getBoolean("torTx", d.torTx),
            allowMetered = p.getBoolean("allowMetered", d.allowMetered),
            checkUpdates = p.getBoolean("checkUpdates", d.checkUpdates),
            autoInstall = p.getBoolean("autoInstall", d.autoInstall),
        )
    }

    fun save(ctx: Context, s: NodeSettings) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("storageIndex", s.storageIndex)
            .putBoolean("useBanList", s.useBanList)
            .putBoolean("rpcLan", s.rpcLan)
            .putBoolean("rpcLogin", s.rpcLogin)
            .putString("rpcUser", s.rpcUser)
            .putString("rpcPassword", s.rpcPassword)
            .putBoolean("torTx", s.torTx)
            .putBoolean("allowMetered", s.allowMetered)
            .putBoolean("checkUpdates", s.checkUpdates)
            .putBoolean("autoInstall", s.autoInstall)
            .apply()
    }
}
