package io.github.viyh.healthfire.envelope

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun envelope(recordType: String): HcEnvelope = HcEnvelope(
    schema_version = SCHEMA_VERSION,
    record_type = recordType,
    hc_record_id = "id",
    person_uid = "uid",
    recorded_at = "2026-05-15T01:00:00Z",
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

class JsonlWriterTest {

    @Test
    fun flushGroupsByRecordType() {
        val writer = JsonlWriter()
        assertNull(writer.add(envelope("steps")))
        assertNull(writer.add(envelope("steps")))
        assertNull(writer.add(envelope("heart_rate")))
        val files = writer.flush()
        assertEquals(2, files.size)
        assertEquals(2, files.single { it.recordType == "steps" }.recordCount)
        assertEquals(1, files.single { it.recordType == "heart_rate" }.recordCount)
    }

    @Test
    fun jsonlHasOneLinePerRecord() {
        val writer = JsonlWriter()
        writer.add(envelope("steps"))
        writer.add(envelope("steps"))
        val lines = writer.flush().single().jsonl.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("{") && lines[0].endsWith("}"))
    }

    @Test
    fun envelopeJsonUsesContractKeys() {
        val writer = JsonlWriter()
        writer.add(envelope("weight"))
        val line = writer.flush().single().jsonl.trim()
        assertTrue(line.contains("\"schema_version\":1"))
        assertTrue(line.contains("\"record_type\":\"weight\""))
        assertTrue(line.contains("\"recorded_at_end\":null"))
        assertTrue(line.contains("\"payload\":{"))
    }

    @Test
    fun flushWithNothingBufferedProducesNoFiles() {
        assertEquals(0, JsonlWriter().flush().size)
    }

    @Test
    fun aLargeRecordTypeIsSplitAcrossFiles() {
        // A tiny cap forces the type to roll over after a couple of records.
        val writer = JsonlWriter(maxBytes = 200)
        val files = ArrayList<JsonlFile>()
        repeat(20) { writer.add(envelope("steps"))?.let(files::add) }
        files.addAll(writer.flush())
        assertTrue("expected a split into multiple files", files.size > 1)
        assertEquals(20, files.sumOf { it.recordCount })
    }
}
