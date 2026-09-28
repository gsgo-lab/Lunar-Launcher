package com.lunar.launcher.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads an official Minecraft Java instance from Mojang metadata. */
object MinecraftInstaller {
    data class Progress(val done: Int, val total: Int, val label: String)

    suspend fun install(context: Context, version: MinecraftVersionRepository.Version, onProgress: (Progress) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(context.filesDir, "instances/${version.id}").apply { mkdirs() }
            val metadataText = downloadText(version.manifestUrl)
            File(root, "version.json").writeText(metadataText)
            val meta = JSONObject(metadataText)
            val downloads = mutableListOf<Download>()
            val client = meta.getJSONObject("downloads").getJSONObject("client")
            downloads += Download("client.jar", client.getString("url"), client.optString("sha1"))

            val libs = meta.optJSONArray("libraries")
            if (libs != null) for (i in 0 until libs.length()) {
                val lib = libs.getJSONObject(i)
                if (!allowed(lib)) continue
                val dl = lib.optJSONObject("downloads")?.optJSONObject("artifact")
                if (dl != null) {
                    val path = dl.optString("path"); val url = dl.optString("url")
                    if (path.isNotBlank() && url.isNotBlank()) downloads += Download("libraries/$path", url, dl.optString("sha1"))
                }
                val natives = lib.optJSONObject("downloads")?.optJSONObject("classifiers")
                val nativeKey = nativeClassifier()
                val nd = natives?.optJSONObject(nativeKey)
                if (nd != null) {
                    val path = nd.optString("path"); val url = nd.optString("url")
                    if (path.isNotBlank() && url.isNotBlank()) downloads += Download("natives/$path", url, nd.optString("sha1"))
                }
            }

            val assetIndex = meta.optJSONObject("assetIndex")
            if (assetIndex != null) {
                val id = assetIndex.optString("id")
                val url = assetIndex.optString("url")
                if (id.isNotBlank() && url.isNotBlank()) {
                    downloads += Download("assets/indexes/$id.json", url, assetIndex.optString("sha1"))
                    val indexText = downloadText(url)
                    val index = JSONObject(indexText).optJSONObject("objects")
                    if (index != null) for (key in index.keys()) {
                        val hash = index.getJSONObject(key).optString("hash")
                        if (hash.length >= 2) downloads += Download("assets/objects/${hash.take(2)}/$hash", "https://resources.download.minecraft.net/${hash.take(2)}/$hash", hash)
                    }
                }
            }

            val unique = downloads.distinctBy { it.path }
            unique.forEachIndexed { index, item ->
                onProgress(Progress(index, unique.size, item.path))
                val target = File(root, item.path)
                if (!isValid(target, item.sha1)) downloadTo(URL(item.url), target, item.sha1)
            }
            File(root, "installed.ok").writeText("${System.currentTimeMillis()}\n${version.id}")
            onProgress(Progress(unique.size, unique.size, "Готово"))
            root
        }
    }

    private fun allowed(lib: JSONObject): Boolean {
        val rules = lib.optJSONArray("rules") ?: return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i); val action = r.optString("action")
            val os = r.optJSONObject("os")?.optString("name")
            val matches = os == null || os == "linux"
            if (matches) allowed = action == "allow"
        }
        return allowed
    }

    private fun nativeClassifier(): String = "natives-linux"

    private fun downloadText(url: String): String =
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("User-Agent", "LunarLauncher/1.0")
            try { if (responseCode !in 200..299) error("HTTP $responseCode"); inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
            finally { disconnect() }
        }

    data class Download(val path: String, val url: String, val sha1: String?)

    private fun isValid(file: File, sha1: String?): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        if (sha1.isNullOrBlank()) return true
        return sha1.equals(sha1(file), true)
    }

    private fun sha1(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadTo(url: URL, target: File, expectedSha1: String?) {
        target.parentFile?.mkdirs(); val tmp = File(target.parentFile, target.name + ".part")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 120000; c.setRequestProperty("User-Agent", "LunarLauncher/1.0")
        try { if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}: $url"); c.inputStream.use { input -> FileOutputStream(tmp).use { output -> input.copyTo(output) } }; if (expectedSha1 != null && !expectedSha1.equals(sha1(tmp), true)) { tmp.delete(); error("SHA-1 не совпадает: ${target.path}") }
            if (!tmp.renameTo(target)) error("Не удалось сохранить ${target.name}") }
        finally { c.disconnect(); if (tmp.exists() && !target.exists()) tmp.delete() }
    }
}
