package com.saha.batchuninstaller.debloat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreRootCommandBuilderTest {
    @Test fun createsInstallExistingCommandsOnlyForPackageIdentifiers() {
        assertTrue(RestoreRootCommandBuilder.installExisting("com.example.app")!!.contains("cmd package install-existing --user 0 com.example.app"))
        assertTrue(RestoreRootCommandBuilder.installExisting("com.example.app", false)!!.contains("pm install-existing --user 0 com.example.app"))
        assertNull(RestoreRootCommandBuilder.installExisting("com.example.app; reboot"))
    }

    @Test fun quotesAllApkPathsAndRejectsPathTraversal() {
        val directory = createTempDir(prefix = "appsieve restore ")
        try {
            File(directory, "base.apk").writeBytes(byteArrayOf(1, 2, 3))
            File(directory, "split_01.apk").writeBytes(byteArrayOf(4, 5, 6))
            val valid = listOf(
                RestoreFileEntry("base.apk", "hash", 3),
                RestoreFileEntry("split_01.apk", "hash", 3)
            )
            val command = RestoreRootCommandBuilder.installMultiple(directory, valid)
            assertTrue(command!!.startsWith("pm install-multiple -r "))
            assertTrue(command.contains("'"))
            assertTrue(command.contains("base.apk"))
            assertTrue(command.contains("split_01.apk"))
            assertNull(RestoreRootCommandBuilder.installMultiple(directory, valid + RestoreFileEntry("../outside.apk", "", 0)))
            assertFalse(command.contains("rm "))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun requiresBaseApkAndExistingFiles() {
        val directory = createTempDir(prefix = "appsieve-restore")
        try {
            File(directory, "split_01.apk").writeBytes(byteArrayOf(1))
            assertNull(RestoreRootCommandBuilder.installMultiple(
                directory,
                listOf(RestoreFileEntry("split_01.apk", "hash", 1))
            ))
            assertNull(RestoreRootCommandBuilder.installMultiple(
                directory,
                listOf(RestoreFileEntry("base.apk", "hash", 1))
            ))
        } finally {
            directory.deleteRecursively()
        }
    }
}
