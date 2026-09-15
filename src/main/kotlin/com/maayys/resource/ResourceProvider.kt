package com.maayys.resource

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class ResourceManifest(val version: String, val sha256: String, val size: Long, val github: String?, val mirror: String?)

class ResourceProvider internal constructor(
    private val root: File,
    private val testDownload: ((String, File, Long) -> Unit)?
) {
    constructor(root: File) : this(root, null)
    private val lock = ReentrantReadWriteLock()
    fun activeVersion(): String? = lock.read { File(root, "active.json").takeIf { it.isFile }?.readText()?.let { Regex("version\\\"\\s*:\\s*\\\"([^\"]+)").find(it)?.groupValues?.get(1) } }
    private fun validVersion(version: String) = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}").matches(version)
    fun activePath(): File? = lock.read { activeVersion()?.takeIf(::validVersion)?.let { File(root, "resource-versions/$it").takeIf(File::isDirectory) } }
    fun installArchive(archive: File, manifest: ResourceManifest): File = lock.write {
        ResourceProvider(root) { _, target, _ -> archive.copyTo(target, overwrite = true); Unit }
            .install(manifest.copy(github = "bundled-resource"))
    }
    fun installDownloadedArchive(archive: File, manifest: ResourceManifest): File = lock.write {
        ResourceProvider(root) { _, target, _ -> archive.copyTo(target, overwrite = true); Unit }.install(manifest)
    }
    fun install(m: ResourceManifest, preferMirror: Boolean = false): File = lock.write {
        require(validVersion(m.version)) { "invalid resource version" }
        require(m.size in 1..536870912L) { "invalid resource archive size" }
        require(Regex("[a-fA-F0-9]{64}").matches(m.sha256)) { "invalid sha256" }
        val target = File(root, "resource-versions/${m.version}")
        require(!target.exists()) { "version already installed; refusing to overwrite" }
        root.mkdirs()
        val cache = File(root, "cache").apply { mkdirs() }
        val tmp = File.createTempFile("resource-", ".zip", cache)
        var staging: File? = null
        var installed = false
        var committed = false
        val activeTmp = File(root, "active.json.tmp")
        try {
            val url = if (preferMirror) m.mirror ?: m.github else m.github ?: m.mirror
            require(!url.isNullOrBlank()) { "no download source" }
            val downloader = testDownload
            if (downloader != null) downloader(url, tmp, m.size) else download(url, tmp, m.size)
            require(tmp.length() == m.size) { "size mismatch" }
            require(sha256(tmp).equals(m.sha256, ignoreCase = true)) { "sha256 mismatch" }
            val versions = File(root, "resource-versions").apply { mkdirs() }
            val stage = java.nio.file.Files.createTempDirectory(versions.toPath(), ".install-").toFile()
            staging = stage
            SafeResourceZip.extract(tmp, stage)
            require(File(stage, "interface.json").isFile && File(stage, "tasks").isDirectory)
            require(stage.renameTo(target)) { "activation failed" }
            installed = true
            val active = File(root, "active.json")
            activeTmp.writeText("{\"version\":\"${m.version}\"}")
            require(activeTmp.renameTo(active)) { "active pointer update failed" }
            committed = true
            // Pruning is deferred until Runtime resource leases are implemented.
            return@write target
        } finally {
            tmp.delete()
            staging?.deleteRecursively()
            activeTmp.delete()
            if (installed && !committed) target.deleteRecursively()
        }
    }

    private fun prune(active: String) {
        File(root, "resource-versions").listFiles()?.filter { it.isDirectory && it.name != active && !it.name.endsWith(".tmp") }
            ?.sortedByDescending { it.lastModified() }?.drop(1)?.forEach { it.deleteRecursively() }
    }

    private fun download(url: String, out: File, limit: Long) {
        val source = URL(url)
        require(source.protocol == "https") { "HTTPS download required" }
        val c = source.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 30_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "MaaYYs-Android/0.1")
            require(c.responseCode in 200..299) { "HTTP ${c.responseCode}" }
            c.inputStream.use { input -> out.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= limit) { "download exceeds manifest size" }
                    output.write(buffer, 0, count)
                }
            } }
        } finally { c.disconnect() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
