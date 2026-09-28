package com.lunar.launcher.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Modrinth client with version/loader filtering and atomic installs. */
object ModrinthContentRepository {
    data class Mod(
        val id: String,
        val title: String,
        val description: String,
        val author: String,
        val downloads: Long,
        val fileUrl: String? = null,
        val fileName: String? = null,
        val versionId: String? = null
    )

    suspend fun search(query: String, version: String, loader: String, limit: Int = 30): Result<List<Mod>> =
        withContext(Dispatchers.IO) { runCatching {
            val facets = "[[\"project_type:mod\"],[\"versions:$version\"],[\"categories:$loader\"]]"
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val f = URLEncoder.encode(facets, "UTF-8")
            val text = request("https://api.modrinth.com/v2/search?query=$q&limit=$limit&facets=$f")
            val arr = JSONObject(text).getJSONArray("hits")
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(Mod(
                        id = o.optString("project_id"), title = o.optString("title"),
                        description = o.optString("description"), author = o.optString("author"),
                        downloads = o.optLong("downloads")
                    ))
                }
            }
        } }

    suspend fun resolveLatest(projectId: String, version: String, loader: String): Result<Mod> =
        withContext(Dispatchers.IO) { runCatching {
            val arr = JSONArray(request("https://api.modrinth.com/v2/project/$projectId/version"))
            for (i in 0 until arr.length()) {
                val v = arr.getJSONObject(i)
                val gameVersions = v.optJSONArray("game_versions") ?: JSONArray()
                val loaders = v.optJSONArray("loaders") ?: JSONArray()
                val gameOk = (0 until gameVersions.length()).any { gameVersions.optString(it) == version }
                val loaderOk = (0 until loaders.length()).any { loaders.optString(it).equals(loader, true) }
                if (!gameOk || !loaderOk) continue
                val files = v.optJSONArray("files") ?: continue
                for (j in 0 until files.length()) {
                    val f = files.getJSONObject(j)
                    if (f.optBoolean("primary", j == 0)) {
                        return@runCatching Mod(
                            projectId, v.optString("name"), v.optString("version_number"),
                            "Modrinth", 0L, f.optString("url"), f.optString("filename"), v.optString("id")
                        )
                    }
                }
            }
            error("Для $version/$loader не найден совместимый файл")
        } }

    suspend fun install(context: Context, mod: Mod, instance: String): Result<File> =
        withContext(Dispatchers.IO) { runCatching {
            val url = mod.fileUrl ?: error("Сначала разрешите версию мода")
            val name = (mod.fileName ?: "${mod.id}.jar").replace(Regex("[^A-Za-z0-9._-]"), "_")
            require(name.endsWith(".jar", true)) { "Modrinth вернул не-JAR файл" }
            val dir = File(context.filesDir, "instances/$instance/mods").apply { mkdirs() }
            val out = File(dir, name)
            val tmp = File(dir, ".$name.part")
            download(url, tmp)
            if (!tmp.renameTo(out)) error("Не удалось завершить установку $name")
            out
        } }

    private fun request(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "LunarLauncher/1.0 (+https://github.com/)")
        return try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun download(url: String, target: File) {
        target.parentFile?.mkdirs()
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 120_000
        c.setRequestProperty("User-Agent", "LunarLauncher/1.0")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        } finally { c.disconnect() }
    }
}
