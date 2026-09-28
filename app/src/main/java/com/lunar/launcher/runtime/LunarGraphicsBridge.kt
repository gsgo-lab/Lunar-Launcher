package com.lunar.launcher.runtime

import android.view.Surface

/**
 * Native Android graphics bridge owned by Lunar.
 *
 * This is a low-level Surface -> EGL/GLES bridge. It is deliberately kept
 * independent from any third-party launcher package/class names.
 *
 * It does NOT pretend to be a complete LWJGL implementation: a Minecraft
 * build still needs an Android-compatible LWJGL layer that calls this bridge.
 */
object LunarGraphicsBridge {
    init { System.loadLibrary("lunar_graphics") }

    @JvmStatic external fun nativeSetSurface(surface: Surface?)
    @JvmStatic external fun nativeInitEgl(): Boolean
    @JvmStatic external fun nativeSwap()
    @JvmStatic external fun nativeShutdownEgl()

    fun attach(surface: Surface?) = nativeSetSurface(surface)
    fun init(): Boolean = nativeInitEgl()
    fun swap() = nativeSwap()
    fun shutdown() = nativeShutdownEgl()
}
