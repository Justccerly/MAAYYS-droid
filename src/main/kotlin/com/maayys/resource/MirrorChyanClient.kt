package com.maayys.resource

import com.google.gson.JsonParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Mirror酱 official latest API used by MAA-Meow:
 * GET https://mirrorchyan.com/api/resources/{rid}/latest
 * query: current_version, user_agent, cdk. Response is {code,msg,data:{version_name,url,release_note}}.
 */
data class MirrorUpdate(val version: String, val url: String?, val note: String?)
class MirrorChyanClient(
    private val cdk: String = "",
    private val userAgent: String = "MaaYYs-Android",
    private val resourceId: String = "MaaYYs",
    private val os: String = "windows",
    private val arch: String = "amd64"
) {
    private val endpoint get() = "https://mirrorchyan.com/api/resources/$resourceId/latest"
    fun latest(currentVersion: String): MirrorUpdate {
        val query = "?current_version=${java.net.URLEncoder.encode(currentVersion, "UTF-8")}" +
            "&user_agent=${java.net.URLEncoder.encode(userAgent, "UTF-8")}" +
            "&os=$os&arch=$arch" + if (cdk.isBlank()) "" else "&cdk=${java.net.URLEncoder.encode(cdk, "UTF-8")}"
        val connection = URL(endpoint + query).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000; connection.readTimeout = 30_000
        return try { connection.inputStream.bufferedReader().use { reader ->
            val root = JsonParser.parseReader(reader).asJsonObject
            val code = root.get("code")?.asInt ?: -1
            require(code == 0) { root.get("msg")?.asString ?: "Mirror酱请求失败（code=$code）" }
            val data = root.getAsJsonObject("data") ?: error("Mirror酱返回数据为空")
            MirrorUpdate(data.get("version_name").asString, data.get("url")?.asString, data.get("release_note")?.asString)
        } } finally { connection.disconnect() }
    }
    fun download(update: MirrorUpdate, destination: File): ResourceManifest {
        val url = requireNotNull(update.url) { "Mirror酱没有提供下载地址（通常需要有效 CDK）" }
        require(url.startsWith("https://")) { "仅允许 HTTPS 更新地址" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000; connection.readTimeout = 60_000
        try { connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } } }
        finally { connection.disconnect() }
        val digest = MessageDigest.getInstance("SHA-256")
        destination.inputStream().use { input -> val b = ByteArray(8192); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
        return ResourceManifest(update.version, digest.digest().joinToString("") { "%02x".format(it) }, destination.length(), url, null)
    }
}
