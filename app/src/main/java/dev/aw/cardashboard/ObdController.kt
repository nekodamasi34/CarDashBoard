package dev.aw.cardashboard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.io.InputStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import kotlin.math.sin

data class PairedDevice(val name: String, val address: String)
data class DashboardState(
    val diagnostic: AdapterDiagnosticView = AdapterDiagnosticView(),
    val communication: CommunicationMode = CommunicationMode.OBD,
    val can: CanView = CanView(),
    val canLiveRequested: Boolean = false,
    val mode: PollingMode = PollingMode.RPM_PRIORITY,
    val timing: AdaptiveTiming = AdaptiveTiming.ATAT1,
    val appliedMode: PollingMode? = null,
    val timingRevision: Long = 0,
    val appliedTiming: AdaptiveTiming? = null,
    val settingError: String? = null,
    val benchmark: BenchmarkView = BenchmarkView(),
    val status: String = "未接続",
    val connected: Boolean = false,
    val bluetoothConnected: Boolean = false,
    val busy: Boolean = false,
    val demo: Boolean = false,
    val demoHud: Rx8CanValues? = null,
    val demoUpdatedNs: Long? = null,
    val communicationError: String? = null,
    val devices: List<PairedDevice> = emptyList(),
    val rpm: Float? = null,
    val speedKmh: Int? = null,
    val coolantC: Int? = null,
    val rpmHz: Float? = null,
    val rpmResponseMs: Float? = null,
    val rpmAverageMs: Float? = null,
    val logs: List<String> = listOf("アプリ起動。まずはデモで動作確認！")
)

