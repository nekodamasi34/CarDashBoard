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
        val hex = raw.uppercase(Locale.US).replace(Regex("[^0-9A-F]"), "")
        val pattern = Regex("41" + pid.uppercase(Locale.US) + "([0-9A-F]{" + (bytes * 2) + "})")
        val payload = pattern.find(hex)?.groupValues?.get(1) ?: return null
        return IntArray(bytes) { index ->
            payload.substring(index * 2, index * 2 + 2).toInt(16)
        }
    }

    fun rpm(raw: String): Float? = payload(raw, "0C", 2)?.let {
        ((it[0] * 256) + it[1]) / 4.0f
    }

    fun speedKmh(raw: String): Int? = payload(raw, "0D", 1)?.get(0)

    fun coolantC(raw: String): Int? = payload(raw, "05", 1)?.let { it[0] - 40 }
}
