package org.kerlann.easynode

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

data class StorageOption(val label: String, val dir: File, val removable: Boolean)

/** Remplace les étapes 6/7 du script (disque externe) : mémoire interne ou carte SD. */
object Storage {

    fun options(ctx: Context): List<StorageOption> {
        val list = mutableListOf(
            StorageOption(ctx.getString(R.string.storage_internal), File(ctx.filesDir, "bitmonero"), false)
        )
        // Index 0 = stockage partagé émulé ; les suivants = cartes SD amovibles.
        ctx.getExternalFilesDirs(null).drop(1).filterNotNull().forEachIndexed { i, d ->
            if (Environment.getExternalStorageState(d) == Environment.MEDIA_MOUNTED) {
                val label = ctx.getString(R.string.storage_sd) + if (i > 0) " ${i + 1}" else ""
                list += StorageOption(label, File(d, "bitmonero"), true)
            }
        }
        return list
    }

    fun selected(ctx: Context, s: NodeSettings): StorageOption {
        val opts = options(ctx)
        return opts.getOrElse(s.storageIndex) { opts[0] }
    }

    fun freeBytes(dir: File): Long {
        var base: File? = dir
        while (base != null && !base.exists()) base = base.parentFile
        return try {
            StatFs((base ?: dir).path).availableBytes
        } catch (e: Exception) {
            0L
        }
    }

    fun usedBytes(dir: File): Long =
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
