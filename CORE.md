# Lunar Core — что сделано и что нужно подключить

## Сделано в этом архиве (код, не проверен компиляцией)
- `app/src/main/cpp/lunar_jvm.cpp` — запуск JVM внутри процесса через `dlopen(libjli.so)` + `JLI_Launch`
  (обход запрета exec из app data на Android 10+), перенаправление stdout/stderr в лог, `LD_LIBRARY_PATH`
  через `android_update_LD_LIBRARY_PATH`, передача `Surface` (`lunar_get_window()` для GLFW/EGL-моста).
- `GameActivity` — отдельный процесс `:game`, SurfaceView, после выхода JVM процесс убивается.
- `MinecraftLauncher` — план запуска из официального `version.json`: исправлены подстановки внутри строк
  (`-Djava.library.path=${natives_directory}` раньше не заменялось), правила `features` (раньше мог
  включиться `--demo`), архитектура в правилах, LWJGL от Mojang заменяется на патченый, если режим не AUTO.

## Что нужно положить (это и есть «ядро Pojav»)
1. **Runtime** — OpenJDK 8/17/21 под Android arm64 → `filesDir/runtimes/<name>/` (`lib/libjli.so`, `lib/server/libjvm.so`).
2. **Патченый LWJGL 3** (jar) → `filesDir/lwjgl/<версия>/`, режим в разделе LWJGL: Bundled/Custom.
3. **Графический слой** (мост GLFW→EGL, gl4es/ANGLE/Zink, OpenAL) → `filesDir/core/`, описать в `core/core.json`:
```json
{
  "jvmArgs": ["-Dorg.lwjgl.librarypath=${core_dir}/lib", "-Dorg.lwjgl.opengl.libname=${core_dir}/lib/libgl4es.so"],
  "env": { "LD_LIBRARY_PATH": "${core_dir}/lib" }
}
```
Точные `-D` параметры зависят от выбранного набора компонентов — берите их из его документации.

## Важное ограничение
Готовые `libpojavexec.so` и патченый LWJGL из PojavLauncher привязаны к Java-классам
`net.kdt.pojavlaunch.*` (имена JNI-функций). Поэтому проще всего взять их проект как базу, а этот код
использовать как основу лаунчерной части. Просто «положить .so» без этих классов не заработает.
Лицензия PojavLauncher — LGPL-3.0: сохраняйте её условия.

## Сборка
Нужны Android SDK + NDK + CMake 3.22.1: `gradle assembleDebug`. На Termux нативная сборка обычно не идёт —
собирайте на ПК или в CI. При первых запусках смотрите `instances/<версия>/lunar-launch.log` и `adb logcat -s LunarCore`.
