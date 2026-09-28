package com.lunar.launcher.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lunar's own Minecraft Java version catalog.
 *
 * The catalog is populated from Mojang's public Java Edition version manifest,
 * then cached locally so the launcher can still show previously downloaded
 * versions while offline.
 */
object MinecraftVersionRepository {
    private const val MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val CACHE_NAME = "minecraft-version-manifest-v2.json"

    data class Version(
        val id: String,
        val type: String,
        val releaseTime: String,
        val manifestUrl: String
    ) {
        val typeLabel: String
            get() = when (type) {
                "release" -> "Release"
                "snapshot" -> "Snapshot / Beta"
                "old_beta" -> "Beta"
                "old_alpha" -> "Alpha"
                else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
            }

        val dateLabel: String
            get() = runCatching {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                val date: Date = parser.parse(releaseTime) ?: return@runCatching releaseTime.take(10)
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)
            }.getOrElse { releaseTime.take(10) }
    }

    data class Catalog(
        val latestRelease: String?,
        val latestSnapshot: String?,
        val versions: List<Version>,
        val fromCache: Boolean
    )

    suspend fun load(context: Context, forceRefresh: Boolean = false): Result<Catalog> =
        withContext(Dispatchers.IO) {
            val cache = File(context.filesDir, CACHE_NAME)

            if (!forceRefresh && cache.isFile) {
                parse(cache.readText()).map { it.copy(fromCache = true) }
                    .onSuccess { cached ->
                        // Refresh silently in the background on the next explicit refresh.
                    }
                    .let { cachedResult ->
                        if (cachedResult.isSuccess) return@withContext cachedResult
                    }
            }

            runCatching {
                val json = downloadManifest()
                cache.parentFile?.mkdirs()
                cache.writeText(json)
                parse(json).getOrThrow().copy(fromCache = false)
            }.recoverCatching {
                if (cache.isFile) parse(cache.readText()).getOrThrow().copy(fromCache = true)
                else throw it
            }
        }

    private fun downloadManifest(): String {
        val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "LunarLauncher/1.0")
        }
        return connection.use { c ->
            if (c.responseCode !in 200..299) {
                throw IllegalStateException("Minecraft manifest HTTP ${c.responseCode}")
            }
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    private fun parse(json: String): Result<Catalog> = runCatching {
        val root = JSONObject(json)
        val latest = root.optJSONObject("latest")
        val array = root.getJSONArray("versions")
        val list = buildList(array.length()) {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    Version(
                        id = item.getString("id"),
                        type = item.optString("type", "release"),
                        releaseTime = item.optString("releaseTime", ""),
                        manifestUrl = item.getString("url")
                    )
                )
            }
        }
        Catalog(
            latestRelease = latest?.optString("release")?.takeIf { it.isNotBlank() },
            latestSnapshot = latest?.optString("snapshot")?.takeIf { it.isNotBlank() },
            versions = list,
            fromCache = false
        )
    }
}
