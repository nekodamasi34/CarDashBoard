package dev.aw.cardashboard

const val HUD_STALE_DELAY_NS = 1_000_000_000L
const val HUD_RECOVERY_NS = 500_000_000L
// 0x201 is a periodic signal. Gaps over 250ms restart the recovery observation.
const val HUD_RECOVERY_MAX_GAP_NS = 250_000_000L

/** Owned by the communication controller; only decoded, newly received 0x201 frames enter here. */
class HudReception {
    private var last: Long? = null
    private var recoveryStart: Long? = null
    private var stale = false

    @Synchronized fun reset() { last = null; recoveryStart = null; stale = false }

    @Synchronized fun tick(now: Long): Boolean {
        last?.let {
            if (now - it >= HUD_STALE_NS + HUD_STALE_DELAY_NS) stale = true
            if (now - it > HUD_RECOVERY_MAX_GAP_NS) recoveryStart = null
        }
        return stale
    }

    @Synchronized fun received(now: Long): Boolean {
        val previous = last
        tick(now)
        if (previous != null && now <= previous) return stale
        if (stale) {
            if (recoveryStart == null) recoveryStart = now
            if (now - recoveryStart!! >= HUD_RECOVERY_NS) {
                stale = false
                recoveryStart = null
            }
        }
        last = now
        return stale
    }
}
