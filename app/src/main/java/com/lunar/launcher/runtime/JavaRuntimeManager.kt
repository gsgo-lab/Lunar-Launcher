package com.lunar.launcher.runtime

import android.content.Context
import java.io.File

object JavaRuntimeManager {
    fun installed(context: Context): List<RuntimeManager.RuntimeInfo> = File(context.filesDir,"runtimes").listFiles().orEmpty().mapNotNull { root ->
        val java=File(root,"bin/java"); if(java.isFile) RuntimeManager.RuntimeInfo(java,root,root.name) else null
    }
    fun select(context: Context,name:String){context.getSharedPreferences("java",0).edit().putString("selected",name).apply()}
    fun selected(context: Context): String? = context.getSharedPreferences("java",0).getString("selected",null)

    fun selectedRuntime(context: Context): RuntimeManager.RuntimeInfo? =
        selected(context)?.let { name -> RuntimeManager.installed(context).firstOrNull { it.name == name } }
            ?: RuntimeManager.find(context)

    fun installPack(context: Context, pack: AndroidRuntimeManager.Pack, onProgress: (Int) -> Unit = {}): Result<RuntimeManager.RuntimeInfo> =
        AndroidRuntimeManager.install(context, pack, onProgress).map { root ->
            select(context, root.name)
            RuntimeManager.RuntimeInfo(File(root, "bin/java"), root, root.name)
        }
}
