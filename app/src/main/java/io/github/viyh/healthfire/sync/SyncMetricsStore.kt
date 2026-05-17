package io.github.viyh.healthfire.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Upload tally for one record type: files written, datapoints, and bytes. */
@Serializable
data class TypeMetrics(
    val files: Int = 0,
    val records: Int = 0,
    val bytes: Long = 0,
) {
    operator fun plus(other: TypeMetrics): TypeMetrics =
        TypeMetrics(files + other.files, records + other.records, bytes + other.bytes)
}

/**
 * The date span of the health data exported for one record type: the earliest
 * and latest record date seen ("yyyy-MM-dd") and the count of distinct days
 * with data. Exact after a full backfill; a Start over re-sync refreshes it.
 */
@Serializable
data class TypeCoverage(
    val firstDay: String? = null,
    val lastDay: String? = null,
    val days: Int = 0,
) {
    /** Widens the span to cover [other]; the day count keeps the larger total. */
    fun mergedWith(other: TypeCoverage): TypeCoverage = TypeCoverage(
        firstDay = listOfNotNull(firstDay, other.firstDay).minOrNull(),
        lastDay = listOfNotNull(lastDay, other.lastDay).maxOrNull(),
        days = maxOf(days, other.days),
    )
}

/** Everything uploaded on one UTC calendar day, broken down by record type. */
@Serializable
data class DayMetrics(
    val byType: Map<String, TypeMetrics> = emptyMap(),
)

/** A time window for viewing [MetricsLog] totals. */
enum class MetricsWindow { TODAY, LAST_7_DAYS, LAST_30_DAYS, ALL_TIME }

/**
 * Rolling upload metrics. [days] keeps recent per-day detail keyed by UTC date
 * ("yyyy-MM-dd") and is pruned to [RETENTION_DAYS]; [lifetime] is the running
 * total since install and is never pruned. [coverage] is the date span of the
 * exported health data per record type.
 */
@Serializable
data class MetricsLog(
    val days: Map<String, DayMetrics> = emptyMap(),
    val lifetime: Map<String, TypeMetrics> = emptyMap(),
    val coverage: Map<String, TypeCoverage> = emptyMap(),
) {
    /** Per-type totals over every day bucket on or after [fromDate] (inclusive). */
    fun since(fromDate: String): Map<String, TypeMetrics> =
        days.filterKeys { it >= fromDate }.values
            .fold(emptyMap()) { acc, day -> acc.mergedWith(day.byType) }

    /**
     * Per-type totals for [window], relative to the UTC date [today].
     * [MetricsWindow.ALL_TIME] returns [lifetime]; the rest sum day buckets.
     */
    fun windowed(window: MetricsWindow, today: LocalDate): Map<String, TypeMetrics> =
        when (window) {
            MetricsWindow.TODAY -> since(today.toString())
            MetricsWindow.LAST_7_DAYS -> since(today.minusDays(6).toString())
            MetricsWindow.LAST_30_DAYS -> since(today.minusDays(29).toString())
            MetricsWindow.ALL_TIME -> lifetime
        }

    /**
     * Folds one sync run into this log: [uploads] merged into the day bucket
     * for [at] and into [lifetime] (day buckets past the retention window
     * dropped), and [coverage] merged per record type. An empty run is a no-op.
     */
    fun recording(
        uploads: Map<String, TypeMetrics>,
        coverage: Map<String, TypeCoverage>,
        at: Instant,
    ): MetricsLog {
        if (uploads.isEmpty() && coverage.isEmpty()) return this
        val day = utcDate(at)
        val cutoff = utcDate(at.minus(RETENTION_DAYS, ChronoUnit.DAYS))
        val mergedDays = if (uploads.isEmpty()) {
            days.filterKeys { it >= cutoff }
        } else {
            val merged = (days[day]?.byType ?: emptyMap()).mergedWith(uploads)
            (days + (day to DayMetrics(merged))).filterKeys { it >= cutoff }
        }
        val mergedCoverage = HashMap(this.coverage)
        for ((type, span) in coverage) {
            mergedCoverage[type] = (mergedCoverage[type] ?: TypeCoverage()).mergedWith(span)
        }
        return MetricsLog(
            days = mergedDays,
            lifetime = lifetime.mergedWith(uploads),
            coverage = mergedCoverage,
        )
    }

    companion object {
        const val RETENTION_DAYS: Long = 35

        private val DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

        /** The UTC calendar date of [instant], as "yyyy-MM-dd". */
        fun utcDate(instant: Instant): String = DATE.format(instant)
    }
}

/** Sums two per-type maps key-wise, leaving both operands untouched. */
fun Map<String, TypeMetrics>.mergedWith(
    other: Map<String, TypeMetrics>,
): Map<String, TypeMetrics> {
    if (other.isEmpty()) return this
    if (isEmpty()) return other
    val out = HashMap<String, TypeMetrics>(this)
    for ((type, m) in other) out[type] = (out[type] ?: TypeMetrics()) + m
    return out
}

private val Context.metricsDataStore by preferencesDataStore(name = "sync_metrics")
private val METRICS_LOG = stringPreferencesKey("metrics_log")

/** Persists [MetricsLog] in app-private storage. */
class SyncMetricsStore(private val context: Context) {

    /** The stored metrics, or an empty [MetricsLog] if nothing has been saved. */
    suspend fun load(): MetricsLog {
        val stored = context.metricsDataStore.data.first()[METRICS_LOG] ?: return MetricsLog()
        return runCatching { Json.decodeFromString<MetricsLog>(stored) }.getOrDefault(MetricsLog())
    }

    /** Folds one sync run's uploads and date coverage into the stored log. */
    suspend fun record(
        uploads: Map<String, TypeMetrics>,
        coverage: Map<String, TypeCoverage>,
        at: Instant,
    ) {
        if (uploads.isEmpty() && coverage.isEmpty()) return
        context.metricsDataStore.edit { prefs ->
            val current = prefs[METRICS_LOG]
                ?.let { runCatching { Json.decodeFromString<MetricsLog>(it) }.getOrNull() }
                ?: MetricsLog()
            prefs[METRICS_LOG] = Json.encodeToString(current.recording(uploads, coverage, at))
        }
    }

    /** Clears all stored metrics. */
    suspend fun clear() {
        context.metricsDataStore.edit { it.remove(METRICS_LOG) }
    }
}
