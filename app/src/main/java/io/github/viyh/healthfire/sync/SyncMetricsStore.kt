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
 * total since install and is never pruned.
 */
@Serializable
data class MetricsLog(
    val days: Map<String, DayMetrics> = emptyMap(),
    val lifetime: Map<String, TypeMetrics> = emptyMap(),
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
     * Folds one sync run's [uploads] into this log: merged into the day bucket
     * for [at] and into [lifetime], with day buckets older than the retention
     * window dropped. Empty [uploads] returns this log unchanged.
     */
    fun recording(uploads: Map<String, TypeMetrics>, at: Instant): MetricsLog {
        if (uploads.isEmpty()) return this
        val day = utcDate(at)
        val cutoff = utcDate(at.minus(RETENTION_DAYS, ChronoUnit.DAYS))
        val merged = (days[day]?.byType ?: emptyMap()).mergedWith(uploads)
        return MetricsLog(
            days = (days + (day to DayMetrics(merged))).filterKeys { it >= cutoff },
            lifetime = lifetime.mergedWith(uploads),
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

    /** Folds one sync run's uploads into the stored log. */
    suspend fun record(uploads: Map<String, TypeMetrics>, at: Instant) {
        if (uploads.isEmpty()) return
        context.metricsDataStore.edit { prefs ->
            val current = prefs[METRICS_LOG]
                ?.let { runCatching { Json.decodeFromString<MetricsLog>(it) }.getOrNull() }
                ?: MetricsLog()
            prefs[METRICS_LOG] = Json.encodeToString(current.recording(uploads, at))
        }
    }
}
