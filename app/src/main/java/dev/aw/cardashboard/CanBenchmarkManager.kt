package dev.aw.cardashboard

import java.time.Instant
import kotlin.math.ceil
import kotlin.math.sqrt

data class CanStats(val seconds: Double = 0.0, val frames: Long = 0, val invalid: Long = 0,
    val errors: Long = 0, val hz: Double = 0.0, val rpmChangedHz: Double = 0.0,
    val latestMs: Double? = null, val meanMs: Double? = null, val minMs: Double? = null,
    val maxMs: Double? = null, val p95Ms: Double? = null, val stddevMs: Double? = null)
data class CanResult(val date: String, val stats: CanStats, val reason: String) {
    fun csv() = "date,communication,can_id,duration_s,frames,arrival_hz,rpm_changed_hz,mean_interval_ms,p95_interval_ms,max_interval_ms,invalid,errors,reason\r\n" +
        listOf(date, "CAN", "0x201", stats.seconds, stats.frames, stats.hz, stats.rpmChangedHz,
            stats.meanMs, stats.p95Ms, stats.maxMs, stats.invalid, stats.errors, reason)
            .joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" } + "\r\n"
}
data class CanView(val live: CanStats = CanStats(), val running: Boolean = false,
    val result: CanResult? = null, val latest: CanFrame? = null, val values: Rx8CanValues? = null,
    val raw: List<String> = emptyList())

/** Fixed histogram (1 ms bins, overflow at 60s), O(1) receive cost and bounded memory. */
private class CanAccumulator(val start: Long) {
    var frames = 0L; var invalid = 0L; var errors = 0L; var changes = 0L
    var last: Long? = null; var rpm: Float? = null
    var latest: Double? = null; var min: Double? = null; var max: Double? = null
    var sum = 0.0; var square = 0.0
    val histogram = LongArray(60001)
    fun record(f: CanFrame, value: Float?) {
        last?.let {
            val ms = (f.receivedNs - it).coerceAtLeast(0) / 1e6
            latest = ms; min = minOf(min ?: ms, ms); max = maxOf(max ?: ms, ms)
            sum += ms; square += ms * ms; histogram[ceil(ms).toInt().coerceIn(0, 60000)]++
        }
        if (rpm != null && value != null && rpm != value) changes++
        rpm = value; frames++; last = f.receivedNs
    }
    fun stats(now: Long): CanStats {
        val seconds = (now - start).coerceAtLeast(0) / 1e9
        val n = frames - 1
        val mean = if (n > 0) sum / n else null
        var p95: Double? = null
        if (n > 0) {
            var count = 0L
            val rank = ceil(n * .95).toLong()
            for (i in histogram.indices) { count += histogram[i]; if (count >= rank) { p95 = i.toDouble(); break } }
        }
        return CanStats(seconds, frames, invalid, errors, if (seconds > 0) frames / seconds else 0.0,
            if (seconds > 0) changes / seconds else 0.0, latest, mean, min, max, p95,
            mean?.let { sqrt((square / n - it * it).coerceAtLeast(0.0)) })
    }
}

class CanBenchmarkManager {
    private var live: CanAccumulator? = null
    private var run: CanAccumulator? = null
    private var date = ""
    private var result: CanResult? = null
    private var latest: CanFrame? = null
    private var values: Rx8CanValues? = null
    private val raw = ArrayDeque<String>()
    private val decoder = Rx8CanDecoder()
    @Synchronized fun connected(now: Long) { live = CanAccumulator(now); latest = null; values = null; raw.clear() }
    @Synchronized fun start(now: Long) { if (live != null && run == null) { run = CanAccumulator(now); date = Instant.now().toString() } }
    private fun eligible(now: Long) = run?.takeIf { now >= it.start && now <= it.start + 30_000_000_000L }
    @Synchronized fun record(frame: CanFrame) {
        if (frame.id != 0x201) return
        latest = frame; values = decoder.decode(frame)
        raw.addLast("${frame.receivedNs} 201 [${frame.data.size}] ${frame.hex}")
        while (raw.size > 35) raw.removeFirst()
        live?.record(frame, values?.rpm); eligible(frame.receivedNs)?.record(frame, values?.rpm)
    }
    @Synchronized fun invalid(now: Long) { live?.let { it.invalid++ }; eligible(now)?.let { it.invalid++ } }
    @Synchronized fun error(now: Long) { live?.let { it.errors++ }; eligible(now)?.let { it.errors++ } }
    @Synchronized fun stop(now: Long, reason: String = "手動停止") {
        run?.let { result = CanResult(date, it.stats(minOf(now, it.start + 30_000_000_000L)), reason) }; run = null
    }
    @Synchronized fun disconnected(now: Long) { stop(now, "監視終了／通信切断"); live = null; latest = null; values = null; raw.clear() }
    @Synchronized fun snapshot(now: Long): CanView {
        run?.let { if (now >= it.start + 30_000_000_000L) stop(it.start + 30_000_000_000L, "30秒完了") }
        return CanView(live?.stats(now) ?: CanStats(), run != null, result, latest, values, raw.toList())
    }
}
