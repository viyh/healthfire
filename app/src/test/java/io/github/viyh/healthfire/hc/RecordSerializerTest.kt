package io.github.viyh.healthfire.hc

import androidx.health.connect.client.units.Mass
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

private enum class Hand { LEFT, RIGHT }

private data class Sample(val time: Instant, val beatsPerMinute: Long)

private data class FakeRecord(
    val startTime: Instant,
    val zoneOffset: ZoneOffset?,
    val samples: List<Sample>,
)

class RecordSerializerTest {

    @Test
    fun primitivesAndNull() {
        assertEquals(JsonPrimitive("hi"), RecordSerializer.toJson("hi"))
        assertEquals(JsonPrimitive(42), RecordSerializer.toJson(42))
        assertEquals(JsonPrimitive(true), RecordSerializer.toJson(true))
        assertEquals(JsonNull, RecordSerializer.toJson(null))
    }

    @Test
    fun nonFiniteDoubleBecomesNull() {
        assertEquals(JsonNull, RecordSerializer.toJson(Double.NaN))
        assertEquals(JsonNull, RecordSerializer.toJson(Double.POSITIVE_INFINITY))
        assertEquals(JsonPrimitive(3.5), RecordSerializer.toJson(3.5))
    }

    @Test
    fun instantSerializesAsRfc3339() {
        assertEquals(
            JsonPrimitive("2026-05-15T15:42:00Z"),
            RecordSerializer.toJson(Instant.parse("2026-05-15T15:42:00Z")),
        )
    }

    @Test
    fun zoneOffsetAndDuration() {
        assertEquals(JsonPrimitive("-07:00"), RecordSerializer.toJson(ZoneOffset.ofHours(-7)))
        assertEquals(JsonPrimitive("Z"), RecordSerializer.toJson(ZoneOffset.UTC))
        assertEquals(JsonPrimitive("PT1H30M"), RecordSerializer.toJson(Duration.ofMinutes(90)))
    }

    @Test
    fun enumSerializesAsName() {
        assertEquals(JsonPrimitive("RIGHT"), RecordSerializer.toJson(Hand.RIGHT))
    }

    @Test
    fun healthConnectUnitBecomesUnitValueObject() {
        val mass = RecordSerializer.toJson(Mass.kilograms(70.0)).jsonObject
        assertEquals(JsonPrimitive("g"), mass["unit"])
        assertEquals(JsonPrimitive(70000.0), mass["value"])
    }

    @Test
    fun objectReflectsKotlinPropertiesAndNests() {
        val record = FakeRecord(
            startTime = Instant.parse("2026-05-15T15:42:00Z"),
            zoneOffset = ZoneOffset.ofHours(-7),
            samples = listOf(
                Sample(Instant.parse("2026-05-15T15:42:10Z"), 61),
                Sample(Instant.parse("2026-05-15T15:42:20Z"), 63),
            ),
        )
        val json = RecordSerializer.toJson(record).jsonObject
        assertEquals(JsonPrimitive("2026-05-15T15:42:00Z"), json["startTime"])
        assertEquals(JsonPrimitive("-07:00"), json["zoneOffset"])
        val samples = json["samples"]!!.jsonArray
        assertEquals(2, samples.size)
        assertEquals(JsonPrimitive(63L), samples[1].jsonObject["beatsPerMinute"])
    }

    @Test
    fun nullPropertyIsJsonNull() {
        val json = RecordSerializer.toJson(
            FakeRecord(Instant.EPOCH, zoneOffset = null, samples = emptyList()),
        ).jsonObject
        assertEquals(JsonNull, json["zoneOffset"])
    }
}
