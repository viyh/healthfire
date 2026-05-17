package io.github.viyh.healthfire.firebase

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import io.github.viyh.healthfire.envelope.JsonlFile
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Uploads batched JSON Lines to Cloud Storage for Firebase at the contract
 * path: `hc/person_uid=<uid>/record_type=<type>/<exported_at>__<uuid>.jsonl`.
 */
class StorageUploader {

    /** Uploads one [file] and returns the object path it was written to. */
    suspend fun upload(file: JsonlFile, personUid: String, exportedAt: Instant): String {
        val path = objectPath(file, personUid, exportedAt)
        val metadata = StorageMetadata.Builder()
            .setContentType("application/x-ndjson")
            .build()
        FirebaseStorage.getInstance().reference
            .child(path)
            .putBytes(file.jsonl.toByteArray(Charsets.UTF_8), metadata)
            .await()
        return path
    }

    companion object {
        /**
         * The contract object path for [file], unique per upload via a random
         * UUID. The exported_at stamp uses `-` in place of `:` for the filename.
         */
        internal fun objectPath(
            file: JsonlFile,
            personUid: String,
            exportedAt: Instant,
        ): String {
            val stamp = DateTimeFormatter.ISO_INSTANT.format(exportedAt).replace(':', '-')
            return "hc/person_uid=$personUid/record_type=${file.recordType}" +
                "/${stamp}__${UUID.randomUUID()}.jsonl"
        }
    }
}
