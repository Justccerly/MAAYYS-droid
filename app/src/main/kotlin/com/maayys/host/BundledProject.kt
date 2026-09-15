package com.maayys.host

import android.content.Context
import com.google.gson.JsonParser
import com.maayys.resource.*
import com.maayys.runtime.RuntimeLog
import java.io.File

object BundledProject {
    @Synchronized fun prepare(context: Context, provider: ResourceProvider): ProjectCatalog {
        val manifest = context.assets.open("maayys-manifest.json").bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val version = manifest.get("version").asString
        if (provider.activeVersion() == version) provider.activePath()?.let { return ProjectCatalog(it) }
        RuntimeLog.append("安装 MaaYYs ${manifest.get("project_version").asString} 资源…")
        val archive = File.createTempFile("maayys-", ".zip", context.cacheDir)
        try {
            context.assets.open("maayys-resources.zip").use { input -> archive.outputStream().use { input.copyTo(it) } }
            val installed = provider.installArchive(archive, ResourceManifest(version, manifest.get("sha256").asString,
                manifest.get("size").asLong, null, null))
            RuntimeLog.append("MaaYYs 资源已安装")
            return ProjectCatalog(installed)
        } finally { archive.delete() }
    }
}
