package com.lunar.launcher.runtime

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Installs a REAL mobile JRE/runtime pack supplied by the user or a trusted release server.
 * A runtime pack is a zip containing <root>/bin/java and native libraries.
 * The launcher never accepts a desktop JDK as a substitute.
 */
object AndroidRuntimeManager {
    data class Pack(
        val id: String,
        val version: String,
        val url: String,
        val sha256: String,
        val sizeBytes: Long,
        val abi: String
    )

    fun abi(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "arm"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> "unknown"
    }

    fun root(context: Context, id: String): File = File(context.filesDir, "runtimes/$id")

    fun javaExecutable(context: Context, id: String): File = File(root(context, id), "bin/java")

    fun isUsable(context: Context, id: String): Boolean {
        val java = javaExecutable(context, id)
        return java.isFile && java.canRead() && isNativeAbiDirectory(root(context, id))
    }

    fun install(context: Context, pack: Pack, onProgress: (Int) -> Unit = {}): Result<File> = runCatching {
        require(pack.abi == abi()) { "Runtime ABI ${pack.abi} не подходит устройству ${abi()}" }
        require(pack.url.startsWith("https://")) { "Runtime URL должен использовать HTTPS" }
        require(pack.sha256.matches(Regex("[A-Fa-f0-9]{64}"))) { "Неверный SHA-256 runtime" }

        val base = File(context.filesDir, "runtime-downloads").apply { mkdirs() }
        val archive = File(base, "${pack.id}.zip.part")
        download(pack.url, archive, pack.sizeBytes, onProgress)
        require(sha256(archive).equals(pack.sha256, ignoreCase = true)) { "SHA-256 runtime не совпадает" }

        val target = root(context, pack.id)
        val staging = File(context.filesDir, "runtime-staging/${pack.id}-${System.nanoTime()}").apply { mkdirs() }
        unzipSafe(archive, staging)
        val actualRoot = normalizeRoot(staging)
        val java = File(actualRoot, "bin/java")
        require(java.isFile) { "В runtime отсутствует bin/java" }
        makeExecutable(java)
        actualRoot.listFiles().orEmpty().forEach { if (it.isDirectory) markExecutables(it) }
        require(isNativeAbiDirectory(actualRoot)) { "Runtime не содержит ожидаемые native/lib каталоги" }

        target.parentFile?.mkdirs()
        if (target.exists()) target.deleteRecursively()
        if (!actualRoot.renameTo(target)) {
            copyTree(actualRoot, target)
            staging.deleteRecursively()
        }
        archive.delete()
        target
    }

    private fun download(urlString: String, out: File, expectedSize: Long, onProgress: (Int) -> Unit) {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        conn.connect()
        if (conn.responseCode !in 200..299) error("Runtime download HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
        conn.inputStream.use { input ->
            FileOutputStream(out).use { output ->
                val buffer = ByteArray(1024 * 128)
                var done = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                }
            }
        }
        conn.disconnect()
    }

    private fun unzipSafe(zip: File, dest: File) {
        ZipInputStream(FileInputStream(zip).buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val clean = entry.name.replace('\\', '/')
                require(!clean.startsWith("/") && !clean.split('/').contains("..")) { "Небезопасный путь в runtime archive" }
                val out = File(dest, clean).canonicalFile
                require(out.path.startsWith(dest.canonicalPath + File.separator)) { "Path traversal" }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                }
            }
        }
    }

    private fun normalizeRoot(staging: File): File {
        if (File(staging, "bin/java").isFile) return staging
        val dirs = staging.listFiles()?.filter { it.isDirectory }.orEmpty()
        return dirs.firstOrNull { File(it, "bin/java").isFile } ?: staging
    }

    private fun isNativeAbiDirectory(root: File): Boolean =
        File(root, "lib").isDirectory || File(root, "lib/aarch64").isDirectory ||
            File(root, "lib/arm64").isDirectory || File(root, "jre/lib").isDirectory

    private fun makeExecutable(file: File) { file.setExecutable(true, false) }

    private fun markExecutables(dir: File) {
        dir.listFiles().orEmpty().forEach { f ->
            if (f.isDirectory) markExecutables(f) else if (f.name == "java" || f.name.endsWith(".so")) f.setExecutable(true, false)
        }
    }

    private fun copyTree(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles().orEmpty().forEach { copyTree(it, File(dst, it.name)) }
        } else src.copyTo(dst, overwrite = true)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 128)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
