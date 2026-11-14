package dev.aw.cardashboard

import kotlinx.coroutines.*

object BluetoothRecovery {
    fun waitMs(attempt: Int) = attempt.coerceIn(1, 3) * 1000L
    fun accepts(activeSession: Int, sourceSession: Int) = activeSession == sourceSession
    suspend fun retire(reader: Job?, close: () -> Unit) = withContext(NonCancellable) {
        try { close() } catch (_: Exception) { }
        reader?.cancelAndJoin()
    }
}
