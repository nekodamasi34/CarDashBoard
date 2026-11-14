package dev.aw.cardashboard

enum class CommandSupport(val label: String) {
    SUPPORTED("対応確認済み"), UNSUPPORTED("未対応"), ABNORMAL("応答異常"), UNTESTED("未検証")
}
enum class ResponseKind { OK, INFORMATION, UNSUPPORTED, ERROR, TIMEOUT, EMPTY, INCOMPLETE, DISCONNECTED, UNEXPECTED }
data class CommandDiagnostic(
    val command: String, val raw: String = "", val support: CommandSupport = CommandSupport.UNTESTED,
    val kind: ResponseKind? = null, val timestampMs: Long = System.currentTimeMillis()
)
object AdapterDiagnostics {
    val commands = listOf("ATI", "AT@1", "ATDP", "ATDPN", "ATH1", "ATD1", "ATCRA201", "ATCSM1")
    fun payload(command: String, raw: String) = raw.substringBefore('>').split('\r', '\n')
        .map { it.trim() }.filter { it.isNotEmpty() && !it.equals(command, true) }.joinToString("\n")
    fun classify(command: String, raw: String, failure: ResponseKind? = null): CommandDiagnostic {
        val body = payload(command, raw)
        val lines = body.uppercase().lines()
        val kind = failure ?: when {
            !raw.trimEnd().endsWith('>') -> ResponseKind.INCOMPLETE
            body.isEmpty() -> ResponseKind.EMPTY
            lines.any { it.contains("ERROR") || it.contains("UNABLE") || it.contains("BUS BUSY") || it.contains("STOPPED") } -> ResponseKind.ERROR
            lines == listOf("?") -> ResponseKind.UNSUPPORTED
            lines == listOf("OK") -> if (command in listOf("ATI", "AT@1", "ATDP", "ATDPN")) ResponseKind.UNEXPECTED else ResponseKind.OK
            command == "ATI" && body.contains("ELM327", true) -> ResponseKind.INFORMATION
            command == "AT@1" && body != "OK" -> ResponseKind.INFORMATION
            command == "ATDP" && (body.contains("CAN", true) || body.contains("ISO", true) || body.contains("J1850", true) || body.equals("AUTO", true)) -> ResponseKind.INFORMATION
            command == "ATDPN" && body.uppercase().matches(Regex("A?[0-9A-C]")) -> ResponseKind.INFORMATION
            else -> ResponseKind.UNEXPECTED
        }
        val support = when (kind) {
            ResponseKind.OK, ResponseKind.INFORMATION -> CommandSupport.SUPPORTED
            ResponseKind.UNSUPPORTED -> CommandSupport.UNSUPPORTED
            else -> CommandSupport.ABNORMAL
        }
        return CommandDiagnostic(command, raw, support, kind)
    }
}

data class AdapterDiagnosticView(
    val running: Boolean = false, val adapterName: String = "未接続",
    val ecuVerified: Boolean = false, val message: String = "未検証",
    val results: List<CommandDiagnostic> = AdapterDiagnostics.commands.map { CommandDiagnostic(it) }
) {
    fun record(result: CommandDiagnostic) = copy(results = results.filterNot { it.command == result.command } + result)
    fun value(command: String) = results.firstOrNull { it.command == command && it.support == CommandSupport.SUPPORTED }
        ?.let { AdapterDiagnostics.payload(command, it.raw) } ?: "未検証"
    fun summary() = "ELM327: ${value("ATI")}\nProtocol: ${value("ATDP")} / ${value("ATDPN")}\n" +
        results.joinToString("\n") { "${it.command}: ${it.support.label} (${it.kind ?: "未実施"})" } +
        "\nATMA: 未検証\nSilent CAN monitoring: 未検証\nCAN monitoring: 互換性調査が必要\n$message"
    // Shared reports deliberately exclude the user-assigned Bluetooth name and address.
    fun csv(): String {
        fun cell(s: String) = "\"${s.replace("\"", "\"\"")}\""
        return "timestamp_ms,command,status,result,raw\r\n" + results.joinToString("\r\n") {
            listOf(it.timestampMs.toString(), it.command, it.support.label, it.kind?.name.orEmpty(), redact(it.raw)).joinToString(",", transform = ::cell)
        }
    }
    fun sharedReport() = redact(summary() + "\n\n" + results.joinToString("\n") { "${it.command}: ${it.raw}" })
    private fun redact(raw: String): String = raw.replace(Regex("(?i)([0-9a-f]{2}[:-]){5}[0-9a-f]{2}"), "[Bluetooth address omitted]")
        .let { if (adapterName != "未接続" && adapterName.isNotBlank()) it.replace(adapterName, "[adapter name omitted]") else it }
}

enum class MonitorPhase { OBD, SETUP, BLOCKED, MONITORING, STOPPING, RECONNECTING, DISCONNECTED }
/** Evidence is supplied only by reviewed application code, never by a UI checkbox or version string. */
class CanMonitorSession {
    var phase = MonitorPhase.OBD
        private set
    fun setup() { check(phase == MonitorPhase.OBD); phase = MonitorPhase.SETUP }
    fun authorize(protocol6: Boolean, silent: CommandSupport, filter: CommandSupport, trustedEvidence: Boolean, experimentalConsent: Boolean = false): Boolean {
        check(phase == MonitorPhase.SETUP)
        val allowed = protocol6 && filter == CommandSupport.SUPPORTED && (trustedEvidence || experimentalConsent) &&
            silent in listOf(CommandSupport.SUPPORTED, CommandSupport.UNSUPPORTED)
        phase = if (allowed) MonitorPhase.MONITORING else MonitorPhase.BLOCKED
        return allowed
    }
    fun stop() { check(phase == MonitorPhase.MONITORING); phase = MonitorPhase.STOPPING }
    fun reconnect() { phase = MonitorPhase.RECONNECTING }
    fun restored() { check(phase == MonitorPhase.RECONNECTING); phase = MonitorPhase.OBD }
    fun disconnected() { phase = MonitorPhase.DISCONNECTED }
    val permitsPid get() = phase == MonitorPhase.OBD
}
