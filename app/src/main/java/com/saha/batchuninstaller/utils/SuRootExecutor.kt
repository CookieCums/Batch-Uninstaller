package com.saha.batchuninstaller.utils

import java.io.IOException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs a command through su and bounds the wait. Standard error is redirected to standard output,
 * and command execution is kept outside the UI thread by the caller.
 */
object SuRootExecutor : RootExecutor {
    override fun execute(command: String): RootCommandResult {
        var process: Process? = null
        return try {
            val activeProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "$command 2>&1"))
            process = activeProcess
            val outputTask = FutureTask { activeProcess.inputStream.bufferedReader().use { it.readText() } }
            val exitTask = FutureTask { activeProcess.waitFor() }
            Thread(outputTask, "AppSieve-root-output").apply { isDaemon = true; start() }
            Thread(exitTask, "AppSieve-root-wait").apply { isDaemon = true; start() }

            val exitCode = exitTask.get(15, TimeUnit.SECONDS)
            val output = try {
                outputTask.get(1, TimeUnit.SECONDS)
            } catch (_: Exception) {
                ""
            }
            RootCommandResult(exitCode, output)
        } catch (_: TimeoutException) {
            process?.destroy()
            RootCommandResult(-1, "Root command timed out after 15 seconds", true)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            process?.destroy()
            RootCommandResult(-1, "Root command was interrupted", true)
        } catch (e: Exception) {
            process?.destroy()
            val reason = e.message ?: if (e is IOException) "Could not start su" else e.javaClass.simpleName
            RootCommandResult(-1, reason)
        } finally {
            try {
                process?.inputStream?.close()
            } catch (_: IOException) {
                // Best-effort stream cleanup.
            }
        }
    }
}
