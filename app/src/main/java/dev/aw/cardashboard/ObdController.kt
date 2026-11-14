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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sin

data class PairedDevice(val name: String, val address: String)
data class DashboardState(
    val status: String = "未接続",
    val connected: Boolean = false,
    val bluetoothConnected: Boolean = false,
    val busy: Boolean = false,
    val demo: Boolean = false,
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
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.JAPAN)
    private val lock = Any()
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
        session++
        operation?.cancel()
        operation = null
        try { socket?.close() } catch (_: Exception) { }
        socket = null
    }

    fun disconnect() {
        stopCurrent()
        _state.update { DashboardState(devices = it.devices, logs = it.logs) }
        log("手動切断（再接続しません）")
    }

    fun startDemo() {
        stopCurrent()
        val id = session
        _state.update { DashboardState(status = "デモモード（実車ではない）", demo = true,
            devices = it.devices, logs = it.logs, rpm = 1200f, speedKmh = 0, coolantC = 80) }
        log("デモモード開始")
        operation = scope.launch {
            val start = SystemClock.elapsedRealtime()
            while (isActive && id == session) {
                val seconds = (SystemClock.elapsedRealtime() - start) / 1000.0
                val rpm = 2700 + 1900 * sin(seconds * 0.85) + 370 * sin(seconds * 2.2)
                publish(id) { it.copy(rpm = rpm.toFloat().coerceIn(700f, 7500f),
                    speedKmh = (50 + 32 * sin(seconds * 0.24)).toInt().coerceAtLeast(0),
                    coolantC = 83 + ((seconds / 7) % 11).toInt()) }
                delay(50)
            }
        }
    }

    /** Only read-only Mode 01 requests and ELM adapter configuration commands. */
    fun connect(address: String) {
        stopCurrent()
        val id = session
        if (!hasConnectPermission()) {
            _state.update { DashboardState(status = "接続エラー: Bluetooth接続権限が必要です",
                devices = it.devices, logs = it.logs) }
            log("Bluetooth接続権限が必要です")
            return
        }
        _state.update { DashboardState(status = "Bluetooth接続中…", busy = true,
            devices = it.devices, logs = it.logs) }
        log("接続開始: $address")
        operation = scope.launch(Dispatchers.IO) {
            for (attempt in 1..3) {
                ensureActive()
                if (id != session) break
                try {
                    runConnection(address, id)
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val reason = e.message ?: e.javaClass.simpleName
                    sessionLog(id, "通信失敗 ($attempt/3): $reason")
                    publish(id) { DashboardState(devices = it.devices, logs = it.logs,
                        busy = attempt < 3, status = if (attempt < 3)
                            "再試行中 ($attempt/3): $reason" else "接続エラー: $reason") }
                    if (attempt < 3) delay(1000)
                }
            }
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
        var monitor: Job? = null
        val replies = Channel<String>(8)
        try {
            publish(id) { it.copy(status = "Bluetooth接続中…") }
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
            sessionLog(id, "Bluetooth接続成功（ECU未確認）")
            publish(id) { it.copy(bluetoothConnected = true, status = "ELM327初期化中…") }
            reader = launch(Dispatchers.IO) { readPrompts(local.inputStream, replies) }
            val output = local.outputStream
            var lastCommandMs = 0f
            val timestamps = ArrayDeque<Long>()
            val durations = ArrayDeque<Float>()
            val lastRpm = AtomicLong(0L)
            fun clearMetrics() {
                timestamps.clear()
                durations.clear()
                publish(id) { it.copy(rpmHz = null, rpmResponseMs = null, rpmAverageMs = null) }
            }
            suspend fun command(text: String, timeoutMs: Long): String {
                ensureActive()
                if (replies.tryReceive().isSuccess) throw IOException("予期しない終端 >（同期ずれ）")
                val started = SystemClock.elapsedRealtimeNanos()
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
                    lastCommandMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
                    return raw
                } catch (e: TimeoutCancellationException) {
                    // No next command on this socket. A late reply belongs to this
                    // abandoned session; the outer retry opens a fresh socket.
                    throw IOException("$text 応答タイムアウト（終端 > 未受信）", e)
                } catch (e: IOException) {
                    if (expired.get()) throw IOException("$text 通信タイムアウト（終端 > 未受信）", e)
                    throw e
                } finally { writeGuard.cancel() }
            }
            suspend fun validated(text: String, timeoutMs: Long = 3000,
                valid: (String) -> Boolean): String {
                for (retry in 1..3) {
                    val raw = command(text, timeoutMs)
                    if (valid(raw)) return raw
                    clearMetrics()
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
                    else it.split('\r', '\n').any { line -> line.trim().equals("OK", true) }
                }
                sessionLog(id, "$init → ${brief(raw)}")
            }
            publish(id) { it.copy(status = "ECU通信確認中…") }
            val supported = validated("0100", 12000) { ObdParser.payload(it, "00", 4) != null }
            sessionLog(id, "ECU通信成功: 0100 → ${brief(supported)}")
            publish(id) { it.copy(connected = true, status = "データ取得中") }
            monitor = launch {
                while (isActive) {
                    delay(250)
                    if (lastRpm.get() != 0L && SystemClock.elapsedRealtimeNanos() - lastRpm.get() > 2_000_000_000L) {
                        publish(id) { it.copy(rpmHz = null, rpmResponseMs = null, rpmAverageMs = null) }
                    }
                }
            }
            var count = 0
            while (isActive && id == session) {
                val raw = validated("010C") { ObdParser.rpm(it) != null }
                val now = SystemClock.elapsedRealtimeNanos()
                if (lastRpm.get() != 0L && now - lastRpm.get() > 2_000_000_000L) clearMetrics()
                lastRpm.set(now)
                timestamps.addLast(now)
                durations.addLast(lastCommandMs)
                while (timestamps.size > 16) timestamps.removeFirst()
                while (durations.size > 16) durations.removeFirst()
                val hz = if (timestamps.size >= 2) (timestamps.size - 1) * 1_000_000_000f /
                    (timestamps.last() - timestamps.first()).coerceAtLeast(1) else null
                publish(id) { it.copy(rpm = ObdParser.rpm(raw), rpmHz = hz,
                    rpmResponseMs = lastCommandMs, rpmAverageMs = durations.average().toFloat(),
                    connected = true, status = "データ取得中") }
                if (count % 3 == 0) {
                    val speed = validated("010D") { ObdParser.speedKmh(it) != null }
                    publish(id) { it.copy(speedKmh = ObdParser.speedKmh(speed), connected = true, status = "データ取得中") }
                }
                if (count % 15 == 0) {
                    val temp = validated("0105") { ObdParser.coolantC(it) != null }
                    publish(id) { it.copy(coolantC = ObdParser.coolantC(temp), connected = true, status = "データ取得中") }
                }
                count++
            }
        } finally {
            // Closing first interrupts blocking read/write; cancellation alone cannot.
            try { local.close() } catch (_: Exception) { }
            reader?.cancel()
            monitor?.cancel()
            replies.cancel()
            synchronized(lock) { if (socket === local) socket = null }
        }
    }

    private suspend fun readPrompts(input: InputStream, replies: Channel<String>) {
        val bytes = ByteArray(256)
        val text = StringBuilder()
        try {
            while (currentCoroutineContext().isActive) {
                val count = input.read(bytes)
                if (count < 0) throw IOException("Bluetooth接続が切断されました")
                for (i in 0 until count) {
                    val char = (bytes[i].toInt() and 0xFF).toChar()
                    if (char == '>') {
                        replies.send(text.toString())
                        text.setLength(0)
                    } else {
                        if (text.length >= 8192) throw IOException("ELM327応答が長すぎます")
                        text.append(char)
                    }
                }
            }
        } catch (e: Exception) {
            replies.close(e)
        } finally { replies.close() }
    }

    private fun brief(raw: String) = raw.replace('\r', ' ').replace('\n', ' ').trim().take(100).ifEmpty { "(空応答)" }
    fun close() { stopCurrent(); scope.cancel() }
}
