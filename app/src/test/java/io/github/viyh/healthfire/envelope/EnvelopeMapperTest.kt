package io.github.viyh.healthfire.envelope

import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class EnvelopeMapperTest {

    private val exportedAt = Instant.parse("2026-05-15T19:00:03Z")

    @Test
    fun intervalRecordMapsStartAndEndTimes() {
        val record = StepsRecord(
            Instant.parse("2026-05-15T15:42:00Z"),
            ZoneOffset.ofHours(-7),
            Instant.parse("2026-05-15T16:00:00Z"),
            ZoneOffset.ofHours(-7),
            500L,
            Metadata.unknownRecordingMethod(),
        )
        val envelope = EnvelopeMapper.toEnvelope(record, "uid-1", exportedAt, "0.1.0")
        assertEquals(SCHEMA_VERSION, envelope.schema_version)
        assertEquals("steps", envelope.record_type)
        assertEquals("2026-05-15T15:42:00Z", envelope.recorded_at)
        assertEquals("2026-05-15T16:00:00Z", envelope.recorded_at_end)
        assertEquals("-07:00", envelope.zone_offset)
        assertEquals("uid-1", envelope.person_uid)
        assertEquals("0.1.0", envelope.app_version)
        assertEquals("2026-05-15T19:00:03Z", envelope.exported_at)
        assertEquals("UNKNOWN", envelope.recording_method)
    }

    @Test
    fun instantaneousRecordHasNoEndTime() {
        val record = WeightRecord(
            Instant.parse("2026-05-15T15:42:00Z"),
            null,
            Mass.kilograms(70.0),
            Metadata.unknownRecordingMethod(),
        )
        val envelope = EnvelopeMapper.toEnvelope(record, "uid-1", exportedAt, "0.1.0")
        assertEquals("weight", envelope.record_type)
        assertEquals("2026-05-15T15:42:00Z", envelope.recorded_at)
        assertNull(envelope.recorded_at_end)
        assertNull(envelope.zone_offset)
    }

    @Test
    fun payloadContainsTheRawRecord() {
        val record = WeightRecord(
            Instant.parse("2026-05-15T15:42:00Z"),
            ZoneOffset.UTC,
            Mass.kilograms(70.0),
            Metadata.unknownRecordingMethod(),
        )
        val payload = EnvelopeMapper.toEnvelope(record, "uid-1", exportedAt, "0.1.0")
            .payload.jsonObject
        assertTrue(payload.containsKey("time"))
        assertTrue(payload.containsKey("weight"))
        assertTrue(payload.containsKey("metadata"))
    }

    @Test
    fun recordingMethodNames() {
        assertEquals(
            "ACTIVELY_RECORDED",
            EnvelopeMapper.recordingMethodName(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED),
        )
        assertEquals(
            "AUTOMATICALLY_RECORDED",
            EnvelopeMapper.recordingMethodName(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED),
        )
        assertEquals(
            "MANUALLY_ENTERED",
            EnvelopeMapper.recordingMethodName(Metadata.RECORDING_METHOD_MANUAL_ENTRY),
        )
        assertEquals(
            "UNKNOWN",
            EnvelopeMapper.recordingMethodName(Metadata.RECORDING_METHOD_UNKNOWN),
        )
    }

    @Test
    fun deviceTypeNames() {
        assertEquals("WATCH", EnvelopeMapper.deviceTypeName(Device(Device.TYPE_WATCH, "x", "y")))
        assertEquals(
            "CHEST_STRAP",
            EnvelopeMapper.deviceTypeName(Device(Device.TYPE_CHEST_STRAP, "x", "y")),
        )
        assertNull(EnvelopeMapper.deviceTypeName(null))
    }
}
