package io.github.viyh.healthfire.envelope

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun envelope(recordType: String, recordedAt: String): HcEnvelope = HcEnvelope(
    schema_version = SCHEMA_VERSION,
    record_type = recordType,
    hc_record_id = "id",
    person_uid = "uid",
    recorded_at = recordedAt,
    recorded_at_end = null,
    zone_offset = null,
    exported_at = "2026-05-15T19:00:03Z",
    source_app = "com.example",
    source_device_type = null,
    source_device_manufacturer = null,
    source_device_model = null,
    recording_method = "UNKNOWN",
    hc_last_modified = "2026-05-15T15:43:00Z",
    app_version = "0.1.0",
    payload = JsonObject(emptyMap()),
)

class JsonlBatcherTest {

    @Test
    fun groupsByDateAndRecordType() {
        val files = JsonlBatcher.batch(
            listOf(
                envelope("steps", "2026-05-15T01:00:00Z"),
                envelope("steps", "2026-05-15T23:00:00Z"),
                envelope("steps", "2026-05-16T02:00:00Z"),
                envelope("heart_rate", "2026-05-15T10:00:00Z"),
            ),
        )
        assertEquals(3, files.size)
        val steps15 = files.single { it.dt == "2026-05-15" && it.recordType == "steps" }
        assertEquals(2, steps15.recordCount)
    }

    @Test
    fun jsonlHasOneLinePerRecord() {
        val file = JsonlBatcher.batch(
            listOf(
                envelope("steps", "2026-05-15T01:00:00Z"),
                envelope("steps", "2026-05-15T02:00:00Z"),
            ),
        ).single()
        val lines = file.jsonl.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("{") && lines[0].endsWith("}"))
    }

    @Test
    fun envelopeJsonUsesContractKeys() {
        val file = JsonlBatcher.batch(
            listOf(envelope("weight", "2026-05-15T01:00:00Z")),
        ).single()
        val line = file.jsonl.trim()
        assertTrue(line.contains("\"schema_version\":1"))
        assertTrue(line.contains("\"record_type\":\"weight\""))
        assertTrue(line.contains("\"recorded_at_end\":null"))
        assertTrue(line.contains("\"payload\":{"))
    }

    @Test
    fun emptyInputProducesNoFiles() {
        assertEquals(0, JsonlBatcher.batch(emptyList()).size)
    }
}
