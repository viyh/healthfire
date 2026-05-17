package io.github.viyh.healthfire.envelope

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One JSON Lines file's worth of envelopes, all sharing a `(dt, record_type)`.
 * The export writes one such file per group; [dt] and [recordType] are the
 * hive partition keys of its destination path.
 */
data class JsonlFile(
    val dt: String,
    val recordType: String,
    val recordCount: Int,
    val jsonl: String,
)

/** Groups envelopes into JSON Lines files partitioned by date and record type. */
object JsonlBatcher {

    private val json = Json { explicitNulls = true }

    /**
     * Groups [envelopes] by `(dt, record_type)` - where `dt` is the UTC date of
     * `recorded_at` - and renders each group as newline-delimited JSON.
     */
    fun batch(envelopes: List<HcEnvelope>): List<JsonlFile> =
        envelopes
            .groupBy { dateOf(it.recorded_at) to it.record_type }
            .map { (key, group) ->
                JsonlFile(
                    dt = key.first,
                    recordType = key.second,
                    recordCount = group.size,
                    jsonl = group.joinToString(separator = "\n", postfix = "\n") {
                        json.encodeToString(it)
                    },
                )
            }

    /** The UTC date of an RFC 3339 timestamp: `2026-05-15T15:42:00Z` -> `2026-05-15`. */
    private fun dateOf(rfc3339: String): String = rfc3339.substringBefore('T')
}
