package io.github.viyh.healthfire.sync

import android.util.Log
import androidx.health.connect.client.records.Record
import io.github.viyh.healthfire.envelope.EnvelopeMapper
import io.github.viyh.healthfire.envelope.JsonlFile
import io.github.viyh.healthfire.envelope.JsonlWriter
import io.github.viyh.healthfire.firebase.AuthManager
import io.github.viyh.healthfire.firebase.StorageUploader
import io.github.viyh.healthfire.hc.HcAvailability
import io.github.viyh.healthfire.hc.HealthConnectGateway
import io.github.viyh.healthfire.hc.RecordTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

/** Outcome of a sync run. */
sealed interface SyncResult {
    data class Success(val recordsExported: Int) : SyncResult

    /**
     * A sync did not complete. [retryable] is true for transient faults (a
     * network or Health Connect read error), and false for configuration
     * faults (not signed in, no permissions) that re-running cannot fix.
     */
    data class Failure(val reason: String, val retryable: Boolean) : SyncResult
}

/** Live progress of the running sync, for the UI to observe. */
sealed interface SyncProgress {
    /** No sync is running. */
    data object Idle : SyncProgress

    /**
     * A sync is in progress. For a backfill, [recordTypesDone]/[recordTypesTotal]
     * and [currentType] track the per-type sweep; [currentDate] is the latest
     * record date reached. The counts move with every record.
     */
    data class Running(
        val backfill: Boolean,
        val recordTypesTotal: Int = 0,
        val recordTypesDone: Int = 0,
        val currentType: String? = null,
        val currentDate: String? = null,
        val recordsExported: Int = 0,
        val filesUploaded: Int = 0,
    ) : SyncProgress
}

/**
 * Orchestrates a sync: read Health Connect, wrap each record in an envelope,
 * and upload it as JSON Lines - one file per record type (large types split
 * into a few size-capped files). The first run backfills history and takes a
 * changes token; later runs export only what Health Connect reports as changed.
 */
