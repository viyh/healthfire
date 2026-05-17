package io.github.viyh.healthfire.envelope

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Current envelope schema version. Bump only if the envelope structure changes. */
const val SCHEMA_VERSION: Int = 1

/**
 * One exported Health Connect record. The field names are the JSON keys of the
 * ingestion contract, so they are deliberately snake_case; `payload` holds the
 * raw record as serialized by [io.github.viyh.healthfire.hc.RecordSerializer].
 */
@Serializable
data class HcEnvelope(
    val schema_version: Int,
    val record_type: String,
    val hc_record_id: String,
    val person_uid: String,
    val recorded_at: String,
    val recorded_at_end: String?,
    val zone_offset: String?,
    val exported_at: String,
    val source_app: String,
    val source_device_type: String?,
    val source_device_manufacturer: String?,
    val source_device_model: String?,
    val recording_method: String,
    val hc_last_modified: String,
    val app_version: String,
    val payload: JsonElement,
)
