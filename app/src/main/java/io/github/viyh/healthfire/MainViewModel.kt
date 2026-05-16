package io.github.viyh.healthfire

import android.app.Application
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.viyh.healthfire.hc.HcAvailability
import io.github.viyh.healthfire.hc.RecordTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** State for the step-1 Health Connect status screen. */
data class MainUiState(
    val availability: HcAvailability,
    val knownTypeCount: Int,
    val grantedTypeCount: Int = 0,
    val historyGranted: Boolean = false,
    val backgroundGranted: Boolean = false,
    val isReading: Boolean = false,
    val lastReadSummary: String? = null,
)

/**
 * Drives the step-1 verification screen: reports Health Connect status and
 * runs a logged read of recently granted data.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val gateway = (application as HealthfireApp).container.healthConnectGateway

    private val _uiState = MutableStateFlow(
        MainUiState(
            availability = gateway.availability(),
            knownTypeCount = RecordTypes.ALL.size,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** The full permission set to hand to the Health Connect permission request. */
    val permissionsToRequest: Set<String> = gateway.readPermissions

    /** Re-reads availability and granted permissions. Safe to call on every resume. */
    fun refresh() {
        val availability = gateway.availability()
        _uiState.update { it.copy(availability = availability) }
        if (availability != HcAvailability.AVAILABLE) return
        viewModelScope.launch {
            runCatching { gateway.grantedPermissions() }
                .onSuccess(::applyGrantedPermissions)
                .onFailure { Log.e(TAG, "Could not read granted permissions", it) }
        }
    }

    private fun applyGrantedPermissions(granted: Set<String>) {
        val grantedTypes = RecordTypes.ALL.count {
            HealthPermission.getReadPermission(it) in granted
        }
        _uiState.update {
            it.copy(
                grantedTypeCount = grantedTypes,
                historyGranted = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted,
                backgroundGranted =
                    HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted,
            )
        }
    }

    /** Reads the last [READ_WINDOW_DAYS] days of every granted type, logging a breakdown. */
    fun readRecentData() {
        val current = _uiState.value
        if (current.availability != HcAvailability.AVAILABLE || current.isReading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isReading = true) }
            val summary = runCatching { readAndLog() }.getOrElse { e ->
                Log.e(TAG, "Read failed", e)
                "Read failed: ${e.message}"
            }
            _uiState.update { it.copy(isReading = false, lastReadSummary = summary) }
        }
    }

    private suspend fun readAndLog(): String {
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(READ_WINDOW_DAYS))
        val types = gateway.grantedRecordTypes()
        if (types.isEmpty()) return "No record types granted yet. Tap Grant first."
        Log.i(TAG, "Reading ${types.size} granted record types over the last $READ_WINDOW_DAYS days")
        var total = 0
        for (type in types) {
            val count = gateway.readAll(type, start, end).size
            total += count
            Log.i(TAG, "  ${RecordTypes.recordTypeName(type)}: $count")
        }
        Log.i(TAG, "Done: $total records across ${types.size} record types")
        return "Read $total records across ${types.size} granted types " +
            "(last $READ_WINDOW_DAYS days). See logcat for the per-type breakdown."
    }

    private companion object {
        const val TAG = "Healthfire"
        const val READ_WINDOW_DAYS = 30L
    }
}
