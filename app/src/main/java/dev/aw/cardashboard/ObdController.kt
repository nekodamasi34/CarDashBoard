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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.sin


data class PairedDevice(val name: String, val address: String)

data class DashboardState(
    val status: String = "未接続",
    val connected: Boolean = false,
    val demo: Boolean = false,
    val devices: List<PairedDevice> = emptyList(),
    val rpm: Float? = null,
    val speedKmh: Int? = null,
    val coolantC: Int? = null,
    val rpmHz: Float? = null,
    val logs: List<String> = listOf("アプリ起動。まずはデモで動作確認！")
)

class ObdController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.JAPAN)

    @Volatile private var socket: BluetoothSocket? = null
    private var operation: Job? = null
    private var session = 0

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun log(message: String) {
        val line = "${LocalTime.now().format(timeFormat)}  $message"
        _state.update { it.copy(logs = (it.logs + line).takeLast(35)) }
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        if (!hasConnectPermission()) {
            log("付近のデバイスの権限が必要だよ")
            return
        }
        try {
            val bluetooth = adapter
            if (bluetooth == null) {
                log("この端末にBluetoothがない")
                return
            }
            if (!bluetooth.isEnabled) {
                log("スマホのBluetoothをONにしてね")
                return
            }
            val devices = bluetooth.bondedDevices.orEmpty().map {
                PairedDevice(it.name ?: "名前なし", it.address)
            }.sortedWith(compareBy<PairedDevice> { !it.name.contains("OBD", ignoreCase = true) }.thenBy { it.name })
            _state.update { it.copy(devices = devices) }
            log("ペアリング済みデバイス: ${devices.size}件")
        } catch (e: SecurityException) {
            log("Bluetooth権限エラー: ${e.message}")
        }
    }

    private fun stopCurrent() {
        session++
        operation?.cancel()
        operation = null
        // Closing the socket interrupts a blocking Bluetooth read / connect.
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    fun disconnect() {
        stopCurrent()
        _state.update { it.copy(status = "未接続", connected = false, demo = false,
            rpm = null, speedKmh = null, coolantC = null, rpmHz = null) }
        log("切断")
    }

    fun startDemo() {
        stopCurrent()
        val mySession = session
        _state.update { it.copy(status = "デモ動作中（実車ではない）", connected = false,
            demo = true, rpm = 1200f, speedKmh = 0, coolantC = 80, rpmHz = 20f) }
        log("デモモード開始")
        operation = scope.launch {
            val start = SystemClock.elapsedRealtime()
            while (isActive && mySession == session) {
                val seconds = (SystemClock.elapsedRealtime() - start) / 1000.0
                val rpm = 2700.0 + 1900.0 * sin(seconds * 0.85) + 370.0 * sin(seconds * 2.2)
                val speed = (50 + 32 * sin(seconds * 0.24)).toInt().coerceAtLeast(0)
                _state.update { it.copy(rpm = rpm.toFloat().coerceIn(700f, 7500f),
                    speedKmh = speed, coolantC = 83 + ((seconds / 7) % 11).toInt()) }
                delay(50)
            }
        }
    }

    /** Only read-only OBD-II Mode 01 commands are used. */
    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!hasConnectPermission()) {
            log("Bluetooth接続権限がないよ")
            return
        }
        stopCurrent()
        val mySession = session
        _state.update { it.copy(status = "Bluetooth接続中…", connected = false, demo = false,
            rpm = null, speedKmh = null, coolantC = null, rpmHz = null) }
        log("接続開始: $address")

        operation = scope.launch(Dispatchers.IO) {
            var localSocket: BluetoothSocket? = null
            var readerJob: Job? = null
            try {
                val bluetooth = adapter ?: error("Bluetoothアダプターがない")
                if (!bluetooth.isEnabled) error("BluetoothをONにしてね")
                val device = bluetooth.getRemoteDevice(address)
                val connectedSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                localSocket = connectedSocket
                socket = connectedSocket
                connectedSocket.connect()
                if (mySession != session) return@launch
                log("Bluetooth接続成功。ELM327を初期化中")
                _state.update { it.copy(status = "ELM327初期化中…") }

                val replies = Channel<String>(Channel.UNLIMITED)
                readerJob = launch(Dispatchers.IO) { readPrompts(connectedSocket.inputStream, replies) }
                val output = connectedSocket.outputStream

                suspend fun command(text: String, timeoutMs: Long = 3000L): String {
                    // Ignore any unsolicited/leftover prompts from the previous command.
                    while (replies.tryReceive().isSuccess) { }
                    output.write((text + "\r").toByteArray(Charsets.US_ASCII))
                    output.flush()
                    return withTimeout(timeoutMs) { replies.receive() }
                }

                // Keep AT commands and OBD-II requests strictly sequential.
                for (init in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) {
                    val response = command(init, if (init == "ATZ") 6000L else 3000L)
                    log("$init → ${brief(response)}")
                }
                val supported = command("0100", 7000L)
                log("0100 → ${brief(supported)}")
                if (ObdParser.payload(supported, "00", 4) == null) {
                    log("ECU未応答の可能性。イグニッションONを確認してね")
                }
                _state.update { it.copy(status = "データ取得中", connected = true) }
                log("PIDポーリング開始（RPM優先）")

                val timestamps = ArrayDeque<Long>()
                var count = 0
                while (isActive && mySession == session) {
                    val rawRpm = command("010C")
                    val rpm = ObdParser.rpm(rawRpm)
                    if (rpm != null) {
                        val now = SystemClock.elapsedRealtimeNanos()
                        timestamps.addLast(now)
                        while (timestamps.size > 16) timestamps.removeFirst()
                        val hz = if (timestamps.size >= 2) {
                            (timestamps.size - 1) * 1_000_000_000.0f /
                                (timestamps.last() - timestamps.first()).coerceAtLeast(1L)
                        } else null
                        _state.update { it.copy(rpm = rpm, rpmHz = hz) }
                    } else if (count % 10 == 0) {
                        log("RPM応答なし: ${brief(rawRpm)}")
                    }

                    if (count % 3 == 0) {
                        val rawSpeed = command("010D")
                        val speed = ObdParser.speedKmh(rawSpeed)
                        if (speed != null) _state.update { it.copy(speedKmh = speed) }
                    }
                    if (count % 15 == 0) {
                        val rawTemp = command("0105")
                        val coolant = ObdParser.coolantC(rawTemp)
                        if (coolant != null) _state.update { it.copy(coolantC = coolant) }
                    }
                    count++
                }
            } catch (e: TimeoutCancellationException) {
                if (mySession == session) {
                    log("通信終了: 応答タイムアウト")
                    _state.update { it.copy(status = "接続エラー: 応答タイムアウト", connected = false) }
                }
            } catch (_: CancellationException) {
                // Expected when the user disconnects.
            } catch (e: Exception) {
                if (mySession == session) {
                    val reason = "${e.javaClass.simpleName}: ${e.message ?: "接続失敗"}"
                    log("通信終了: $reason")
                    _state.update { it.copy(status = "接続エラー: $reason", connected = false) }
                }
            } finally {
                readerJob?.cancel()
                try { localSocket?.close() } catch (_: Exception) {}
                if (mySession == session) {
                    socket = null
                    _state.update { it.copy(connected = false,
                        status = if (it.status == "データ取得中") "切断された" else it.status) }
                }
            }
        }
    }

    private suspend fun readPrompts(input: InputStream, replies: Channel<String>) {
        val bytes = ByteArray(256)
        val text = StringBuilder()
        try {
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                for (i in 0 until count) {
                    val character = (bytes[i].toInt() and 0xFF).toChar()
                    if (character == '>') {
                        replies.send(text.toString())
                        text.setLength(0)
                    } else if (text.length < 8192) {
                        text.append(character)
                    }
                }
            }
        } finally {
            replies.close()
        }
    }

    private fun brief(raw: String): String = raw.replace('\r', ' ').replace('\n', ' ')
        .trim().take(100).ifEmpty { "(空応答)" }

    fun close() {
        stopCurrent()
        scope.cancel()
    }
}
