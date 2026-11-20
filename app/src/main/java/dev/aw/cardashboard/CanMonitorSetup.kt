package dev.aw.cardashboard

/** Called only after normal OBD initialization and successful 0100 verification. */
object CanMonitorSetup {
    val commands = listOf("ATSP6", "ATE0", "ATL0", "ATS0", "ATH1", "ATCAF0", "ATD1")

    suspend fun apply(ecuVerified: Boolean, exchange: suspend (String) -> String,
        record: (CommandDiagnostic) -> Unit, partialResponse: () -> String = { "" }) {
        check(ecuVerified) { "0100 ECU確認が必要です" }
        for (setting in commands) {
            val result = try {
                AdapterDiagnostics.classify(setting, exchange(setting) + ">")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                record(AdapterDiagnostics.classify(setting, partialResponse(), if (e.message.orEmpty().contains("タイムアウト"))
                    ResponseKind.TIMEOUT else ResponseKind.DISCONNECTED))
                throw e
            }
            record(result)
            check(result.kind == ResponseKind.OK) { "$setting 未対応／失敗: ${result.kind} RAW=${result.raw}" }
        }
    }
}

/** ATZ restores ELM defaults (including CAF1) on every normal OBD connection. */
object ObdInitialization {
    val commands = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
}
