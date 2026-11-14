package dev.aw.cardashboard

import java.time.Instant
import kotlin.math.ceil
import kotlin.math.sqrt

data class RequestSample(val pid: Pid, val sentNs: Long, val completedNs: Long,
    val success: Boolean, val timeout: Boolean = false)
data class BenchmarkStats(
    val seconds: Double = 0.0, val rpmHz: Double = 0.0, val speedHz: Double = 0.0,
    val coolantHz: Double = 0.0, val commandsHz: Double = 0.0,
    val latestMs: Double? = null, val meanMs: Double? = null, val medianMs: Double? = null,
    val p95Ms: Double? = null, val maxMs: Double? = null, val jitterMs: Double? = null,
    val success: Int = 0, val failed: Int = 0, val timeouts: Int = 0
) {
    val successPercent: Double? get() = (success + failed).takeIf { it > 0 }?.let { success * 100.0 / it }
}
object BenchmarkStatistics {
    // Frequencies are successful completions / actual elapsed observation time.
    // Latency includes failed RPM attempts as well, to avoid hiding slow failures.
    fun calculate(samples: List<RequestSample>, start: Long, end: Long): BenchmarkStats {
        val seconds = (end - start).coerceAtLeast(0) / 1e9
        val eligible = samples.filter { it.sentNs >= start && it.completedNs <= end }
        fun hz(pid: Pid?) = if (seconds <= 0) 0.0 else
            eligible.count { if (pid == null) true else it.pid == pid && it.success } / seconds
        val rpm = eligible.filter { it.pid == Pid.RPM }
        val times = rpm.map { (it.completedNs - it.sentNs) / 1e6 }.sorted()
        val intervals = rpm.filter { it.success }.zipWithNext { a, b -> (b.completedNs - a.completedNs) / 1e6 }
        val meanInterval = intervals.takeIf { it.isNotEmpty() }?.average()
        return BenchmarkStats(seconds, hz(Pid.RPM), hz(Pid.SPEED), hz(Pid.COOLANT), hz(null),
            rpm.lastOrNull()?.let { (it.completedNs - it.sentNs) / 1e6 },
            times.takeIf { it.isNotEmpty() }?.average(),
            times.takeIf { it.isNotEmpty() }?.let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 },
            times.takeIf { it.isNotEmpty() }?.let { it[ceil(it.size * .95).toInt() - 1] },
            times.lastOrNull(), meanInterval?.let { avg -> sqrt(intervals.sumOf { (it - avg) * (it - avg) } / intervals.size) },
            eligible.count { it.success }, eligible.count { !it.success }, eligible.count { it.timeout })
    }
}
data class BenchmarkResult(val date: String, val mode: PollingMode, val timing: AdaptiveTiming,
    val stats: BenchmarkStats, val reason: String) {
    fun csv(): String {
        fun cell(value: Any?) = "\"" + (value?.toString() ?: "").replace("\"", "\"\"") + "\""
        return "date,mode,atat,duration_s,rpm_hz,mean_ms,p95_ms,success_percent,timeouts,success,failed,reason\r\n" +
            listOf(date, mode.name, timing.name, stats.seconds, stats.rpmHz, stats.meanMs,
                stats.p95Ms, stats.successPercent, stats.timeouts, stats.success, stats.failed, reason)
                .joinToString(",") { cell(it) } + "\r\n"
    }
}
data class BenchmarkView(val live: BenchmarkStats = BenchmarkStats(), val running: Boolean = false,
    val measured: BenchmarkStats = BenchmarkStats(), val result: BenchmarkResult? = null)

/** Recording is O(1); copying/sorting is done by the 4 Hz statistics worker, never per command. */
class BenchmarkManager {
    private val recent = ArrayDeque<RequestSample>()
    private val measured = ArrayList<RequestSample>()
    private var liveStart: Long? = null
    private var runStart: Long? = null
    private var date = ""
    private var mode = PollingMode.RPM_PRIORITY
    private var timing = AdaptiveTiming.ATAT1
    private var result: BenchmarkResult? = null
    @Synchronized fun isRunning() = runStart != null
    @Synchronized fun start(now: Long, selected: PollingMode, applied: AdaptiveTiming) {
        if (runStart != null) return
        runStart = now; date = Instant.now().toString(); mode = selected; timing = applied
        measured.clear()
    }
    @Synchronized fun connected(now: Long) { recent.clear(); liveStart = now }
    @Synchronized fun record(sample: RequestSample) {
        recent.addLast(sample)
        while (recent.isNotEmpty() && recent.first().completedNs < sample.completedNs - 5_000_000_000L) recent.removeFirst()
        runStart?.let { if (sample.sentNs >= it && sample.completedNs <= it + 30_000_000_000L) measured.add(sample) }
    }
    @Synchronized fun stop(now: Long, reason: String = "手動停止") {
        val start = runStart ?: return
        val end = minOf(now, start + 30_000_000_000L)
        result = BenchmarkResult(date, mode, timing, BenchmarkStatistics.calculate(measured.toList(), start, end), reason)
        runStart = null
    }
    @Synchronized fun disconnected(now: Long) {
        stop(now, "通信切断"); recent.clear(); liveStart = null
    }
    @Synchronized fun snapshot(now: Long): BenchmarkView {
        runStart?.let { if (now >= it + 30_000_000_000L) stop(it + 30_000_000_000L, "30秒完了") }
        val live = liveStart?.let { BenchmarkStatistics.calculate(recent.toList(), maxOf(it, now - 5_000_000_000L), now) } ?: BenchmarkStats()
        val running = runStart?.let { BenchmarkStatistics.calculate(measured.toList(), it, now) } ?: BenchmarkStats()
        return BenchmarkView(live, runStart != null, running, result)
    }
}
