package dev.aw.cardashboard

import kotlinx.coroutines.withTimeout

/** Monotonic deadline, independent of rendering and frame arrival. */
class CanExperiment(val startNs: Long) {
    fun expired(now: Long) = now - startNs >= DURATION_NS
    fun remaining(now: Long) = ((DURATION_NS - (now - startNs)).coerceAtLeast(0) / 1e9)
    companion object { const val DURATION_NS = 5_000_000_000L }
}

object MonitorLine {
    fun error(line: String, stopping: Boolean): String? {
        val s = line.trim().uppercase()
        return when {
            s == "?" -> "ATMA未対応 (?)"
            "BUFFER FULL" in s -> "BUFFER FULL"
            "CAN ERROR" in s -> "CAN ERROR"
            "DATA ERROR" in s -> "DATA ERROR（ATCAF0適用後の受信異常・値不採用）: $s"
            "STOPPED" in s -> if (stopping) null else "予期しないSTOPPED"
            listOf("ERROR", "UNABLE", "BUS BUSY", "NO DATA").any { it in s } -> s
            else -> null
        }
    }
    fun ignored(line: String, stopping: Boolean) = line.isBlank() || line.trim().equals("ATMA", true) ||
        (stopping && line.trim().equals("STOPPED", true))
}

data class ExperimentInfo(val elm: String = "未取得", val protocol: String = "未取得",
    val silent: String = "未検証", val filter: String = "未検証", val atma: String = "未検証")

data class RawCanRow(val ns: Long, val raw: String, val frame: CanFrame?) {
    fun cells(): List<Any?> = listOf(ns, frame?.id?.toString(16), frame?.data?.size) +
        (0..7).map { frame?.data?.getOrNull(it)?.let { b -> "%02X".format(b) } } + listOf(raw, frame != null)
}
fun csvCells(cells: List<Any?>) = cells.joinToString(",") { "\"${(it?.toString() ?: "").replace("\"", "\"\"")}\"" }

object MonitorStopProtocol {
    suspend fun finish(alreadyPrompt: Boolean, timeoutMs: Long = 3000,
        write: suspend (ByteArray) -> Unit, prompt: suspend () -> Unit) {
        if (alreadyPrompt) return
        withTimeout(timeoutMs) {
            write(byteArrayOf(13)) // Exactly one character, discarded by ELM327 while monitoring.
            prompt()
        }
    }
}