class SyncEngine(
    private val gateway: HealthConnectGateway,
    private val authManager: AuthManager,
    private val uploader: StorageUploader,
    private val stateStore: SyncStateStore,
    private val metricsStore: SyncMetricsStore,
    private val appVersion: String,
) {

    private val _progress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)

    /** Live progress of the current sync, or [SyncProgress.Idle] when none runs. */
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    /** Runs one sync and returns its outcome. Never throws, except on cancellation. */
    suspend fun sync(): SyncResult {
        if (gateway.availability() != HcAvailability.AVAILABLE) {
            return SyncResult.Failure("Health Connect is not available.", retryable = false)
        }
        val personUid = authManager.uid
            ?: return SyncResult.Failure("Not signed in.", retryable = false)
        val grantedTypes = gateway.grantedRecordTypes()
        if (grantedTypes.isEmpty()) {
            return SyncResult.Failure("No Health Connect permissions granted.", retryable = false)
        }

        val exportedAt = Instant.now()
        val state = stateStore.load()
        Log.i(TAG, "sync: starting (backfill=${state.changesToken == null})")
        _progress.value = SyncProgress.Running(
            backfill = state.changesToken == null,
            recordTypesTotal = if (state.changesToken == null) grantedTypes.size else 0,
        )
        return try {
            runCatching {
                val token = state.changesToken
                if (token == null) {
                    backfill(state, grantedTypes, personUid, exportedAt)
                } else {
                    incremental(token, grantedTypes, personUid, exportedAt)
                }
            }.fold(
                onSuccess = { byType ->
                    val records = byType.values.sumOf { it.records }
                    Log.i(TAG, "sync: complete, $records datapoints exported")
                    SyncResult.Success(records)
                },
                onFailure = { error ->
                    // A Stop request cancels the coroutine; let that unwind.
                    if (error is CancellationException) throw error
                    Log.e(TAG, "sync: failed", error)
                    SyncResult.Failure(error.message ?: "Sync failed.", retryable = true)
                },
            )
        } finally {
            _progress.value = SyncProgress.Idle
        }
    }

    /**
     * Exports all history, one record type at a time. Resumable: the changes
     * token is taken once and each completed type is checkpointed - its sync
     * state and its metrics - so an interrupted run picks up from the next
     * unexported type and the recorded totals stay accurate.
     */
    private suspend fun backfill(
        state: SyncState,
        types: List<KClass<out Record>>,
        personUid: String,
        exportedAt: Instant,
    ): Map<String, TypeMetrics> {
        // Keep the original token across resumes so the first incremental sync
        // still catches whatever changed while the backfill was running.
        val token = state.backfillToken ?: gateway.getChangesToken(types.toSet())
        if (state.backfillToken == null) {
            stateStore.save(SyncState(backfillToken = token))
        }
        val doneTypes = state.backfillDoneTypes.toMutableSet()
        val totals = HashMap<String, TypeMetrics>()
        types.forEachIndexed { index, type ->
            val typeName = RecordTypes.recordTypeName(type)
            updateProgress {
                it.copy(recordTypesDone = index, currentType = typeName, currentDate = null)
            }
            if (typeName in doneTypes) {
                Log.i(TAG, "backfill: $typeName (${index + 1}/${types.size}) already done")
            } else {
                Log.i(TAG, "backfill: reading $typeName (${index + 1}/${types.size})")
                val typeMetrics = exportType(type, typeName, personUid, exportedAt)
                Log.i(
                    TAG,
                    "backfill: $typeName done (${typeMetrics.records} records, " +
                        "${typeMetrics.files} files)",
                )
                if (typeMetrics.records > 0) {
                    runCatching { metricsStore.record(mapOf(typeName to typeMetrics), exportedAt) }
                }
                totals[typeName] = typeMetrics
                doneTypes.add(typeName)
                // Checkpoint so an interrupted run resumes from the next type.
                stateStore.save(
                    SyncState(backfillToken = token, backfillDoneTypes = doneTypes.toList()),
                )
            }
        }
        updateProgress { it.copy(recordTypesDone = types.size, currentType = null) }
        stateStore.save(
            SyncState(
                changesToken = token,
                lastSyncAt = rfc3339(exportedAt),
                backfillComplete = true,
            ),
        )
        return totals
    }

    /** Reads one record type's full history and uploads it as size-capped files. */
    private suspend fun exportType(
        type: KClass<out Record>,
        typeName: String,
        personUid: String,
        exportedAt: Instant,
    ): TypeMetrics {
        val writer = JsonlWriter()
        val tally = HashMap<String, TypeMetrics>()
        gateway.readPaged(type, BACKFILL_START, exportedAt) { page ->
            for (record in page) {
                exportRecord(record, writer, personUid, exportedAt, tally)
            }
            Log.i(TAG, "backfill: $typeName +${page.size}")
        }
        writer.flush().forEach { uploadFile(it, personUid, exportedAt, tally) }
        return tally[typeName] ?: TypeMetrics()
    }

    /** Later runs: export the records Health Connect reports as changed. */
    private suspend fun incremental(
        token: String,
        types: List<KClass<out Record>>,
        personUid: String,
        exportedAt: Instant,
    ): Map<String, TypeMetrics> {
        val changes = gateway.changesSince(token)
        if (changes.tokenExpired) {
            // The token aged out (unused for more than 30 days); re-baseline
            // with a fresh backfill.
            Log.i(TAG, "incremental: token expired, re-baselining")
            _progress.value = SyncProgress.Running(backfill = true, recordTypesTotal = types.size)
            return backfill(SyncState(), types, personUid, exportedAt)
        }
        Log.i(TAG, "incremental: ${changes.upsertedRecords.size} changed records")
        val writer = JsonlWriter()
        val totals = HashMap<String, TypeMetrics>()
        for (record in changes.upsertedRecords) {
            exportRecord(record, writer, personUid, exportedAt, totals)
        }
        writer.flush().forEach { uploadFile(it, personUid, exportedAt, totals) }
        runCatching { metricsStore.record(totals, exportedAt) }
        stateStore.save(
            SyncState(
                changesToken = changes.nextToken,
                lastSyncAt = rfc3339(exportedAt),
                backfillComplete = true,
            ),
        )
        return totals
    }

    /** Envelopes one record into [writer]; uploads and tallies any file it completes. */
    private suspend fun exportRecord(
        record: Record,
        writer: JsonlWriter,
        personUid: String,
        exportedAt: Instant,
        tally: MutableMap<String, TypeMetrics>,
    ) {
        val envelope = EnvelopeMapper.toEnvelope(record, personUid, exportedAt, appVersion)
        writer.add(envelope)?.let { uploadFile(it, personUid, exportedAt, tally) }
        updateProgress {
            it.copy(
                recordsExported = it.recordsExported + 1,
                currentDate = laterDate(it.currentDate, envelope.recorded_at),
            )
        }
    }

    /** Uploads [file], adds it to [tally], and advances the file count. */
    private suspend fun uploadFile(
        file: JsonlFile,
        personUid: String,
        exportedAt: Instant,
        tally: MutableMap<String, TypeMetrics>,
    ) {
        uploader.upload(file, personUid, exportedAt)
        val bytes = file.jsonl.toByteArray(Charsets.UTF_8).size.toLong()
        tally[file.recordType] = (tally[file.recordType] ?: TypeMetrics()) +
            TypeMetrics(files = 1, records = file.recordCount, bytes = bytes)
        updateProgress { it.copy(filesUploaded = it.filesUploaded + 1) }
    }

    /** Atomically applies [transform] to the progress state while a sync runs. */
    private fun updateProgress(transform: (SyncProgress.Running) -> SyncProgress.Running) {
        _progress.update { current ->
            if (current is SyncProgress.Running) transform(current) else current
        }
    }

    /** The later of a current `yyyy-MM-dd` and the date inside RFC 3339 [rfc3339]. */
    private fun laterDate(current: String?, rfc3339: String): String {
        val date = rfc3339.substringBefore('T')
        return if (current == null || date > current) date else current
    }

    private fun rfc3339(instant: Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(instant)

    private companion object {
        const val TAG = "Healthfire"

        /** Backfill horizon - earlier than typical consumer health-tracker data. */
        val BACKFILL_START: Instant = Instant.parse("2015-01-01T00:00:00Z")
    }
}
