package com.saha.batchuninstaller.debloat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreFileIntegrityTest {
    @Test fun calculatesStableSha256ForSameFileContents() {
        val file = File.createTempFile("appsieve-restore", ".apk")
        try {
            file.writeBytes("sample apk payload".toByteArray(Charsets.UTF_8))
            val first = RestoreFileIntegrity.sha256(file)
            val second = RestoreFileIntegrity.sha256(file)
            assertEquals(first, second)
            assertEquals(64, first.length)
            assertTrue(RestoreFileIntegrity.matches(file, first))
        } finally {
            file.delete()
        }
    }

    @Test fun detectsBackupTampering() {
        val file = File.createTempFile("appsieve-restore", ".apk")
        try {
            file.writeBytes("original bytes".toByteArray(Charsets.UTF_8))
            val originalHash = RestoreFileIntegrity.sha256(file)
            file.writeBytes("modified bytes".toByteArray(Charsets.UTF_8))
            assertFalse(RestoreFileIntegrity.matches(file, originalHash))
        } finally {
            file.delete()
        }
    }

    @Test fun missingFileIsNotAccepted() {
        val dir = createTempDir(prefix = "appsieve-restore")
        try {
            val missing = File(dir, "missing.apk")
            assertFalse(missing.exists())
            assertTrue(runCatching { RestoreFileIntegrity.sha256(missing) }.isFailure)
        } finally {
            dir.deleteRecursively()
        }
    }
}
