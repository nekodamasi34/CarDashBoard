package dev.aw.cardashboard

import java.util.Locale

/** Pure parsing functions. This file is also easy to unit-test on the JVM. */
object ObdParser {
    /**
     * Expected result after sending e.g. "010C": 41 0C AA BB.
     * Ignores spaces, line endings, command echo and CAN header prefixes.
     */
    fun payload(raw: String, pid: String, bytes: Int): IntArray? {
        require(bytes in 1..8)
        require(pid.matches(Regex("[0-9A-Fa-f]{2}")))
        if (raw.uppercase(Locale.US).split('\r', '\n', '>').any {
            it.trim() in listOf("NO DATA", "STOPPED", "UNABLE TO CONNECT", "BUS ERROR", "CAN ERROR", "BUFFER FULL", "ERROR", "?")
        }) return null
        val marker = "41" + pid.uppercase(Locale.US)
        for (line in raw.uppercase(Locale.US).split('\r', '\n', '>')) {
            val hex = line.replace(Regex("\\s+"), "")
            if (!hex.matches(Regex("[0-9A-F]+"))) continue
            // Headerless, or 11/29-bit CAN header plus length. Do not join lines
            // or remove invalid characters to manufacture a valid response.
            for (offset in listOf(0, 5, 10)) {
                if (hex.length != offset + 4 + bytes * 2 || !hex.startsWith(marker, offset)) continue
                if (offset > 0) {
                    val headerLength = offset - 2
                    if (headerLength == 3 && hex.take(3).toInt(16) > 0x7FF) continue
                    if (headerLength == 8 && hex.take(8).toLong(16) > 0x1FFFFFFF) continue
                    if (hex.substring(headerLength, offset).toInt(16) != bytes + 2) continue
                }
                val data = hex.substring(offset + 4)
                return IntArray(bytes) { data.substring(it * 2, it * 2 + 2).toInt(16) }
            }
        }
        return null
    }

    fun failureReason(raw: String): String {
        val text = raw.uppercase(Locale.US)
        return listOf("UNABLE TO CONNECT", "NO DATA", "STOPPED", "BUS ERROR",
            "CAN ERROR", "BUFFER FULL", "ERROR", "?").firstOrNull { text.contains(it) }
            ?: if (text.replace(">", "").isBlank()) "空応答"
            else if (text.contains("SEARCHING")) "SEARCHING（ECU応答なし）"
            else "不正・不完全な応答／対象PIDなし"
    }

    fun rpm(raw: String): Float? = payload(raw, "0C", 2)?.let {
        ((it[0] * 256) + it[1]) / 4.0f
    }

    fun speedKmh(raw: String): Int? = payload(raw, "0D", 1)?.get(0)

    fun coolantC(raw: String): Int? = payload(raw, "05", 1)?.let { it[0] - 40 }
}
