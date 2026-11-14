package dev.aw.cardashboard

data class CanFrame(val id: Int, val data: List<Int>, val receivedNs: Long) {
    val hex get() = data.joinToString(" ") { "%02X".format(it) }
}

/** ELM 11-bit headers, with or without spaces and optional single-nibble DLC. */
object CanFrameParser {
    fun parse(line: String, now: Long): CanFrame? {
        val s = line.trim().replace(Regex("\\s+"), "").uppercase()
        if (!s.matches(Regex("[0-9A-F]+")) || s.length < 3) return null
        val id = s.take(3).toIntOrNull(16) ?: return null
        if (id > 0x7ff) return null
        var payload = s.drop(3)
        if (payload.length % 2 == 1) {
            val dlc = payload.take(1).toInt(16)
            payload = payload.drop(1)
            if (dlc !in 0..8 || payload.length != dlc * 2) return null
        }
        if (payload.length !in 0..16 || payload.length % 2 != 0) return null
        return CanFrame(id, payload.chunked(2).map { it.toInt(16) }, now)
    }
    fun parseLines(raw: String, now: Long) = raw.split('\r', '\n').mapNotNull { parse(it, now) }
}

data class Rx8CanValues(val rpm: Float, val speedKmh: Double, val acceleratorPercent: Float = 0f)
/** Unverified on Japanese 2005 RX-8. Offsets are zero based; big endian. */
class Rx8CanDecoder(val rpmOffset: Int = 0, val rpmDivisor: Double = 4.0,
    val speedOffset: Int = 4, val speedBias: Double = 10000.0, val speedDivisor: Double = 100.0) {
    fun decode(frame: CanFrame): Rx8CanValues? {
        if (frame.id != 0x201 || frame.data.size != 8) return null
        fun word(offset: Int) = frame.data[offset] * 256 + frame.data[offset + 1]
        return Rx8CanValues((word(rpmOffset) / rpmDivisor).toFloat(),
            ((word(speedOffset) - speedBias) / speedDivisor).coerceAtLeast(0.0),
            (frame.data[6] / 2f).coerceIn(0f, 100f))
    }
}

enum class CommunicationMode { OBD, CAN }
object CommunicationHealth {
    fun retry(manualDisconnect: Boolean, mode: CommunicationMode, attempt: Int) =
        !manualDisconnect && mode == CommunicationMode.OBD && attempt < 3
    fun fresh(now: Long, last: Long?, limit: Long = 3_000_000_000L) = last != null && now - last in 0..limit
    fun protocol6(dp: String, dpn: String): Boolean =
        dp.uppercase().let { it.contains("CAN") && it.contains("11") && it.contains("500") && !it.contains('?') } &&
            dpn.trim().uppercase() in listOf("6", "A6")
}
