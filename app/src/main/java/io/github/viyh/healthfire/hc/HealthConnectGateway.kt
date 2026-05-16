package io.github.viyh.healthfire.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.reflect.KClass

/** Whether Health Connect can be used on this device. */
enum class HcAvailability { AVAILABLE, UPDATE_REQUIRED, NOT_SUPPORTED }

/**
 * Thin wrapper over [HealthConnectClient]. Reading is generic: the gateway
 * holds no per-record-type logic, it simply reads whichever of
 * [RecordTypes.ALL] the user has granted.
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
     * Reads every [recordType] record in the window [start]..[end], following
     * Health Connect's pagination.
     */
    suspend fun readAll(
        recordType: KClass<out Record>,
        start: Instant,
        end: Instant,
    ): List<Record> {
        val records = ArrayList<Record>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken,
                ),
            )
            records.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)
        return records
    }
}
