package com.maayys.resource

import java.io.File
import java.util.zip.ZipFile

/** Destination must be a fresh private directory owned by this installation. */
internal object SafeResourceZip {
    fun extract(archive: File, destination: File, maxBytes: Long = 2L * 1024 * 1024 * 1024,
                maxEntries: Int = 50_000) {
        require(maxBytes > 0 && maxEntries > 0)
        val root = destination.canonicalFile
        require(root.isDirectory && root.list()?.isEmpty() == true) { "staging must be empty" }
        val prefix = root.path + File.separator
        val seen = HashSet<String>()
        var total = 0L
        var entries = 0
        ZipFile(archive).use { zip ->
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val entry = iterator.nextElement()
                require(++entries <= maxEntries) { "too many zip entries" }
                val name = entry.name.removeSuffix("/")
                require(name.isNotEmpty() && !name.startsWith("/") &&
                    !name.contains('\\') && !name.contains(':') && !name.contains('\u0000') &&
                    name.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "unsafe zip path" }
                val out = File(root, name).canonicalFile
                require(out.path.startsWith(prefix) && seen.add(out.path)) { "unsafe or duplicate zip path" }
                if (entry.isDirectory) {
                    require(out.isDirectory || out.mkdirs()) { "cannot create directory" }
                } else {
                    require(!out.exists()) { "conflicting zip entry" }
                    val parent = out.parentFile!!
                    require(parent.isDirectory || parent.mkdirs()) { "cannot create parent" }
                    zip.getInputStream(entry).use { input -> out.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(count.toLong() <= maxBytes - total) { "uncompressed size limit exceeded" }
                            total += count
                            output.write(buffer, 0, count)
                        }
                    } }
                }
            }
        }
    }
}
