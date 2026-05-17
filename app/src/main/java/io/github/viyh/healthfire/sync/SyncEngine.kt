package io.github.viyh.healthfire.sync

import androidx.health.connect.client.records.Record
import io.github.viyh.healthfire.envelope.EnvelopeMapper
import io.github.viyh.healthfire.envelope.JsonlBatcher
import io.github.viyh.healthfire.firebase.AuthManager
import io.github.viyh.healthfire.firebase.StorageUploader
import io.github.viyh.healthfire.hc.HcAvailability
import io.github.viyh.healthfire.hc.HealthConnectGateway
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

/** Outcome of a sync run. */
sealed interface SyncResult {
    data class Success(val recordsExported: Int) : SyncResult
    data class Failure(val reason: String) : SyncResult
}

/**
 * Orchestrates a sync: read Health Connect, wrap each record in an envelope,
 * batch to JSON Lines, and upload. The first run backfills history and takes a
 * changes token; later runs export only what Health Connect reports as changed.
 */
class SyncEngine(
    private val gateway: HealthConnectGateway,
    private val authManager: AuthManager,
    private val uploader: StorageUploader,
    private val stateStore: SyncStateStore,
    private val appVersion: String,
) {

    /** Runs one sync and returns its outcome. Never throws. */
    suspend fun sync(): SyncResult {
        if (gateway.availability() != HcAvailability.AVAILABLE) {
            return SyncResult.Failure("Health Connect is not available.")
        }
        val personUid = authManager.uid
            ?: return SyncResult.Failure("Not signed in.")
        val grantedTypes = gateway.grantedRecordTypes()
        if (grantedTypes.isEmpty()) {
            return SyncResult.Failure("No Health Connect permissions granted.")
        }

        val exportedAt = Instant.now()
        val token = stateStore.load().changesToken
        return runCatching {
            if (token == null) {
                backfill(grantedTypes, personUid, exportedAt)
            } else {
                incremental(token, grantedTypes, personUid, exportedAt)
            }
        }.fold(
            onSuccess = { SyncResult.Success(it) },
            onFailure = { SyncResult.Failure(it.message ?: "Sync failed.") },
        )
    }

    /** First run: take a changes token, then export all history page by page. */
    private suspend fun backfill(
        types: List<KClass<out Record>>,
        personUid: String,
        exportedAt: Instant,
    ): Int {
        val token = gateway.getChangesToken(types.toSet())
        var exported = 0
        for (type in types) {
            gateway.readPaged(type, BACKFILL_START, exportedAt) { page ->
                exported += exportRecords(page, personUid, exportedAt)
            }
        }
        stateStore.save(
            SyncState(
                changesToken = token,
                lastSyncAt = rfc3339(exportedAt),
                backfillComplete = true,
            ),
        )
        return exported
    }

    /** Later runs: export the records Health Connect reports as changed. */
    private suspend fun incremental(
        token: String,
        types: List<KClass<out Record>>,
        personUid: String,
        exportedAt: Instant,
    ): Int {
        val changes = gateway.changesSince(token)
        if (changes.tokenExpired) {
            // The token aged out (unused for more than 30 days); re-baseline.
            return backfill(types, personUid, exportedAt)
        }
        val exported = exportRecords(changes.upsertedRecords, personUid, exportedAt)
        stateStore.save(
            SyncState(
                changesToken = changes.nextToken,
                lastSyncAt = rfc3339(exportedAt),
                backfillComplete = true,
            ),
        )
        return exported
    }

    /** Envelopes, batches and uploads one set of records. */
    private suspend fun exportRecords(
        records: List<Record>,
        personUid: String,
        exportedAt: Instant,
    ): Int {
        if (records.isEmpty()) return 0
        val envelopes = records.map {
            EnvelopeMapper.toEnvelope(it, personUid, exportedAt, appVersion)
        }
        JsonlBatcher.batch(envelopes).forEach { file ->
            uploader.upload(file, personUid, exportedAt)
        }
        return records.size
    }

    private fun rfc3339(instant: Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(instant)

    private companion object {
        /** Backfill horizon - earlier than typical consumer health-tracker data. */
        val BACKFILL_START: Instant = Instant.parse("2015-01-01T00:00:00Z")
    }
}
