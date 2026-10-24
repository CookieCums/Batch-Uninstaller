package com.saha.batchuninstaller.debloat

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

/** Small filesystem-only integrity helper; it can be verified in JVM unit tests. */
object RestoreFileIntegrity {
    @Throws(IOException::class)
    fun sha256(file: File): String {
        if (!file.isFile) throw IOException("Backup file is missing: ${file.name}")
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    @Throws(IOException::class)
    fun matches(file: File, expectedSha256: String): Boolean =
        sha256(file).equals(expectedSha256, ignoreCase = true)
}
