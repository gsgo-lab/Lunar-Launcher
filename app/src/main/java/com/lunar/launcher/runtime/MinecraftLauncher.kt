package com.lunar.launcher.runtime

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.lunar.launcher.GameActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Builds the launch plan from the official version.json and starts GameActivity,
 * which runs the JVM inside the :game process (see NativeJvm / lunar_jvm.cpp).
 */
object MinecraftLauncher {
    data class Config(
        val version: String,
        val javaHome: File,
        val gameDir: File,
        val clientJar: File,
        val versionJson: File,
        val librariesDir: File,
        val nativesDir: File,
        val assetsDir: File,
        val username: String = "LunarPlayer",
        val uuid: String = UUID.nameUUIDFromBytes("LunarPlayer".toByteArray()).toString().replace("-", ""),
        val accessToken: String = "0",
        val userType: String = "msa"
    )

    /** javaRoot = runtime directory that contains lib/; args exclude argv[0]. */
    data class Plan(val javaRoot: File, val gameDir: File, val log: File, val args: List<String>, val env: Map<String, String>)

    fun defaultPaths(context: Context, version: String): Triple<File, File, File> {
        val root = File(context.filesDir, "instances/$version")
        val java = File(context.filesDir, "runtimes/java21/bin/java")
        return Triple(java, root, File(root, "client.jar"))
    }

