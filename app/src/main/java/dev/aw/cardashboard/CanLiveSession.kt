package dev.aw.cardashboard

/** Physical bus-analyzer evidence must be reviewed and recorded here before enabling continuous use.
 * ATCSM1 OK or ATMA reception alone is NOT evidence of no CAN transmission/ACK.
 * Record adapter/firmware, analyzer trace, ignition conditions and reviewer in the evidence field.
 */
object CanLiveSafety {
    private const val VERIFIED_NO_TRANSMIT_EVIDENCE = ""
    val continuousAllowed get() = VERIFIED_NO_TRANSMIT_EVIDENCE.isNotBlank()
    val validationSeconds = listOf(5, 15, 30)
    fun permits(seconds: Int?) = if (seconds == null) continuousAllowed else seconds in validationSeconds
}

/** User-owned live session, independent of benchmark capture and of Compose lifetime. */
class CanLiveSession(val limitSeconds: Int?) {
    init { require(CanLiveSafety.permits(limitSeconds)) }
    @Volatile var requested = true
        private set
    private var startedNs: Long? = null
    fun started(now: Long) { check(requested); startedNs = now }
    fun expired(now: Long): Boolean = startedNs?.let { start ->
        limitSeconds?.let { now - start >= it * 1_000_000_000L } ?: false
    } ?: false
    fun stop() { requested = false }
    fun receiving(now: Long) = requested && !expired(now)
}
