package dev.aw.cardashboard

/** Called only by the single command loop. No catch-up bursts after a slow reply. */
enum class PollingMode(val label: String, val speedNs: Long, val coolantNs: Long) {
    RPM_PRIORITY("RPM優先", 500_000_000, 2_000_000_000),
    BALANCED("バランス", 250_000_000, 1_000_000_000),
    RPM_ONLY("RPM専用", Long.MAX_VALUE, Long.MAX_VALUE)
}
enum class AdaptiveTiming { ATAT1, ATAT2 }
enum class Pid(val command: String) { RPM("010C"), SPEED("010D"), COOLANT("0105") }
class PollingScheduler {
    private var mode: PollingMode? = null
    private var speedAt = 0L
    private var coolantAt = 0L
    private var rpmNext = true
    fun next(now: Long, selected: PollingMode): Pid {
        if (mode != selected) {
            mode = selected
            speedAt = now
            coolantAt = now
            rpmNext = true
        }
        if (rpmNext || selected == PollingMode.RPM_ONLY) {
            rpmNext = false
            return Pid.RPM
        }
        val pid = when {
            now >= speedAt -> { speedAt = now + selected.speedNs; Pid.SPEED }
            now >= coolantAt -> { coolantAt = now + selected.coolantNs; Pid.COOLANT }
            else -> Pid.RPM
        }
        rpmNext = pid != Pid.RPM
        return pid
    }
}