    fun launch(context: Context, config: Config): Result<Unit> = runCatching {
        val plan = buildPlan(context, config).getOrThrow()
        // The plan holds the access token: private app dir, deleted by GameActivity right after reading.
        val file = File(config.gameDir, "lunar-plan.json")
        file.writeText(JSONObject().apply {
            put("javaRoot", plan.javaRoot.absolutePath)
            put("gameDir", plan.gameDir.absolutePath)
            put("log", plan.log.absolutePath)
            put("args", JSONArray(plan.args))
            put("env", JSONArray(plan.env.map { "${it.key}=${it.value}" }))
        }.toString())
        context.startActivity(
            Intent(context, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_PLAN, file.absolutePath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    fun buildPlan(context: Context, config: Config): Result<Plan> = runCatching {
        require(config.javaHome.exists()) { "Android Java runtime не найден" }
        require(config.clientJar.isFile) { "Minecraft client.jar не найден" }
        require(config.versionJson.isFile) { "version.json не найден" }
        config.gameDir.mkdirs(); config.nativesDir.mkdirs()
        File(config.gameDir, "tmp").mkdirs()

        val javaRoot = resolveJavaRoot(config.javaHome)
        require(hasJli(javaRoot)) { "В runtime нет libjli.so — это не JRE для Android: $javaRoot" }

        val meta = JSONObject(config.versionJson.readText())
        val core = readCore(context)
        val ph = placeholders(context, config, meta)

        val args = ArrayList<String>()
        core.jvmArgs.forEach { args += it }
        if (args.none { it.startsWith("-Xmx") }) args += "-Xmx${defaultXmxMb(context)}M"
        args += "-Djava.io.tmpdir=${File(config.gameDir, "tmp").absolutePath}"
        args += "-Duser.home=${config.gameDir.absolutePath}"

        val jvm = meta.optJSONObject("arguments")?.optJSONArray("jvm")
        if (jvm != null) addArgumentList(jvm, args, ph)
        else { args += "-Djava.library.path=${config.nativesDir.absolutePath}"; args += "-cp"; args += ph.getValue("classpath") }
        if (!args.contains("-cp") && !args.contains("-classpath")) { args += "-cp"; args += ph.getValue("classpath") }
        if (args.none { it.startsWith("-Djava.library.path=") }) args += "-Djava.library.path=${context.applicationInfo.nativeLibraryDir}${File.pathSeparator}${config.nativesDir.absolutePath}"
        if (args.none { it.startsWith("-Dorg.lwjgl.librarypath=") }) args += "-Dorg.lwjgl.librarypath=${context.applicationInfo.nativeLibraryDir}${File.pathSeparator}${config.nativesDir.absolutePath}"

        args += meta.getString("mainClass")

        val game = meta.optJSONObject("arguments")?.optJSONArray("game")
        when {
            game != null -> addArgumentList(game, args, ph)
            meta.has("minecraftArguments") ->
                meta.getString("minecraftArguments").split(' ').filter { it.isNotBlank() }.forEach { args += substitute(it, ph) }
            else -> error("В version.json нет ни arguments, ни minecraftArguments")
        }

        val env = LinkedHashMap<String, String>()
        core.env.forEach { (k, v) -> env[k] = v }
        val libDirs = listOf(config.nativesDir, File(javaRoot, "lib"), File(javaRoot, "lib/server"), File(javaRoot, "lib/jli"))
            .filter { it.isDirectory }.map { it.absolutePath }
        val nativeAppDir = context.applicationInfo.nativeLibraryDir
        env["LD_LIBRARY_PATH"] = (listOf(nativeAppDir) + libDirs + listOfNotNull(env["LD_LIBRARY_PATH"])).joinToString(File.pathSeparator)
        env["POJAV_NATIVEDIR"] = nativeAppDir
        env["LUNAR_NATIVEDIR"] = nativeAppDir
        env["LUNAR_LAUNCHER"] = "1.0"
        env["POJAV_NATIVEDIR"] = nativeAppDir
        env["LUNAR_NATIVEDIR"] = nativeAppDir
        env["LIBGL_NOERROR"] = "1"
        env["LIBGL_NORMALIZE"] = "1"

        val log = File(config.gameDir, "lunar-launch.log")
        log.appendText("\n--- Lunar launch ${System.currentTimeMillis()} ${config.version} ---\n")
        Plan(javaRoot, config.gameDir, log, args, env)
    }

    // ---------- java runtime ----------
    private fun resolveJavaRoot(javaHome: File): File =
        if (javaHome.isDirectory) javaHome else javaHome.parentFile?.parentFile ?: javaHome

    private val jliCandidates = listOf(
        "lib/libjli.so", "lib/jli/libjli.so", "jre/lib/aarch64/jli/libjli.so", "jre/lib/aarch64/libjli.so",
        "jre/lib/arm/jli/libjli.so", "jre/lib/arm/libjli.so", "jre/lib/amd64/jli/libjli.so", "jre/lib/i386/jli/libjli.so"
    )
    private fun hasJli(root: File) = jliCandidates.any { File(root, it).isFile }

    private fun defaultXmxMb(context: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return (mi.totalMem / 1024 / 1024 / 3).coerceIn(512, 3072)
    }

    // ---------- graphics/input core pack: filesDir/core/core.json ----------
    private data class Core(val jvmArgs: List<String>, val env: Map<String, String>)

    /** core.json: {"jvmArgs":["-D..."],"env":{"KEY":"VALUE"}}; ${core_dir} and ${files_dir} are substituted. */
    private fun readCore(context: Context): Core {
        val dir = File(context.filesDir, "core")
        val f = File(dir, "core.json")
        if (!f.isFile) return Core(emptyList(), emptyMap())
        val sub = { s: String -> s.replace("\${core_dir}", dir.absolutePath).replace("\${files_dir}", context.filesDir.absolutePath) }
        val j = JSONObject(f.readText())
        val a = j.optJSONArray("jvmArgs") ?: JSONArray()
        val e = j.optJSONObject("env") ?: JSONObject()
        return Core(
            (0 until a.length()).map { sub(a.getString(it)) },
            e.keys().asSequence().associateWith { sub(e.getString(it)) }
        )
    }

    // ---------- classpath / arguments ----------
    private fun buildClasspath(context: Context, meta: JSONObject, c: Config): String {
        val lwjgl = LwjglManager.get(context)
        val replaceLwjgl = lwjgl.mode != LwjglManager.Mode.AUTO
        val out = LinkedHashSet<String>()
        val libs = meta.optJSONArray("libraries") ?: JSONArray()
        for (i in 0 until libs.length()) {
            val lib = libs.getJSONObject(i)
            if (!ruleAllows(lib.optJSONArray("rules"))) continue
            // Official LWJGL natives are desktop builds; the core supplies Android-patched LWJGL instead.
            if (replaceLwjgl && lib.optString("name").startsWith("org.lwjgl")) continue
            val path = lib.optJSONObject("downloads")?.optJSONObject("artifact")?.optString("path") ?: continue
            if (path.isNotBlank()) out += File(c.librariesDir, path).absolutePath
        }
        val bundledRuntimeLwjgl = File(context.filesDir, "components/lwjgl/lwjgl-glfw-classes.jar")
        if (bundledRuntimeLwjgl.isFile) out += bundledRuntimeLwjgl.absolutePath
        when (lwjgl.mode) {
            LwjglManager.Mode.BUNDLED -> out += LwjglManager.customJars(lwjgl.copy(customDir = LwjglManager.bundledDir(context, lwjgl.version))).map { it.absolutePath }
            LwjglManager.Mode.CUSTOM -> out += LwjglManager.customJars(lwjgl).map { it.absolutePath }
            LwjglManager.Mode.AUTO -> Unit
        }
        out += c.clientJar.absolutePath
        return out.joinToString(File.pathSeparator)
    }

    private fun placeholders(context: Context, c: Config, meta: JSONObject): Map<String, String> = mapOf(
        "auth_player_name" to c.username,
        "auth_uuid" to c.uuid,
        "auth_access_token" to c.accessToken,
        "auth_session" to c.accessToken,
        "auth_xuid" to "0",
        "clientid" to "",
        "user_type" to c.userType,
        "user_properties" to "{}",
        "version_name" to c.version,
        "version_type" to (meta.optString("type").ifBlank { "release" }),
        "game_directory" to c.gameDir.absolutePath,
        "assets_root" to c.assetsDir.absolutePath,
        "game_assets" to c.assetsDir.absolutePath,
        "assets_index_name" to (meta.optJSONObject("assetIndex")?.optString("id") ?: meta.optString("assets")),
        "natives_directory" to c.nativesDir.absolutePath,
        "library_directory" to c.librariesDir.absolutePath,
        "classpath_separator" to File.pathSeparator,
        "launcher_name" to "Lunar Launcher",
        "launcher_version" to "1.0",
        "classpath" to buildClasspath(context, meta, c)
    )

    private val placeholderRegex = Regex("\\$\\{([a-z_]+)}")
    private fun substitute(token: String, ph: Map<String, String>) =
        placeholderRegex.replace(token) { ph[it.groupValues[1]] ?: "" }

    private fun addArgumentList(arr: JSONArray, out: MutableList<String>, ph: Map<String, String>) {
        for (i in 0 until arr.length()) {
            val item = arr.get(i)
            if (item is JSONObject) {
                if (!ruleAllows(item.optJSONArray("rules"))) continue
                val v = item.opt("value")
                if (v is JSONArray) for (j in 0 until v.length()) out += substitute(v.optString(j), ph)
                else if (v != null) out += substitute(v.toString(), ph)
            } else out += substitute(item.toString(), ph)
        }
    }

    /**
     * Mojang rule semantics: default deny, last matching rule wins.
     * Rules with "features" (demo mode, custom resolution, quick play…) never match: we enable none of them.
     */
    private fun ruleAllows(rules: JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            if (r.optJSONObject("features") != null) continue
            val os = r.optJSONObject("os")
            val matches = os == null || (
                os.optString("name", "linux") == "linux" &&
                    os.optString("arch").let { it.isEmpty() || it == AndroidRuntimeManager.abi() }
                )
            if (matches) allowed = r.optString("action") == "allow"
        }
        return allowed
    }
}
