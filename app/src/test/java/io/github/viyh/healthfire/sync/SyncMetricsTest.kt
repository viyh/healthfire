package io.github.viyh.healthfire.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SyncMetricsTest {

    private fun type(files: Int, records: Int, bytes: Long) =
        TypeMetrics(files, records, bytes)

    @Test
    fun plusSumsFieldwise() {
        assertEquals(type(3, 30, 300), type(1, 10, 100) + type(2, 20, 200))
    }

    @Test
    fun mergedWithSumsSharedKeysAndKeepsDistinctOnes() {
        val a = mapOf("steps" to type(1, 10, 100), "weight" to type(1, 1, 50))
        val b = mapOf("steps" to type(2, 20, 200), "heart_rate" to type(1, 5, 75))
        assertEquals(
            mapOf(
                "steps" to type(3, 30, 300),
                "weight" to type(1, 1, 50),
                "heart_rate" to type(1, 5, 75),
            ),
            a.mergedWith(b),
        )
    }

    @Test
    fun mergedWithLeavesOperandsUntouched() {
        val a = mapOf("steps" to type(1, 10, 100))
        val b = mapOf("steps" to type(2, 20, 200))
        a.mergedWith(b)
        assertEquals(mapOf("steps" to type(1, 10, 100)), a)
        assertEquals(mapOf("steps" to type(2, 20, 200)), b)
    }

    @Test
    fun recordingBucketsByUtcDateAndAccumulatesLifetime() {
        val day1 = Instant.parse("2026-05-15T23:00:00Z")
        val day2 = Instant.parse("2026-05-16T01:00:00Z")
        val log = MetricsLog()
            .recording(mapOf("steps" to type(1, 10, 100)), day1)
            .recording(mapOf("steps" to type(1, 5, 50)), day2)

        assertEquals(mapOf("steps" to type(1, 10, 100)), log.days["2026-05-15"]?.byType)
        assertEquals(mapOf("steps" to type(1, 5, 50)), log.days["2026-05-16"]?.byType)
        assertEquals(mapOf("steps" to type(2, 15, 150)), log.lifetime)
    }

    @Test
    fun recordingMergesRepeatRunsWithinTheSameDay() {
        val morning = Instant.parse("2026-05-16T06:00:00Z")
        val evening = Instant.parse("2026-05-16T18:00:00Z")
        val log = MetricsLog()
            .recording(mapOf("steps" to type(1, 10, 100)), morning)
            .recording(mapOf("steps" to type(2, 20, 200)), evening)

        assertEquals(mapOf("steps" to type(3, 30, 300)), log.days["2026-05-16"]?.byType)
    }

    @Test
    fun recordingPrunesDayBucketsPastTheRetentionWindow() {
        val old = Instant.parse("2026-01-01T00:00:00Z")
        val now = Instant.parse("2026-05-16T00:00:00Z")
        val log = MetricsLog()
            .recording(mapOf("steps" to type(1, 10, 100)), old)
            .recording(mapOf("steps" to type(1, 5, 50)), now)

        assertEquals(setOf("2026-05-16"), log.days.keys)
        // The lifetime total still includes the pruned day.
        assertEquals(mapOf("steps" to type(2, 15, 150)), log.lifetime)
    }

    @Test
    fun sinceSumsOnlyDayBucketsInRange() {
        val log = MetricsLog()
            .recording(mapOf("steps" to type(1, 10, 100)), Instant.parse("2026-05-14T12:00:00Z"))
            .recording(mapOf("steps" to type(1, 20, 200)), Instant.parse("2026-05-15T12:00:00Z"))
            .recording(mapOf("steps" to type(1, 30, 300)), Instant.parse("2026-05-16T12:00:00Z"))

        assertEquals(mapOf("steps" to type(2, 50, 500)), log.since("2026-05-15"))
        assertEquals(emptyMap<String, TypeMetrics>(), log.since("2026-05-17"))
    }

    @Test
    fun recordingEmptyUploadsIsANoOp() {
        val log = MetricsLog()
            .recording(mapOf("steps" to type(1, 10, 100)), Instant.parse("2026-05-16T00:00:00Z"))
        assertEquals(log, log.recording(emptyMap(), Instant.parse("2026-05-16T06:00:00Z")))
    }

    @Test
    fun windowedSelectsTheDayBucketsForEachInterval() {
        val today = LocalDate.parse("2026-05-16")
        val log = MetricsLog()
            .recording(mapOf("steps" to type(1, 10, 100)), Instant.parse("2026-05-16T08:00:00Z"))
            .recording(mapOf("steps" to type(1, 20, 200)), Instant.parse("2026-05-12T08:00:00Z"))
            .recording(mapOf("steps" to type(1, 40, 400)), Instant.parse("2026-04-25T08:00:00Z"))

        assertEquals(
            mapOf("steps" to type(1, 10, 100)),
            log.windowed(MetricsWindow.TODAY, today),
        )
        assertEquals(
            mapOf("steps" to type(2, 30, 300)),
            log.windowed(MetricsWindow.LAST_7_DAYS, today),
        )
        assertEquals(
            mapOf("steps" to type(3, 70, 700)),
            log.windowed(MetricsWindow.LAST_30_DAYS, today),
        )
        assertEquals(
            mapOf("steps" to type(3, 70, 700)),
            log.windowed(MetricsWindow.ALL_TIME, today),
        )
    }
}
