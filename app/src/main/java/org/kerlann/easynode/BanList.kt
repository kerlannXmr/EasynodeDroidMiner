package org.kerlann.easynode

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Port de download_ban_list() : télécharge, nettoie, remplace atomiquement. */
object BanList {
    private const val URL_LIST = "https://raw.githubusercontent.com/Boog900/monero-ban-list/main/ban_list.txt"
    private val IPV4_CIDR = Regex("""^(\d{1,3}\.){3}\d{1,3}(/\d{1,2})?$""")

    /** @return Pair(succès du téléchargement, nombre d'entrées dans le fichier final) */
    fun update(target: File): Pair<Boolean, Int> {
        val ok = try {
            val conn = URL(URL_LIST).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val clean = raw.lineSequence()
                .map { it.replace("\r", "").substringBefore('#').trim().split(Regex("\\s+")).first() }
                .filter { IPV4_CIDR.matches(it) }
                .toSortedSet()
            if (clean.isEmpty()) {
                false
            } else {
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.writeText(clean.joinToString("\n", postfix = "\n"))
                tmp.renameTo(target)
            }
        } catch (e: Exception) {
            false
        }
        return ok to count(target)
    }

    fun count(file: File): Int =
        if (!file.exists()) 0 else file.useLines { lines -> lines.count { it.isNotBlank() } }
}
