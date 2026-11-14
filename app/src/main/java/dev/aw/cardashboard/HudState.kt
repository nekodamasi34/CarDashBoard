package dev.aw.cardashboard

const val HUD_MAX_RPM = 10000f
const val HUD_REDLINE_RPM = 8500f
const val HUD_STALE_NS = 3_000_000_000L

enum class HudStatus(val label: String) {
    OFFLINE("Bluetooth未接続"), WAITING("CAN未受信"), LIVE("LIVE"),
    DEMO("DEMO · 実測ではありません"), STALE("STALE · データが古い"), ERROR("通信エラー")
}

data class HudState(
    val source: String, val status: HudStatus, val values: Rx8CanValues? = null,
    val lastUpdatedNs: Long? = null, val ageNs: Long? = null, val measuredHz: Double? = null
)

/** Monotonic receive time only: animation frames never update telemetry or measured Hz. */
fun DashboardState.toHudState(nowNs: Long): HudState {
    if (demo) return HudState("DEMO", HudStatus.DEMO, demoHud, demoUpdatedNs,
        demoUpdatedNs?.let { (nowNs - it).coerceAtLeast(0) })
    val source = if (communication == CommunicationMode.CAN) "CAN" else "OBD2"
    if (communicationError != null || (communication == CommunicationMode.CAN && can.live.errors > 0))
        return HudState(source, HudStatus.ERROR)
    if (!bluetoothConnected) return HudState(source, HudStatus.OFFLINE)
    // OBD2 remains available on the original meter; this HUD requires CAN 0x201.
    val frame = can.latest?.takeIf { communication == CommunicationMode.CAN }
    val values = frame?.let { Rx8CanDecoder().decode(it) }
    if (values == null) return HudState(source, HudStatus.WAITING)
    val age = (nowNs - frame.receivedNs).coerceAtLeast(0)
    return HudState("CAN", if (CommunicationHealth.fresh(nowNs, frame.receivedNs, HUD_STALE_NS))
        HudStatus.LIVE else HudStatus.STALE, values, frame.receivedNs, age, can.live.hz)
}
