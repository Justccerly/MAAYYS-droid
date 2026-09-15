package com.maayys.resource

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafeResourceZipTest {
    @get:Rule val temp = TemporaryFolder()
    private fun archive(vararg entries: Pair<String, String>): File {
        val file = temp.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test fun extractsNestedFiles() {
        val destination = temp.newFolder()
        SafeResourceZip.extract(archive("tasks/a.json" to "{}", "interface.json" to "{}"), destination)
        assertEquals("{}", File(destination, "tasks/a.json").readText())
    }

    @Test fun rejectsTraversalAndAlternativeSeparators() {
        listOf("../escape", "/absolute", "a/../escape", "a\\escape", "C:/escape", "a//b").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                SafeResourceZip.extract(archive(name to "x"), temp.newFolder())
            }
        }
    }

    @Test fun limitsActualExpandedBytes() {
        assertThrows(IllegalArgumentException::class.java) {
            SafeResourceZip.extract(archive("a" to "12345"), temp.newFolder(), maxBytes = 4)
        }
    }

    @Test fun limitsEntryCount() {
        assertThrows(IllegalArgumentException::class.java) {
            SafeResourceZip.extract(archive("a" to "", "b" to ""), temp.newFolder(), maxEntries = 1)
        }
    }

    @Test fun rejectsFileDirectoryCollision() {
        assertThrows(IllegalArgumentException::class.java) {
            SafeResourceZip.extract(archive("a/" to "", "a" to "x"), temp.newFolder())
        }
    }
}