class ObdController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state
    private val lock = Any()
    private val benchmarks = BenchmarkManager()
    private val canBenchmarks = CanBenchmarkManager()
    @Volatile private var liveSession: CanLiveSession? = null
    fun startCanLive(address: String, seconds: Int?) {
        if (state.value.busy || monitoring || state.value.diagnostic.running || diagnosticRequested) return
        val request = CanLiveSession(seconds)
        connect(address, request)
    }
    fun stopCanLive() {
        liveSession?.stop()
        disconnect()
    }
    @Volatile private var experimentConsent = false
    @Volatile private var stoppingMonitor = false
    fun startCanExperiment() {
        if (!state.value.bluetoothConnected || !state.value.diagnostic.ecuVerified || state.value.busy || state.value.diagnostic.running || monitoring || state.value.communication != CommunicationMode.OBD) return
        experimentConsent = true
        selectCommunication(CommunicationMode.CAN)
    }
    fun stopCanExperiment() { experimentConsent = false; selectCommunication(CommunicationMode.OBD) }
    @Volatile private var monitoring = false
    @Volatile private var manualStop = false
    @Volatile private var monitorFailure: String? = null
    @Volatile private var diagnosticRequested = false
    @Volatile private var diagnosticStop = false
    @Volatile private var partialReply = ""
    private val monitorSession = CanMonitorSession()
    private var retiringOperation: Job? = null
    fun startDiagnostics() {
        if (!state.value.bluetoothConnected || liveSession != null || monitoring || state.value.diagnostic.running) return
        diagnosticStop = false
        diagnosticRequested = true
        _state.update { it.copy(communication = CommunicationMode.OBD,
            diagnostic = AdapterDiagnosticView(running = true, adapterName = it.diagnostic.adapterName, message = "診断準備中")) }
    }
    fun stopDiagnostics() { diagnosticStop = true }
    private val lastPid = mutableMapOf<Pid, Long>()
    fun selectCommunication(mode: CommunicationMode) {
        if (liveSession != null || diagnosticRequested || state.value.diagnostic.running) return
        _state.update { it.copy(communication = mode) }
    }
    private class ModeSwitch : IOException("通信方式切替: Socket再初期化")
    init {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(250)
                synchronized(lock) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    val can = canBenchmarks.snapshot(now)
                    if (monitoring) {
                        val alive = !manualStop && CommunicationHealth.fresh(now, can.latest?.receivedNs)
                        _state.update { it.copy(can = can, connected = alive, busy = false,
                            rpm = if (alive) can.values?.rpm else null,
                            speedKmh = if (alive) can.values?.speedKmh?.toInt() else null,
                            coolantC = null, rpmHz = if (alive) can.live.hz.toFloat() else null,
                            rpmResponseMs = null, rpmAverageMs = null,
                            status = if (alive) "CAN監視中（参考値）" else "CAN受信待ち／データ無効") }
                    } else {
                    val view = benchmarks.snapshot(now)
                    if (!_state.value.demo) _state.update { it.copy(
                        rpm = if (CommunicationHealth.fresh(now, lastPid[Pid.RPM])) it.rpm else null,
                        speedKmh = if (CommunicationHealth.fresh(now, lastPid[Pid.SPEED], 5_000_000_000L)) it.speedKmh else null,
                        coolantC = if (CommunicationHealth.fresh(now, lastPid[Pid.COOLANT], 6_000_000_000L)) it.coolantC else null,
                        connected = it.connected && CommunicationHealth.fresh(now, lastPid.values.maxOrNull())) }
                    _state.update { it.copy(benchmark = view, can = can,
                        rpmHz = if (it.connected) view.live.rpmHz.toFloat() else null,
                        rpmResponseMs = view.live.latestMs?.toFloat(),
                        rpmAverageMs = view.live.meanMs?.toFloat()) }
                    }
                }
            }
        }
    }
    fun selectMode(mode: PollingMode) = synchronized(lock) {
        if (!benchmarks.isRunning()) {
            _state.update { it.copy(mode = mode, appliedMode = null) }
        }
    }
    fun selectTiming(timing: AdaptiveTiming) = synchronized(lock) {
        if (!benchmarks.isRunning()) _state.update { it.copy(timing = timing, timingRevision = it.timingRevision + 1, appliedTiming = null, settingError = null) }
    }
    fun startBenchmark() = synchronized(lock) {
        val value = _state.value
        if (value.communication == CommunicationMode.OBD && !monitoring && value.connected && !value.demo && value.appliedTiming == value.timing && value.appliedMode == value.mode)
            benchmarks.start(SystemClock.elapsedRealtimeNanos(), value.mode, value.timing)
    }
    fun stopBenchmark() = synchronized(lock) { benchmarks.stop(SystemClock.elapsedRealtimeNanos()) }
    private fun fresh(old: DashboardState, status: String = "未接続", busy: Boolean = false) =
        old.resetConnection(benchmarks.snapshot(SystemClock.elapsedRealtimeNanos())).copy(status = status, busy = busy,
            can = canBenchmarks.snapshot(SystemClock.elapsedRealtimeNanos()))
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.JAPAN)

    private var socket: BluetoothSocket? = null
    private var operation: Job? = null
    @Volatile private var session = 0
    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private fun hasConnectPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun log(message: String) {
        val line = "${LocalTime.now().format(timeFormat)}  $message"
        _state.update { it.copy(logs = (it.logs + line).takeLast(35)) }
    }

    // Session check and state update are atomic with respect to manual disconnect.
    private fun publish(id: Int, change: (DashboardState) -> DashboardState) = synchronized(lock) {
        if (id == session) _state.update(change)
    }
    private fun sessionLog(id: Int, message: String) = synchronized(lock) {
        if (id == session) log(message)
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        try {
            check(hasConnectPermission()) { "Bluetooth接続権限が必要です" }
            val bluetooth = adapter ?: error("この端末にBluetoothがありません")
            check(bluetooth.isEnabled) { "BluetoothをONにしてください" }
            val devices = bluetooth.bondedDevices.orEmpty().map {
                PairedDevice(it.name ?: "名前なし", it.address)
            }.sortedWith(compareBy<PairedDevice> { !it.name.contains("OBD", true) }.thenBy { it.name })
            _state.update { it.copy(devices = devices) }
            log("ペアリング済みデバイス: ${devices.size}件")
        } catch (e: Exception) { log("Bluetooth: ${e.message}") }
    }

    private fun stopCurrent() = synchronized(lock) {
        experimentConsent = false
        liveSession?.stop()
        liveSession = null
        diagnosticRequested = false
        diagnosticStop = true
        monitorSession.disconnected()
        _state.update { it.copy(diagnostic = it.diagnostic.copy(running = false, ecuVerified = false)) }
        benchmarks.disconnected(SystemClock.elapsedRealtimeNanos())
        canBenchmarks.disconnected(SystemClock.elapsedRealtimeNanos())
        lastPid.clear()
        monitoring = false
        session++
        retiringOperation = operation
        operation?.cancel()
        operation = null
        try { socket?.close() } catch (_: Exception) { }
        socket = null
    }

    fun disconnect() {
        if (monitoring) {
            manualStop = true
            _state.update { it.copy(connected = false, bluetoothConnected = false,
                can = it.can.copy(latest = null, values = null),
                rpm = null, speedKmh = null, status = "監視停止中…") }
            return
        }
        stopCurrent()
        _state.update { fresh(it) }
        log("手動切断（再接続しません）")
    }

    fun startDemo() {
        if (monitoring) { log("CAN監視を停止してからデモを開始してください"); return }
        stopCurrent()
        val id = session
        _state.update { fresh(it).copy(status = "デモモード（実車ではない）", demo = true, rpm = 1200f, speedKmh = 0, coolantC = 80) }
        log("デモモード開始")
        operation = scope.launch {
            val start = SystemClock.elapsedRealtime()
            while (isActive && id == session) {
                val seconds = (SystemClock.elapsedRealtime() - start) / 1000.0
                val rpm = 2700 + 1900 * sin(seconds * 0.85) + 370 * sin(seconds * 2.2)
                publish(id) { it.copy(rpm = rpm.toFloat().coerceIn(700f, 7500f),
                    speedKmh = (50 + 32 * sin(seconds * 0.24)).toInt().coerceAtLeast(0),
                    coolantC = 83 + ((seconds / 7) % 11).toInt(),
                    demoHud = Rx8CanValues((4800 + 4400 * sin(seconds * .55)).toFloat(),
                        (50 + 32 * sin(seconds * .24)).coerceAtLeast(0.0),
                        (50 + 50 * sin(seconds * .85)).toFloat()),
                    demoUpdatedNs = SystemClock.elapsedRealtimeNanos()) }
                delay(50)
            }
        }
    }

    /** Only read-only Mode 01 requests and ELM adapter configuration commands. */
    fun connect(address: String, liveRequest: CanLiveSession? = null) {
        stopCurrent()
        liveSession = liveRequest
        _state.update { it.copy(communication = if (liveRequest != null) CommunicationMode.CAN else CommunicationMode.OBD) }
        manualStop = false
        val id = session
        if (!hasConnectPermission()) {
            _state.update { fresh(it, "接続エラー: Bluetooth接続権限が必要です").copy(communicationError = "Bluetooth接続権限が必要です") }
            log("Bluetooth接続権限が必要です")
            return
        }
        _state.update { fresh(it, "Bluetooth接続中…", true).copy(canLiveRequested = liveRequest != null) }
        _state.update { it.copy(diagnostic = AdapterDiagnosticView(adapterName = it.devices.firstOrNull { d -> d.address == address }?.name ?: "選択したアダプター")) }
        log("接続開始")
        val previousOperation = retiringOperation
        retiringOperation = null
        operation = scope.launch(Dispatchers.IO) {
            previousOperation?.join()
            var attempt = 1
            while (attempt <= 3) {
                ensureActive()
                if (id != session) break
                try {
                    runConnection(address, id)
                    break
                } catch (e: ModeSwitch) {
                    synchronized(lock) { if (id == session) {
                        benchmarks.disconnected(SystemClock.elapsedRealtimeNanos())
                        lastPid.clear()
                    } }
                    publish(id) { fresh(it, "通信方式切替・再初期化中", true) }
                    sessionLog(id, e.message ?: "再初期化")
                    sessionLog(id, "再接続待機 600ms（Socket・Reader終了確認後）")
                    delay(600)
                    continue
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (manualStop) {
                        publish(id) { fresh(it, "手動切断（再接続しません）") }
                        break
                    }
                    // Unsupported monitor setup is an explicit stop, never an endless retry.
                    if (!CommunicationHealth.retry(manualStop, state.value.communication, attempt)) attempt = 3
                    val reason = e.message ?: e.javaClass.simpleName
                    experimentConsent = false
                    liveSession?.stop(); liveSession = null
                    publish(id) { it.copy(communication = CommunicationMode.OBD) }
                    sessionLog(id, "通信失敗 ($attempt/3): ${e.javaClass.simpleName}: $reason")
                    synchronized(lock) { if (id == session) benchmarks.disconnected(SystemClock.elapsedRealtimeNanos()) }
                    publish(id) { fresh(it,
                        busy = attempt < 3, status = if (attempt < 3)
                            "再試行中 ($attempt/3): $reason" else "接続エラー: $reason").copy(communicationError = reason) }
                    if (attempt < 3) { sessionLog(id, "再接続待機 ${BluetoothRecovery.waitMs(attempt)}ms"); delay(BluetoothRecovery.waitMs(attempt)) }
                    attempt++
                }
            }
            diagnosticRequested = false
            publish(id) { it.copy(diagnostic = it.diagnostic.copy(running = false)) }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runConnection(address: String, id: Int): Unit = coroutineScope {
        val bluetooth = adapter ?: error("Bluetoothアダプターがありません")
        check(bluetooth.isEnabled) { "BluetoothをONにしてください" }
        val local = bluetooth.getRemoteDevice(address).createRfcommSocketToServiceRecord(sppUuid)
        synchronized(lock) {
            if (id != session) {
                local.close()
                throw CancellationException()
            }
            socket = local
        }
        var reader: Job? = null

        val replies = Channel<Reply>(8)
        try {
            publish(id) { it.copy(status = "Bluetooth接続中…") }
            val connectStart = SystemClock.elapsedRealtimeNanos()
            sessionLog(id, "Bluetooth接続開始 ${java.time.Instant.now()} session=$id")
            val connectExpired = AtomicBoolean(false)
            val guard = launch {
                delay(12000)
                connectExpired.set(true)
                local.close()
            }
            try {
                local.connect()
            } catch (e: IOException) {
                if (connectExpired.get()) throw IOException("Bluetooth接続タイムアウト（12秒）", e)
                throw e
            } finally { guard.cancelAndJoin() }
            ensureActive()
            sessionLog(id, "Bluetooth接続成功 ${(SystemClock.elapsedRealtimeNanos() - connectStart) / 1_000_000}ms（ECU未確認）")
            publish(id) { it.copy(bluetoothConnected = true, communicationError = null, status = "ELM327初期化中…") }
            synchronized(lock) { if (id == session) lastPid.clear() }
            reader = launch(Dispatchers.IO) { readPrompts(local.inputStream, replies, id) }
            val output = local.outputStream
            suspend fun command(text: String, timeoutMs: Long): String {
                ensureActive()
                check(!monitoring) { "CAN受信中のコマンド/PID送信は禁止" }
                partialReply = ""
                if (replies.tryReceive().isSuccess) throw IOException("予期しない終端 >（同期ずれ）")
                val started = SystemClock.elapsedRealtimeNanos()
                val pid = Pid.entries.firstOrNull { it.command == text }
                fun record(end: Long, success: Boolean, timeout: Boolean = false) = synchronized(lock) {
                    if (id == session && pid != null) benchmarks.record(RequestSample(pid, started, end, success, timeout))
                }
                val expired = AtomicBoolean(false)
                val writeGuard = launch {
                    delay(timeoutMs)
                    expired.set(true)
                    local.close()
                }
                try {
                    val raw = withTimeout(timeoutMs) {
                        output.write((text + "\r").toByteArray(Charsets.US_ASCII))
                        output.flush()
                        replies.receive()
                    }
                    val valid = when (pid) {
                        Pid.RPM -> ObdParser.rpm(raw.text) != null
                        Pid.SPEED -> ObdParser.speedKmh(raw.text) != null
                        Pid.COOLANT -> ObdParser.coolantC(raw.text) != null
                        null -> false
                    }
                    record(raw.completedNs, valid)
                    return raw.text
                } catch (e: TimeoutCancellationException) {
                    record(SystemClock.elapsedRealtimeNanos(), false, true)
                    // No next command on this socket. A late reply belongs to this
                    // abandoned session; the outer retry opens a fresh socket.
                    throw IOException("$text 応答タイムアウト（終端 > 未受信）", e)
                } catch (e: IOException) {
                    record(SystemClock.elapsedRealtimeNanos(), false, expired.get())
                    if (expired.get()) throw IOException("$text 通信タイムアウト（終端 > 未受信）", e)
                    throw e
                } finally { writeGuard.cancel() }
            }
            suspend fun validated(text: String, timeoutMs: Long = 3000,
                valid: (String) -> Boolean): String {
                for (retry in 1..3) {
                    val raw = command(text, timeoutMs)
                    if (valid(raw)) return raw
                    publish(id) { it.copy(rpm = null, speedKmh = null, coolantC = null) }
                    val reason = ObdParser.failureReason(raw)
                    sessionLog(id, "$text → ${brief(raw)} / $reason ($retry/3)")
                    publish(id) { it.copy(connected = false, status = "再試行中 $text ($retry/3): $reason") }
                    if (retry == 3) throw IOException("$text: $reason")
                    delay(250)
                }
                error("unreachable")
            }
            for (init in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) {
                val raw = validated(init, if (init == "ATZ") 6000 else 3000) {
                    if (init == "ATZ") it.contains("ELM327", ignoreCase = true)
                    else ObdParser.adapterOk(it, init)
                }
                sessionLog(id, "$init → ${brief(raw)}")
            }
            publish(id) { it.copy(status = "ECU通信確認中…") }
            val supported = validated("0100", 12000) { ObdParser.payload(it, "00", 4) != null }
            sessionLog(id, "ECU通信成功: 0100 → ${brief(supported)}")
            publish(id) { it.copy(diagnostic = it.diagnostic.copy(ecuVerified = true)) }
            publish(id) { it.copy(connected = true, busy = false, status = "データ取得中") }
            synchronized(lock) { if (id == session) benchmarks.connected(SystemClock.elapsedRealtimeNanos()) }
            suspend fun diagnose(text: String): CommandDiagnostic {
                val result = try {
                    AdapterDiagnostics.classify(text, command(text, 3000) + ">")
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    val kind = if (e.message.orEmpty().contains("タイムアウト")) ResponseKind.TIMEOUT else ResponseKind.DISCONNECTED
                    val failed = AdapterDiagnostics.classify(text, partialReply, kind)
                    publish(id) { it.copy(diagnostic = it.diagnostic.record(failed)) }
                    throw e
                }
                publish(id) { it.copy(diagnostic = it.diagnostic.record(result)) }
                sessionLog(id, "$text → ${result.support.label} (${result.kind})")
                return result
            }
            if (diagnosticRequested) {
                diagnosticRequested = false
                try {
                    for (text in AdapterDiagnostics.commands) {
                        if (diagnosticStop) break
                        diagnose(text)
                    }
                    publish(id) { it.copy(diagnostic = it.diagnostic.copy(message = if (diagnosticStop) "診断停止・通常OBD2へ復帰" else "診断完了・通常OBD2へ復帰。サイレント動作は未検証")) }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    publish(id) { it.copy(diagnostic = it.diagnostic.copy(message = "診断通信異常・Socketを破棄して通常OBD2へ復帰")) }
                } finally {
                    publish(id) { it.copy(communication = CommunicationMode.OBD, diagnostic = it.diagnostic.copy(running = false)) }
                }
                throw ModeSwitch()
            }
            if (state.value.communication == CommunicationMode.CAN) {
              var attemptedMonitor = false
              try {
                monitorSession.reconnect()
                monitorSession.restored()
                monitorSession.setup()
                diagnose("ATI")
                val dp = command("ATDP", 3000)
                val dpn = command("ATDPN", 3000)
                publish(id) { it.copy(diagnostic = it.diagnostic.record(AdapterDiagnostics.classify("ATDP", dp + ">"))
                    .record(AdapterDiagnostics.classify("ATDPN", dpn + ">"))) }
                sessionLog(id, "ATDP → ${brief(dp)} / ATDPN → ${brief(dpn)}")
                check(CommunicationHealth.protocol6(dp, dpn)) { "11bit/500kbpsを確認できず中止（ATSP6未適用）: ${brief(dp)} / ${brief(dpn)}" }
                for (setting in listOf("ATSP6", "ATE0", "ATL0", "ATS0", "ATH1", "ATD1")) {
                    val raw = command(setting, 3000)
                    if (setting in AdapterDiagnostics.commands) publish(id) {
                        it.copy(diagnostic = it.diagnostic.record(AdapterDiagnostics.classify(setting, raw + ">")))
                    }
                    sessionLog(id, "$setting → ${brief(raw)}")
                    check(ObdParser.adapterOk(raw, setting)) { "$setting 未対応／失敗: ${brief(raw)}" }
                }
                check(CommunicationHealth.protocol6(command("ATDP", 3000), command("ATDPN", 3000))) { "ATSP6後のプロトコル不一致" }
                val silent = diagnose("ATCSM1")
                check(silent.kind in listOf(ResponseKind.OK, ResponseKind.UNSUPPORTED)) { "ATCSM1通信異常: ${silent.kind}" }
                val filter = diagnose("ATCRA201")
                // Explicit experimental consent is separate from independently verified silent behavior.
                check(monitorSession.authorize(true, silent.support, filter.support, trustedEvidence = false, experimentalConsent = experimentConsent || liveSession?.requested == true)) {
                    "CAN監視保留: ATCSM1=${silent.support.label}, ATCRA201=${filter.support.label}。サイレント動作の信頼できる根拠が未登録（CAN全体の非対応を意味しません）"
                }
                check(id == session && state.value.communication == CommunicationMode.CAN && state.value.bluetoothConnected && state.value.diagnostic.ecuVerified && !diagnosticRequested && !state.value.diagnostic.running) { "接続／ECU確認が無効・開始取消" }
                check(!replies.tryReceive().isSuccess) { "応答同期ずれ" }
                experimentConsent = false
                canBenchmarks.experimentInfo = ExperimentInfo(state.value.diagnostic.value("ATI"), "$dp / $dpn", silent.raw, filter.raw, "送信準備")
                monitorFailure = null
                stoppingMonitor = false
                canBenchmarks.connected(SystemClock.elapsedRealtimeNanos())
                monitoring = true
                publish(id) { it.copy(connected = false, rpm = null, speedKmh = null, coolantC = null,
                    appliedMode = null, appliedTiming = null, status = "CAN受信待ち") }
                suspend fun monitorWrite(bytes: ByteArray) {
                    val guard = launch { delay(3000); local.close() }
                    try { output.write(bytes); output.flush() } finally { guard.cancel() }
                }
                val started = SystemClock.elapsedRealtimeNanos()
                val live = liveSession
                live?.started(started)
                attemptedMonitor = true
                try {
                    if (live == null) canBenchmarks.start(started, CanExperiment.DURATION_NS)
                    canBenchmarks.experimentInfo = canBenchmarks.experimentInfo.copy(atma = "送信済み（未対応応答なし）")
                    monitorWrite("ATMA\r".toByteArray(Charsets.US_ASCII))
                    while (isActive && id == session && !manualStop && state.value.communication == CommunicationMode.CAN) {
                        if (monitorFailure != null) break
                        if (live != null) {
                            if (!live.receiving(SystemClock.elapsedRealtimeNanos())) break
                        } else if (CanExperiment(started).expired(SystemClock.elapsedRealtimeNanos())) break
                        val remainingMs = (CanExperiment.DURATION_NS - (SystemClock.elapsedRealtimeNanos() - started)) / 1_000_000
                        delay(if (live != null) 20 else minOf(20, remainingMs.coerceAtLeast(1)))
                    }
                    // Exactly one CR; no printable character followed by CR that could become a command.
                    stoppingMonitor = true
                    monitorSession.stop()
                    try { MonitorStopProtocol.finish(replies.tryReceive().isSuccess,
                        write = { monitorWrite(it) }, prompt = { replies.receive(); Unit }) }
                    catch (e: TimeoutCancellationException) { throw IOException("監視停止の > 未復帰。Socket破棄・再初期化", e) }
                    val reason = monitorFailure ?: if (manualStop) "手動切断" else if (live != null && live.expired(SystemClock.elapsedRealtimeNanos())) "検証時間上限で停止" else if (live == null && CanExperiment(started).expired(SystemClock.elapsedRealtimeNanos())) "5秒自動停止" else "手動停止"
                    canBenchmarks.stop(SystemClock.elapsedRealtimeNanos(), reason)
                    sessionLog(id, "監視終了: $reason / > 復帰確認。Socketを再初期化")
                } catch (e: Exception) {
                    if (e !is CancellationException) canBenchmarks.error(SystemClock.elapsedRealtimeNanos())
                    canBenchmarks.stop(SystemClock.elapsedRealtimeNanos(), e.message ?: "通信異常／Socket破棄")
                    throw e
                } finally {
                    synchronized(lock) { if (id == session) {
                        canBenchmarks.stop(SystemClock.elapsedRealtimeNanos(), monitorFailure ?: "通信異常／Socket破棄")
                        monitoring = false
                        canBenchmarks.disconnected(SystemClock.elapsedRealtimeNanos())
                    } }
                    publish(id) { fresh(it, if (manualStop) "手動切断（再接続しません）" else "監視終了／再初期化") }
                }
                if (live != null) {
                    live.stop()
                    publish(id) { fresh(it, reasonForLive(monitorFailure)).copy(
                        communication = CommunicationMode.CAN, communicationError = monitorFailure) }
                    liveSession = null
                    return@coroutineScope
                }
                publish(id) { it.copy(communication = CommunicationMode.OBD) }
                if (manualStop) return@coroutineScope
                throw ModeSwitch()
              } catch (e: ModeSwitch) { throw e
              } catch (e: CancellationException) { throw e
              } catch (e: Exception) {
                  experimentConsent = false
                  if (liveSession != null) {
                      liveSession?.stop()
                      liveSession = null
                      publish(id) { fresh(it, "CAN停止: ${e.message}").copy(
                          communication = CommunicationMode.CAN, communicationError = e.message ?: "CAN通信異常") }
                      return@coroutineScope
                  }
                  if (!attemptedMonitor) {
                      val now = SystemClock.elapsedRealtimeNanos()
                      val d = state.value.diagnostic
                      canBenchmarks.connected(now)
                      canBenchmarks.experimentInfo = ExperimentInfo(d.value("ATI"), "${d.value("ATDP")} / ${d.value("ATDPN")}",
                          d.results.firstOrNull { it.command == "ATCSM1" }?.raw.orEmpty(),
                          d.results.firstOrNull { it.command == "ATCRA201" }?.raw.orEmpty(), "未送信（開始条件不成立）")
                      canBenchmarks.start(now, CanExperiment.DURATION_NS)
                      canBenchmarks.stop(now, e.message ?: "開始中止")
                  }
                  sessionLog(id, e.message ?: "CAN監視異常")
                  publish(id) { it.copy(communication = CommunicationMode.OBD,
                      diagnostic = it.diagnostic.copy(message = e.message ?: "CAN監視異常")) }
                  if (manualStop) return@coroutineScope
                  monitorSession.reconnect()
                  throw ModeSwitch()
              }
            }
            val scheduler = PollingScheduler()
            var consecutiveFailures = 0
            var attemptedRevision = -1L
            var lastFailureLog = 0L
            while (isActive && id == session) {
                if (state.value.communication != CommunicationMode.OBD || diagnosticRequested) throw ModeSwitch()
                val config = state.value
                val desired = config.timing
                if (attemptedRevision != config.timingRevision) {
                    attemptedRevision = config.timingRevision
                    val raw = command(desired.name, 3000)
                    val ok = ObdParser.adapterOk(raw, desired.name)
                    publish(id) { it.copy(appliedTiming = if (ok && it.timingRevision == config.timingRevision) desired else null,
                        settingError = if (ok) null else "${desired.name} 失敗: ${brief(raw)}") }
                    sessionLog(id, "${desired.name} → ${brief(raw)}")
                    synchronized(lock) { if (id == session) benchmarks.connected(SystemClock.elapsedRealtimeNanos()) }
                }
                val pid = synchronized(lock) {
                    if (id != session) throw CancellationException()
                    val value = _state.value
                    if (value.appliedMode != value.mode) {
                        benchmarks.connected(SystemClock.elapsedRealtimeNanos())
                        _state.update { it.copy(appliedMode = it.mode, speedKmh = null, coolantC = null) }
                    }
                    scheduler.next(SystemClock.elapsedRealtimeNanos(), value.mode)
                }
                val raw = command(pid.command, 3000)
                val valid = when (pid) {
                    Pid.RPM -> ObdParser.rpm(raw) != null
                    Pid.SPEED -> ObdParser.speedKmh(raw) != null
                    Pid.COOLANT -> ObdParser.coolantC(raw) != null
                }
                synchronized(lock) { if (id == session && valid) lastPid[pid] = SystemClock.elapsedRealtimeNanos() }
                consecutiveFailures = if (valid) 0 else consecutiveFailures + 1
                if (consecutiveFailures >= 5) throw IOException("PID連続取得失敗（5回）")
                publish(id) {
                    it.copy(connected = valid, rpm = if (pid == Pid.RPM) ObdParser.rpm(raw) else it.rpm,
                        speedKmh = if (pid == Pid.SPEED) ObdParser.speedKmh(raw) else it.speedKmh,
                        coolantC = if (pid == Pid.COOLANT) ObdParser.coolantC(raw) else it.coolantC,
                        status = if (valid) "データ取得中" else "${pid.command}: ${ObdParser.failureReason(raw)}")
                }
                if (!valid && SystemClock.elapsedRealtimeNanos() - lastFailureLog >= 1_000_000_000L) {
                    lastFailureLog = SystemClock.elapsedRealtimeNanos()
                    sessionLog(id, "${pid.command} → ${brief(raw)}（連続エラーのログは最大1回/秒）")
                }
            }
        } finally {
            // Closing first interrupts blocking read/write; cancellation alone cannot.
            val closeStart = SystemClock.elapsedRealtimeNanos()
            BluetoothRecovery.retire(reader) { local.close() }
            sessionLog(id, "Socket終了・Reader終了 ${(SystemClock.elapsedRealtimeNanos() - closeStart) / 1_000_000}ms")

            replies.cancel()
            synchronized(lock) { if (socket === local) socket = null }
        }
    }

    private data class Reply(val text: String, val completedNs: Long)
    private suspend fun readPrompts(input: InputStream, replies: Channel<Reply>, id: Int) {
        val bytes = ByteArray(256)
        val text = StringBuilder()
        try {
            while (currentCoroutineContext().isActive) {
                val count = input.read(bytes)
                if (count < 0) throw IOException("Bluetooth接続が切断されました")
                for (i in 0 until count) {
                    if (!BluetoothRecovery.accepts(session, id)) return
                    val char = (bytes[i].toInt() and 0xFF).toChar()
                    if (char == '>') {
                        if (monitoring && !stoppingMonitor && !manualStop && state.value.communication == CommunicationMode.CAN) {
                            MonitorLine.error(text.toString(), false)?.let {
                                if (monitorFailure == null) canBenchmarks.error(SystemClock.elapsedRealtimeNanos())
                                monitorFailure = it
                                canBenchmarks.experimentInfo = canBenchmarks.experimentInfo.copy(atma = it)
                            }
                            if (monitorFailure == null) monitorFailure = "ATMA終了: ${brief(text.toString())}"
                            if (monitorFailure?.startsWith("ATMA終了") == true) canBenchmarks.error(SystemClock.elapsedRealtimeNanos())
                        }
                        replies.send(Reply(text.toString(), SystemClock.elapsedRealtimeNanos()))
                        text.setLength(0)
                    } else if (monitoring && (char == '\r' || char == '\n')) {
                        val line = text.toString().trim()
                        text.setLength(0)
                        if (line.isNotEmpty()) {
                            val now = SystemClock.elapsedRealtimeNanos()
                            val failure = MonitorLine.error(line, stoppingMonitor)
                            if (failure != null) {
                                canBenchmarks.rawRow(now, line, null)
                                if (monitorFailure == null) canBenchmarks.error(now)
                                monitorFailure = failure
                                canBenchmarks.experimentInfo = canBenchmarks.experimentInfo.copy(atma = failure)
                            } else if (!MonitorLine.ignored(line, stoppingMonitor)) {
                                val frame = CanFrameParser.parse(line, now)
                                synchronized(lock) { if (id == session) {
                                    canBenchmarks.rawRow(now, line, frame)
                                    if (frame != null && frame.id == 0x201) {
                                        canBenchmarks.record(frame)
                                        val values = Rx8CanDecoder().decode(frame)
                                        publish(id) { it.copy(can = it.can.copy(latest = frame, values = values)) }
                                    }
                                    else canBenchmarks.invalid(now)
                                } }
                            }
                        }
                    } else {
                        if (text.length >= 8192) throw IOException("ELM327応答が長すぎます")
                        text.append(char)
                        if (state.value.diagnostic.running) partialReply = text.toString()
                    }
                }
            }
        } catch (e: Exception) {
            if (monitoring && id == session && e !is CancellationException) {
                monitorFailure = "CAN受信失敗: ${e.message}"
                canBenchmarks.error(SystemClock.elapsedRealtimeNanos())
            }
            replies.close(e)
        } finally { replies.close() }
    }

    private fun reasonForLive(failure: String?) = failure?.let { "CAN停止: $it" } ?: "CAN監視停止（自動再開しません）"
    private fun brief(raw: String) = raw.replace('\r', ' ').replace('\n', ' ').trim().take(100).ifEmpty { "(空応答)" }
    fun close() { stopCurrent(); scope.cancel() }
}

/** Clears live telemetry and adapter acknowledgements, retaining user choices and completed results. */
fun DashboardState.resetConnection(view: BenchmarkView = BenchmarkView()) = DashboardState(
    devices = devices, logs = logs, mode = mode, timing = timing, benchmark = view,
    communication = communication, diagnostic = diagnostic.copy(ecuVerified = false),
    can = can.copy(latest = null, values = null, raw = emptyList()))
