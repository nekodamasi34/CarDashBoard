package dev.aw.cardashboard

/** Physical bus-analyzer evidence must be reviewed and recorded here before enabling continuous use.
 * ATCSM1 OK or ATMA reception alone is NOT evidence of no CAN transmission/ACK.
 * Record adapter/firmware, analyzer trace, ignition conditions and reviewer in the evidence field.
 */
object CanLiveSafety {
    private const val VERIFIED_NO_TRANSMIT_EVIDENCE = ""
    val continuousAllowed get() = VERIFIED_NO_TRANSMIT_EVIDENCE.isNotBlank()
    val validationSeconds = listOf(5, 15, 30)
    fun permits(seconds: Int?, experimentalUnlimitedConsent: Boolean = false) =
        if (seconds == null) continuousAllowed || experimentalUnlimitedConsent else seconds in validationSeconds
}

/** User-owned live session, independent of benchmark capture and of Compose lifetime. */
class CanLiveSession(val limitSeconds: Int?, val experimentalUnlimitedConsent: Boolean = false) {
    init { require(CanLiveSafety.permits(limitSeconds, experimentalUnlimitedConsent)) }
    private val lifetime = CanReceiveLifetime(limitSeconds)
    @Volatile var requested = true
        private set
    fun started(now: Long) { check(requested); lifetime.start(now) }
    fun expired(now: Long) = lifetime.expired(now)
    fun stop() { requested = false }
    fun receiving(now: Long) = requested && lifetime.receiving(now)
}

/** Timing only; authorization remains exclusively in CanLiveSession/CanLiveSafety.
 * A null deadline never expires, including periods with no incoming frames.
 */
internal class CanReceiveLifetime(private val seconds: Int?) {
    private var startedNs: Long? = null
    fun start(now: Long) { check(startedNs == null); startedNs = now }
    fun expired(now: Long) = startedNs?.let { start ->
        seconds?.let { now - start >= it * 1_000_000_000L } ?: false
    } ?: false
    fun receiving(now: Long) = startedNs != null && !expired(now)
}
