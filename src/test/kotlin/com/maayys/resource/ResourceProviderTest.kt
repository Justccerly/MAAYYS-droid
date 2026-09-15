package com.maayys.resource

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourceProviderTest {
    @get:Rule val temp = TemporaryFolder()
    private fun archive(valid: Boolean = true): File = temp.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            val names = if (valid) listOf("interface.json", "tasks/a.json") else listOf("../escape")
            names.forEach { name ->
                zip.putNextEntry(ZipEntry(name)); zip.write("{}".toByteArray()); zip.closeEntry()
            }
        }
    }
    private fun manifest(zip: File, version: String = "v1") = ResourceManifest(
        version, MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) },
        zip.length(), "https://example.invalid/pack.zip", null
    )
    private fun provider(root: File, zip: File) = ResourceProvider(root) { _, out, _ -> zip.copyTo(out, overwrite = true); Unit }
    private fun assertClean(root: File) {
        assertTrue(File(root, "cache").listFiles().orEmpty().isEmpty())
        assertFalse(File(root, "active.json.tmp").exists())
        assertTrue(File(root, "resource-versions").listFiles().orEmpty().none { it.name.startsWith(".install-") })
    }

    @Test fun installsAndActivatesValidArchive() {
        val zip = archive(); val root = temp.newFolder(); val provider = provider(root, zip)
        val target = provider.install(manifest(zip))
        assertEquals("v1", provider.activeVersion())
        assertEquals(target, provider.activePath())
        assertTrue(File(target, "tasks/a.json").isFile)
        assertClean(root)
    }

    @Test fun hashFailurePreservesPreviousVersion() {
        val zip = archive(); val root = temp.newFolder(); val provider = provider(root, zip)
        val previous = provider.install(manifest(zip))
        assertThrows(IllegalArgumentException::class.java) {
            provider.install(manifest(zip, "v2").copy(sha256 = "0".repeat(64)))
        }
        assertEquals(previous, provider.activePath())
        assertFalse(File(root, "resource-versions/v2").exists())
        assertClean(root)
    }

    @Test fun sameVersionCannotOverwriteExistingFiles() {
        val zip = archive(); val root = temp.newFolder(); val provider = provider(root, zip)
        val target = provider.install(manifest(zip))
        File(target, "sentinel").writeText("keep")
        assertThrows(IllegalArgumentException::class.java) { provider.install(manifest(zip)) }
        assertEquals("keep", File(target, "sentinel").readText())
    }

    @Test fun unsafeArchiveCleansStaging() {
        val zip = archive(false); val root = temp.newFolder(); val provider = provider(root, zip)
        assertThrows(IllegalArgumentException::class.java) { provider.install(manifest(zip)) }
        assertNull(provider.activePath())
        assertClean(root)
    }

    @Test fun invalidVersionRejectedBeforeDownload() {
        var downloaded = false
        val zip = archive(); val root = temp.newFolder()
        val provider = ResourceProvider(root) { _, _, _ -> downloaded = true }
        assertThrows(IllegalArgumentException::class.java) { provider.install(manifest(zip, "../escape")) }
        assertFalse(downloaded)
    }

    @Test fun pointerFailureRemovesNewVersion() {
        val zip = archive(); val root = temp.newFolder(); val provider = provider(root, zip)
        File(root, "active.json").mkdirs()
        File(root, "active.json/sentinel").writeText("keep")
        assertThrows(IllegalArgumentException::class.java) { provider.install(manifest(zip)) }
        assertFalse(File(root, "resource-versions/v1").exists())
        assertEquals("keep", File(root, "active.json/sentinel").readText())
        assertClean(root)
    }
}