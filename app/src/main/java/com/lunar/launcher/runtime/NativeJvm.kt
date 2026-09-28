package com.lunar.launcher.runtime

import android.view.Surface

/** JNI bridge to liblunar_jvm.so. Only loaded inside the :game process. */
object NativeJvm {
    init { System.loadLibrary("lunar_jvm") }

    /** Blocks until the JVM exits. env entries are "KEY=VALUE". Returns the JVM exit code. */
    @JvmStatic external fun nativeRun(
        javaHome: String, gameDir: String, logPath: String,
        args: Array<String>, env: Array<String>
    ): Int

    @JvmStatic external fun nativeSetSurface(surface: Surface?)
}
