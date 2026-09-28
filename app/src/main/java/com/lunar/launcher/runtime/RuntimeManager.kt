package com.lunar.launcher.runtime

import android.content.Context
import java.io.File

/** Finds and validates mobile Java runtimes installed by Lunar. */
object RuntimeManager {
    data class RuntimeInfo(val java: File, val root: File, val name: String)

    fun find(context: Context): RuntimeInfo? = installed(context).firstOrNull()

    fun installed(context: Context): List<RuntimeInfo> =
        File(context.filesDir, "runtimes").listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { root ->
                val java = File(root, "bin/java")
                if (java.isFile) RuntimeInfo(java, root, root.name) else null
            }
            .filter { AndroidRuntimeManager.isUsable(context, it.name) }

    fun installHint(context: Context): String =
        "Установите мобильный ARM64/ABI-совместимый runtime. Desktop JDK сюда не подходит: ${File(context.filesDir, "runtimes").absolutePath}"
}
