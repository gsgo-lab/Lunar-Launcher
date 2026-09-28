# Lunar Launcher 1.0 — Finalized source

This archive is the cleaned and hardened source of Lunar Launcher 1.0.

## What was finalized

- Real Microsoft device-code login flow: Microsoft OAuth → Xbox Live → XSTS → Minecraft Services.
- Microsoft account data encrypted with Android Keystore.
- Official Mojang Java Edition version manifest and cached catalog.
- Official Minecraft client/library/asset downloader with SHA-1 verification and atomic `.part` downloads.
- Minecraft launch command builder based on the official `version.json` argument format.
- Runtime detection with explicit Android-runtime validation.
- Modrinth search with exact Minecraft-version/loader resolution before installation.
- Atomic Modrinth JAR installation.
- Persistent graphics settings and renderer toggles.
- Local Crash Doctor heuristics for common Java/LWJGL/Vulkan failures.
- A real lightweight CPU benchmark instead of a fake progress indicator.
- Offline account manager.
- Java and LWJGL manager screens.
- JSON control profiles.

## Important runtime boundary

The source does **not** contain Mojang's proprietary Minecraft files and does not pretend that a desktop JDK is an Android Minecraft runtime.

For a real Android launch, install a compatible mobile runtime under:

`<app files>/runtimes/<name>/bin/java`

That runtime must provide Android-compatible OpenJDK plus the native graphics/input bridge required by the selected Minecraft/LWJGL stack. A normal desktop JDK copied from a PC is not sufficient.

## Build in Termux

From the project root:

```bash
pkg install openjdk-17 gradle -y
export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH
gradle assembleDebug --no-daemon
```

The APK is normally written to:

`app/build/outputs/apk/debug/app-debug.apk`

## Microsoft login

No password or manually copied access token is required. The user starts Microsoft sign-in from the Accounts screen, enters the displayed device code on Microsoft's page, and Lunar completes the Minecraft authentication chain.

## Ely.by

Ely.by still requires an application/client registration and OAuth configuration. This source keeps that provider isolated instead of shipping fake credentials.


## Lunar Core runtime status

This source contains:
- in-process mobile JVM host (`lunar_jvm`);
- Android Surface -> EGL/GLES3 bridge (`lunar_graphics`);
- secure runtime-pack installation and SHA-256 verification;
- core/backend manifest and runtime contract.

It intentionally does not ship fabricated JRE, LWJGL, gl4es, ANGLE, Zink, or OpenAL
binary files. Those components must be built or redistributed from sources/releases whose
licenses permit redistribution.

A complete Java Edition launch additionally requires an Android-compatible patched LWJGL
implementation that targets the Lunar native bridge. The built-in EGL bridge alone is
not a drop-in replacement for the LWJGL Java API.
