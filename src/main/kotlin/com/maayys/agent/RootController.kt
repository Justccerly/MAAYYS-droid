package com.maayys.agent

import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Blocking API: invoke only on a worker thread. Requires an existing, user-authorized su. */
data class RootCommandResult(val exitCode: Int, val stdout: ByteArray, val stderr: ByteArray) {
    val success: Boolean get() = exitCode == 0
}

fun interface RootCommandExecutor {
    fun execute(command: String): RootCommandResult
}

class RootShell(private val timeoutMs: Long = 90_000) : RootCommandExecutor {
    init { require(timeoutMs in 1..120_000) }

    override fun execute(command: String): RootCommandResult {
        val process = ProcessBuilder("su", "-c", command).start()
        val readers = Executors.newFixedThreadPool(2)
        try {
            process.outputStream.close()
            val output = readers.submit(Callable { readBounded(process.inputStream, 32 * 1024 * 1024) })
            val errors = readers.submit(Callable { readBounded(process.errorStream, 64 * 1024) })
            check(process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) { "Root command timed out" }
            return RootCommandResult(process.exitValue(), output.get(5, TimeUnit.SECONDS), errors.get(5, TimeUnit.SECONDS))
        } finally {
            process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            readers.shutdownNow()
        }
    }

    // Continue draining after the limit, but reject truncated output (especially PNG data).
    private fun readBounded(stream: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var exceeded = false
        stream.use {
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                if (count <= limit - output.size()) output.write(buffer, 0, count)
                else exceeded = true
            }
        }
        check(!exceeded) { "Root command output exceeded limit" }
        return output.toByteArray()
    }
}

class RootController(private val shell: RootCommandExecutor = RootShell()) : Controller, ScreenshotProvider, PackageLauncher {
    /** May trigger the installed root manager's authorization dialog. Never call on the UI thread. */
    fun requestAuthorization(): Boolean = safely(false) {
        val result = shell.execute("id -u")
        result.success && result.stdout.toString(Charsets.UTF_8).trim() == "0"
    }

    override fun click(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0) return false
        return safely(false) { shell.execute("input tap $x $y").success }
    }

    override fun swipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long): Boolean {
        if (sx < 0 || sy < 0 || ex < 0 || ey < 0 || durationMs !in 1..60_000) return false
        return safely(false) { shell.execute("input swipe $sx $sy $ex $ey $durationMs").success }
    }

    override fun screenshot(): ByteArray? = safely(null) {
        val result = shell.execute("screencap -p")
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        result.stdout.takeIf { bytes ->
            result.success && bytes.size >= signature.size && signature.indices.all { bytes[it] == signature[it] }
        }
    }

    override fun launch(packageName: String): Boolean {
        // Strict allowlist: do not interpolate arbitrary shell syntax from task parameters.
        if (!Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(packageName)) return false
        return safely(false) {
            val result = shell.execute("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
            result.success && result.stdout.toString(Charsets.UTF_8).contains("Events injected: 1")
        }
    }

    private fun <T> safely(fallback: T, block: () -> T): T = try {
        block()
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        fallback
    } catch (_: Exception) {
        fallback
    }
}
