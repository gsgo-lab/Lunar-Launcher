# Lunar Core graphics backends

The built-in `gles3` backend is a native Android Surface -> EGL/GLES3 bridge.

`gl4es`, `ANGLE`, and `Zink` are external native backends. They are not fabricated
or represented by empty libraries. To enable one, place its legally redistributable
ARM64 native libraries in `filesDir/core/lib/` and configure `core.json`.

The bridge does not implement the Java LWJGL API. A compatible LWJGL build must call
the Lunar native surface/EGL bridge.

Native entry points:
- `lunar_get_window()`
- `LunarGraphicsBridge.nativeSetSurface`
- `LunarGraphicsBridge.nativeInitEgl`
- `LunarGraphicsBridge.nativeSwap`
- `LunarGraphicsBridge.nativeShutdownEgl`
