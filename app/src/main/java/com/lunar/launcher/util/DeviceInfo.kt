package com.lunar.launcher.util

import android.os.Build

data class DeviceSnapshot(
    val android: String,
    val cpu: String,
    val abi: String,
    val cores: Int,
    val ramMb: Long
)

object DeviceInfo {
    fun read(): DeviceSnapshot {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val ram = Runtime.getRuntime().maxMemory() / 1024 / 1024
        return DeviceSnapshot(
            android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            cpu = Build.HARDWARE.ifBlank { Build.BOARD },
            abi = abi,
            cores = Runtime.getRuntime().availableProcessors(),
            ramMb = ram
        )
    }
}
