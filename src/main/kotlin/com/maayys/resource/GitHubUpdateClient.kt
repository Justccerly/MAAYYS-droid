package com.maayys.resource

import com.google.gson.JsonParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class GitHubAsset(val name: String, val version: String, val url: String)
class GitHubUpdateClient(private val repository: String) {
    fun latestAsset(matcher: (String) -> Boolean): GitHubAsset? {
        val request = URL("https://api.github.com/repos/$repository/releases/latest").openConnection() as HttpURLConnection
        request.setRequestProperty("Accept", "application/vnd.github+json"); request.setRequestProperty("User-Agent", "MaaYYs-Android")
        request.connectTimeout = 20_000; request.readTimeout = 30_000
        return try { request.inputStream.bufferedReader().use { reader ->
            val root = JsonParser.parseReader(reader).asJsonObject
            val version = root.get("tag_name")?.asString ?: return@use null
            root.getAsJsonArray("assets")?.map { it.asJsonObject }?.firstOrNull { matcher(it.get("name").asString) }?.let {
                GitHubAsset(it.get("name").asString, version, it.get("browser_download_url").asString)
            }
        } } finally { request.disconnect() }
    }
    fun download(asset: GitHubAsset, destination: File): ResourceManifest {
        val request = URL(asset.url).openConnection() as HttpURLConnection
        request.setRequestProperty("Accept", "application/octet-stream"); request.setRequestProperty("User-Agent", "MaaYYs-Android")
        request.connectTimeout = 20_000; request.readTimeout = 60_000
        try { request.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } } }
        finally { request.disconnect() }
        val digest = MessageDigest.getInstance("SHA-256")
        destination.inputStream().use { input -> val buffer = ByteArray(8192); while (true) { val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
        return ResourceManifest(asset.version, digest.digest().joinToString("") { "%02x".format(it) }, destination.length(), asset.url, null)
    }
}
