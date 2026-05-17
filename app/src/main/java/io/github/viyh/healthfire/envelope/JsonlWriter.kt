package io.github.viyh.healthfire.envelope

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One JSON Lines file: a run of envelopes of a single record type, rendered as
 * newline-delimited JSON. [recordType] is the hive partition key of its path.
 */
data class JsonlFile(
    val recordType: String,
    val recordCount: Int,
    val jsonl: String,
)

/**
 * Accumulates envelopes per record type and renders them as JSON Lines. A
 * record type rolls over to a new file once its buffered text passes
 * [maxBytes], so a large type is neither held whole in memory nor uploaded as
 * one oversized object.
 *
 * Not thread-safe - drive it from a single coroutine.
 */
class JsonlWriter(private val maxBytes: Int = MAX_FILE_BYTES) {

    private class Buffer {
        val text = StringBuilder()
        var records = 0
    }

    private val buffers = LinkedHashMap<String, Buffer>()

    /**
     * Appends [envelope] to its record type's buffer, returning a finished
     * [JsonlFile] once that buffer reaches [maxBytes] - otherwise null.
     */
    fun add(envelope: HcEnvelope): JsonlFile? {
        val buffer = buffers.getOrPut(envelope.record_type) { Buffer() }
        buffer.text.append(json.encodeToString(envelope)).append('\n')
        buffer.records++
        if (buffer.text.length < maxBytes) return null
        buffers.remove(envelope.record_type)
        return JsonlFile(envelope.record_type, buffer.records, buffer.text.toString())
    }

    /** Returns a [JsonlFile] for every record type still buffered, then clears. */
    fun flush(): List<JsonlFile> {
        val files = buffers.map { (type, buffer) ->
            JsonlFile(type, buffer.records, buffer.text.toString())
        }
        buffers.clear()
        return files
    }

    companion object {
        /** Approximate JSONL size (in chars) at which a record type rolls to a new file. */
        const val MAX_FILE_BYTES: Int = 4 * 1024 * 1024

        private val json = Json { explicitNulls = true }
    }
}
