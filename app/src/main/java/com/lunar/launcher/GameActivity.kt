package com.lunar.launcher

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.lunar.launcher.runtime.NativeJvm
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/**
 * Runs Minecraft in the isolated ":game" process. When the JVM exits, the process is killed,
 * so the launcher UI process stays alive and the next launch starts from a clean state.
 */
class GameActivity : Activity(), SurfaceHolder.Callback {
    companion object { const val EXTRA_PLAN = "plan" }

    private var started = false
    private lateinit var plan: JSONObject

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val planFile = File(intent.getStringExtra(EXTRA_PLAN) ?: return fail("Нет плана запуска"))
        plan = runCatching { JSONObject(planFile.readText()) }.getOrElse { return fail("План запуска повреждён: ${it.message}") }
        planFile.delete() // contains the access token

        val surface = SurfaceView(this).also { it.holder.addCallback(this) }
        setContentView(FrameLayout(this).apply { addView(surface) })
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        NativeJvm.nativeSetSurface(holder.surface)
        LunarGraphicsBridge.attach(holder.surface)
        LunarGraphicsBridge.init()
        if (started) return
        started = true
        thread(name = "lunar-jvm", isDaemon = false) {
            val args = plan.getJSONArray("args").let { a -> Array(a.length()) { a.getString(it) } }
            val env = plan.getJSONArray("env").let { a -> Array(a.length()) { a.getString(it) } }
            val code = runCatching {
                NativeJvm.nativeRun(plan.getString("javaRoot"), plan.getString("gameDir"), plan.getString("log"), args, env)
            }.getOrElse {
                File(plan.getString("log")).appendText("JNI error: ${it}\n"); -1
            }
            File(plan.getString("log")).appendText("--- JVM exit code $code ---\n")
            finishAndRemoveTask()
            Process.killProcess(Process.myPid())
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        LunarGraphicsBridge.shutdown()
        LunarGraphicsBridge.attach(null)
        NativeJvm.nativeSetSurface(null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    private fun fail(msg: String) { setContentView(TextView(this).apply { text = msg; setPadding(48, 48, 48, 48) }) }
}
