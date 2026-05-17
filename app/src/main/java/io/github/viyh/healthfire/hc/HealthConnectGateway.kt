package io.github.viyh.healthfire.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.reflect.KClass

/** Whether Health Connect can be used on this device. */
enum class HcAvailability { AVAILABLE, UPDATE_REQUIRED, NOT_SUPPORTED }

/** Records changed since a token, plus the next token to poll from. */
data class HcChanges(
    val upsertedRecords: List<Record>,
    val nextToken: String,
    val tokenExpired: Boolean,
)

/**
 * Thin wrapper over [HealthConnectClient]. Reading is generic: the gateway
 * holds no per-record-type logic, it reads whichever of [RecordTypes.ALL] the
 * user has granted.
 */
class HealthConnectGateway(private val context: Context) {

    /** Health Connect availability, re-checked on each call. */
    fun availability(): HcAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HcAvailability.UPDATE_REQUIRED
            else -> HcAvailability.NOT_SUPPORTED
        }

    /** Created on first use; only valid once [availability] is [HcAvailability.AVAILABLE]. */
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    /**
     * Every permission the app may request: one read permission per known
     * record type, plus the two extended permissions (history and background).
     */
    val readPermissions: Set<String> = buildSet {
        RecordTypes.ALL.forEach { add(HealthPermission.getReadPermission(it)) }
        add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
    }

    /** Permissions Health Connect currently reports as granted to this app. */
    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    /** Known record types the app currently holds read permission for. */
    suspend fun grantedRecordTypes(): List<KClass<out Record>> {
        val granted = grantedPermissions()
        return RecordTypes.ALL.filter { HealthPermission.getReadPermission(it) in granted }
    }

    /**
     * Reads every [recordType] record in [start]..[end], invoking [onPage] for
     * each Health Connect page so the caller never holds the whole result.
     */
    suspend fun readPaged(
        recordType: KClass<out Record>,
        start: Instant,
        end: Instant,
        onPage: suspend (List<Record>) -> Unit,
    ) {
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken,
                ),
            )
            if (response.records.isNotEmpty()) onPage(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)
    }

    /** Reads every [recordType] record in [start]..[end] into one list. */
    suspend fun readAll(
        recordType: KClass<out Record>,
        start: Instant,
        end: Instant,
    ): List<Record> {
        val records = ArrayList<Record>()
        readPaged(recordType, start, end) { records.addAll(it) }
        return records
    }

    /** A Health Connect changes token covering [recordTypes], for incremental sync. */
    suspend fun getChangesToken(recordTypes: Set<KClass<out Record>>): String =
        client.getChangesToken(ChangesTokenRequest(recordTypes))

    /**
     * Polls all changes since [token], following pagination. Deletions are
     * ignored (the export is append-only). [HcChanges.tokenExpired] is true if
     * the token has aged out and the caller must re-baseline.
     */
    suspend fun changesSince(token: String): HcChanges {
        val upserted = ArrayList<Record>()
        var current = token
        while (true) {
            val response = client.getChanges(current)
            if (response.changesTokenExpired) {
                return HcChanges(emptyList(), current, tokenExpired = true)
            }
            for (change in response.changes) {
                if (change is UpsertionChange) upserted.add(change.record)
            }
            current = response.nextChangesToken
            if (!response.hasMore) break
        }
        return HcChanges(upserted, current, tokenExpired = false)
    }
}
