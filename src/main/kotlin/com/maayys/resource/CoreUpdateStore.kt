package com.maayys.resource

import com.google.gson.JsonParser
import java.io.File
import java.util.zip.ZipFile

/** Stages a MaaFramework Android archive atomically and selects it only on the next runtime start. */
object CoreUpdateStore {
    private fun abi() = if (android.os.Build.SUPPORTED_ABIS.firstOrNull() == "x86_64") "x86_64" else "arm64-v8a"
    fun currentVersion(root: File): String? = File(root, "core-current.json").takeIf { it.isFile }?.let { JsonParser.parseString(it.readText()).getAsJsonObject().get("version")?.asString }
    fun activeDirectory(root: File): File? = currentVersion(root)?.let { File(root, "core/$it/${abi()}").takeIf(File::isDirectory) }
    fun stage(root: File, manifest: ResourceManifest, archive: File): File {
        require(manifest.size == archive.length())
        val target = File(root, "core/${manifest.version}/${abi()}")
        require(!target.exists()) { "Core version already staged" }
        val stage = File(root, "core/.stage-${System.nanoTime()}").apply { mkdirs() }
        try {
            ZipFile(archive).use { zip -> zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".so") }.forEach { entry ->
                val name = entry.name.substringAfterLast('/')
                require(name.matches(Regex("lib[A-Za-z0-9_]+\\.so")))
                val out = File(stage, name); zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            } }
            require(File(stage, "libMaaFramework.so").isFile)
            target.parentFile.mkdirs(); require(stage.renameTo(target))
            val pointer = File(root, "core-current.json.tmp"); pointer.writeText("{\"version\":\"${manifest.version}\"}")
            require(pointer.renameTo(File(root, "core-current.json")))
            return target
        } finally { stage.deleteRecursively() }
    }
    fun rollback(root: File) { File(root, "core-current.json").delete() }
}
