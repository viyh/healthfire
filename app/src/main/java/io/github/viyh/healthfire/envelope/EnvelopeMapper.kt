package io.github.viyh.healthfire.envelope

import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import io.github.viyh.healthfire.hc.RecordSerializer
import io.github.viyh.healthfire.hc.RecordTypes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Wraps a Health Connect [Record] in an [HcEnvelope]: the contract's flat
 * fields plus the raw record as `payload`. Generic - no per-record-type code.
 */
object EnvelopeMapper {

    /**
     * @param personUid Firebase uid of the signed-in user.
     * @param exportedAt when this sync batch is being pushed.
     * @param appVersion exporter app version, for debugging.
     */
    fun toEnvelope(
        record: Record,
        personUid: String,
        exportedAt: Instant,
        appVersion: String,
    ): HcEnvelope {
        val metadata = record.metadata
        val payload = RecordSerializer.toJson(record)
        val fields = payload as? JsonObject ?: JsonObject(emptyMap())

        // Interval records carry startTime/endTime; instantaneous records carry
        // time. This is read from the serialized payload so it stays generic.
        val startTime = fields.stringAt("startTime")
        val recordedAt: String
        val recordedAtEnd: String?
        val zoneOffset: String?
        if (startTime != null) {
            recordedAt = startTime
            recordedAtEnd = fields.stringAt("endTime")
            zoneOffset = fields.stringAt("startZoneOffset")
        } else {
            recordedAt = fields.stringAt("time") ?: rfc3339(metadata.lastModifiedTime)
            recordedAtEnd = null
            zoneOffset = fields.stringAt("zoneOffset")
        }

        return HcEnvelope(
            schema_version = SCHEMA_VERSION,
            record_type = RecordTypes.recordTypeName(record::class),
            hc_record_id = metadata.id,
            person_uid = personUid,
            recorded_at = recordedAt,
            recorded_at_end = recordedAtEnd,
            zone_offset = zoneOffset,
            exported_at = rfc3339(exportedAt),
            source_app = metadata.dataOrigin.packageName,
            source_device_type = deviceTypeName(metadata.device),
            source_device_manufacturer = metadata.device?.manufacturer,
            source_device_model = metadata.device?.model,
            recording_method = recordingMethodName(metadata.recordingMethod),
            hc_last_modified = rfc3339(metadata.lastModifiedTime),
            app_version = appVersion,
            payload = payload,
        )
    }

    private fun rfc3339(instant: Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(instant)

    /** The string value at [key], or null if absent or not a JSON string. */
    private fun JsonObject.stringAt(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Maps a Health Connect recording-method constant to the contract's name. */
    internal fun recordingMethodName(method: Int): String = when (method) {
        Metadata.RECORDING_METHOD_ACTIVELY_RECORDED -> "ACTIVELY_RECORDED"
        Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED -> "AUTOMATICALLY_RECORDED"
        Metadata.RECORDING_METHOD_MANUAL_ENTRY -> "MANUALLY_ENTERED"
        else -> "UNKNOWN"
    }

    /** Maps a Health Connect device-type constant to the contract's name. */
    internal fun deviceTypeName(device: Device?): String? = when (device?.type) {
        null -> null
        Device.TYPE_WATCH -> "WATCH"
        Device.TYPE_PHONE -> "PHONE"
        Device.TYPE_SCALE -> "SCALE"
        Device.TYPE_RING -> "RING"
        Device.TYPE_HEAD_MOUNTED -> "HEAD_MOUNTED"
        Device.TYPE_FITNESS_BAND -> "FITNESS_BAND"
        Device.TYPE_CHEST_STRAP -> "CHEST_STRAP"
        Device.TYPE_SMART_DISPLAY -> "SMART_DISPLAY"
        else -> "UNKNOWN"
    }
}
