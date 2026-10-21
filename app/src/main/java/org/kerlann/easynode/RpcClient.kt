package org.kerlann.easynode

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Client JSON-RPC minimal pour monerod (127.0.0.1), avec authentification
 * HTTP Digest quand --rpc-login est actif (même logique que `curl --digest`).
 *
 * monerod propose plusieurs challenges (SHA-256, SHA-256-sess, MD5, MD5-sess)
 * avec le même nonce ; on répond avec SHA-256 ou MD5 (variante non "-sess").
 * Après chaque 401 monerod remet son compteur à zéro : la requête suivante
 * est acceptée avec nc=00000001.
 */
class RpcClient(
    private val port: Int,
    private val credentials: Pair<String, String>?,
) {
    private var lastLoggedCode = 0

    fun getInfo(): JSONObject? = call("get_info")

    fun call(method: String): JSONObject? =
        request("/json_rpc", """{"jsonrpc":"2.0","id":"0","method":"$method"}""")?.optJSONObject("result")

    /** Autres points d'accès de monerod (ex. /start_mining, /mining_status) : réponse JSON complète. */
    fun other(path: String, body: JSONObject = JSONObject()): JSONObject? = request(path, body.toString())

    private fun request(path: String, body: String): JSONObject? {
        return try {
            var conn = post(path, body, null)
            var code = conn.responseCode
            if (code == 401 && credentials != null) {
                val challenges = conn.headerFields.entries
                    .filter { it.key?.equals("WWW-Authenticate", ignoreCase = true) == true }
                    .flatMap { it.value }
                drain(conn)
                val auth = digestHeader(challenges, "POST", path)
                if (auth == null) {
                    logOnce(-1, "RPC: authentication challenge not understood: ${challenges.joinToString(" | ")}")
                    return null
                }
                conn = post(path, body, auth)
                code = conn.responseCode
            }
            if (code != 200) {
                logOnce(code, "RPC: HTTP $code")
                drain(conn)
                return null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            lastLoggedCode = 200
            JSONObject(text)
        } catch (e: Exception) {
            null
        }
    }

    /** Écrit une seule fois chaque type d'erreur dans le Journal (pas de spam toutes les 10 s). */
    private fun logOnce(code: Int, message: String) {
        if (code != lastLoggedCode) {
            lastLoggedCode = code
            NodeState.appendLog(message)
        }
    }

    private fun drain(conn: HttpURLConnection) {
        try {
            (conn.errorStream ?: conn.inputStream)?.use { it.readBytes() }
        } catch (_: Exception) {
        }
        conn.disconnect()
    }

    private fun post(path: String, body: String, auth: String?): HttpURLConnection {
        val conn = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 20_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.useCaches = false
        conn.setRequestProperty("Content-Type", "application/json")
        if (auth != null) conn.setRequestProperty("Authorization", auth)
        conn.outputStream.use { it.write(body.toByteArray()) }
        return conn
    }

    private data class Challenge(val algorithm: String, val params: Map<String, String>)

    private fun parseChallenges(headers: List<String>): List<Challenge> {
        // Certains systèmes fusionnent plusieurs en-têtes en une seule ligne : on redécoupe sur "Digest".
        val parts = headers.flatMap { h ->
            h.split(Regex("""(?i)(?:^|,)\s*(?=Digest\s)""")).map { it.trim() }.filter { it.isNotEmpty() }
        }
        return parts.filter { it.startsWith("Digest", ignoreCase = true) }.map { part ->
            val params = Regex("""(\w+)\s*=\s*(?:"([^"]*)"|([^,\s]+))""").findAll(part.substring(6))
                .associate { m -> m.groupValues[1].lowercase() to m.groupValues[2].ifEmpty { m.groupValues[3] } }
            Challenge((params["algorithm"] ?: "MD5").uppercase(), params)
        }
    }

    private fun digestHeader(headers: List<String>, method: String, uri: String): String? {
        val (user, pass) = credentials ?: return null
        val challenges = parseChallenges(headers)
        // Préférence : SHA-256 puis MD5 (jamais les variantes "-sess")
        val chosen = challenges.firstOrNull { it.algorithm == "SHA-256" }
            ?: challenges.firstOrNull { it.algorithm == "MD5" }
            ?: return null
        val hashName = if (chosen.algorithm == "SHA-256") "SHA-256" else "MD5"
        fun h(s: String) = hash(hashName, s)

        val realm = chosen.params["realm"] ?: return null
        val nonce = chosen.params["nonce"] ?: return null
        val qop = chosen.params["qop"]?.split(',')?.map { it.trim() }?.firstOrNull { it.equals("auth", true) }
        val cnonce = randomHex(16)
        val nc = "00000001"
        val ha1 = h("$user:$realm:$pass")
        val ha2 = h("$method:$uri")
        val response = if (qop != null) h("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else h("$ha1:$nonce:$ha2")
        val sb = StringBuilder(
            "Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", " +
                "algorithm=${chosen.algorithm}, response=\"$response\""
        )
        if (qop != null) sb.append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
        chosen.params["opaque"]?.let { sb.append(", opaque=\"$it\"") }
        return sb.toString()
    }

    private fun hash(algorithm: String, s: String): String =
        MessageDigest.getInstance(algorithm).digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}
