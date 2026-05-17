package io.github.viyh.healthfire.firebase

import io.github.viyh.healthfire.envelope.JsonlFile
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StorageUploaderTest {

    private val file = JsonlFile(
        recordType = "blood_pressure",
        recordCount = 2,
        jsonl = "{}\n{}\n",
    )

    @Test
    fun objectPathMatchesContractLayout() {
        val path = StorageUploader.objectPath(
            file,
            personUid = "uid-abc",
            exportedAt = Instant.parse("2026-05-15T19:00:03Z"),
        )
        assertTrue(
            path,
            path.startsWith("hc/person_uid=uid-abc/record_type=blood_pressure/"),
        )
        assertTrue(path, path.endsWith(".jsonl"))
        assertTrue(path, path.contains("2026-05-15T19-00-03Z__"))
    }

    @Test
    fun objectPathIsUniquePerCall() {
        val a = StorageUploader.objectPath(file, "uid", Instant.EPOCH)
        val b = StorageUploader.objectPath(file, "uid", Instant.EPOCH)
        assertNotEquals(a, b)
    }
}
