package com.saha.batchuninstaller.utils

/** Testable seam for shell operations that require root. */
data class RootCommandResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false
) {
    val success: Boolean get() = exitCode == 0 && !timedOut
}

fun interface RootExecutor {
    fun execute(command: String): RootCommandResult
}
