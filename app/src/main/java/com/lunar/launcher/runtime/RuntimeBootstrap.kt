package com.lunar.launcher.runtime

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** Installs a runtime payload packaged by the build pipeline, if present. */
object RuntimeBootstrap {
    private const val ASSET_ROOT = "runtime/arm64"

    fun ensureBundled(context: Context, id: String = "java21-arm64"): Result<File> = runCatching {
        val target = AndroidRuntimeManager.root(context, id)
        if (AndroidRuntimeManager.isUsable(context, id)) return@runCatching target

        val staging = File(context.cacheDir, "runtime-staging-$id").apply {
            deleteRecursively(); mkdirs()
        }
        copyAssets(context, ASSET_ROOT, staging)
        val java = File(staging, "bin/java")
        require(java.isFile) { "В APK нет ARM64 Java runtime (bin/java)" }
        java.setExecutable(true, false)
        staging.walkTopDown().filter { it.isFile && (it.name == "java" || it.name.endsWith(".so")) }
            .forEach { it.setExecutable(true, false) }

        target.deleteRecursively()
        target.parentFile?.mkdirs()
        require(staging.renameTo(target)) { "Не удалось установить встроенный runtime" }
        installComponentAssets(context)
        target
    }

    private fun installComponentAssets(context: Context) {
        val src = "components/lwjgl/lwjgl-glfw-classes.jar"
        val target = File(context.filesDir, "components/lwjgl/lwjgl-glfw-classes.jar")
        if (!target.isFile) {
            target.parentFile?.mkdirs()
            context.assets.open(src).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        }
    }

    private fun copyAssets(context: Context, assetPath: String, dest: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { input.copyTo(it) }
            }
            return
        }
        dest.mkdirs()
        for (child in children) copyAssets(context, "$assetPath/$child", File(dest, child))
    }
}
