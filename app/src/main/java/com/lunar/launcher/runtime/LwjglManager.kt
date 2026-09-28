package com.lunar.launcher.runtime

import android.content.Context
import java.io.File

/** Manages optional LWJGL overrides. The default remains the version shipped by the Minecraft instance. */
object LwjglManager {
    enum class Mode { AUTO, BUNDLED, CUSTOM }
    data class Selection(val mode: Mode, val version: String, val customDir: File?, val experimental: Boolean)

    private const val PREFS = "lwjgl"
    private const val MODE = "mode"
    private const val VERSION = "version"
    private const val DIR = "dir"
    private const val EXPERIMENTAL = "experimental"

    fun get(context: Context): Selection {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = runCatching { Mode.valueOf(p.getString(MODE, Mode.AUTO.name)!!) }.getOrDefault(Mode.AUTO)
        val dir = p.getString(DIR, null)?.let(::File)
        return Selection(mode, p.getString(VERSION, "Auto") ?: "Auto", dir, p.getBoolean(EXPERIMENTAL, false))
    }

    fun save(context: Context, selection: Selection) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(MODE, selection.mode.name)
            .putString(VERSION, selection.version)
            .putString(DIR, selection.customDir?.absolutePath)
            .putBoolean(EXPERIMENTAL, selection.experimental)
            .apply()
    }

    fun bundledDir(context: Context, version: String): File = File(context.filesDir, "lwjgl/$version")

    fun customJars(selection: Selection): List<File> = selection.customDir?.listFiles()
        .orEmpty().filter { it.isFile && it.extension.equals("jar", true) }

    fun validate(context: Context, selection: Selection): String {
        return when (selection.mode) {
            Mode.AUTO -> "Auto: используется LWJGL из официального version.json."
            Mode.BUNDLED -> {
                val dir = bundledDir(context, selection.version)
                if (customJars(Selection(Mode.CUSTOM, selection.version, dir, selection.experimental)).isNotEmpty())
                    "Готово: найдены JAR в ${dir.absolutePath}"
                else "Не найден bundled LWJGL для ${selection.version}."
            }
            Mode.CUSTOM -> {
                val count = customJars(selection).size
                if (count > 0) "Готово: найдено $count LWJGL JAR в ${selection.customDir}" else "В выбранной папке нет .jar файлов."
            }
        }
    }
}
