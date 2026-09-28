# Lunar Launcher — real Android Java runtime

Lunar is designed to launch **Minecraft Java Edition with a real Android ARM64 OpenJDK**, not Android ART and not a desktop JDK.

The source repository itself remains small. The GitHub Actions build fetches the Android OpenJDK 21 ARM64 runtime artifact, places it into the APK assets, and then builds Lunar. This is necessary because the runtime consists of native ARM64 libraries such as `libjvm.so` and `libjli.so` and is much larger than the launcher source.

The launcher then installs the packaged runtime into its private app storage and launches Java with `JAVA_HOME`/native library paths. Minecraft client libraries/assets are still obtained separately from official Minecraft version metadata.

## Build

1. Push this project to GitHub.
2. Open **Actions → Build Lunar Launcher → Run workflow**.
3. Download the `Lunar-Launcher-debug-arm64` artifact.

The build intentionally fails if no ARM64 runtime artifact is found, instead of producing an APK that looks complete but cannot start Java.

## Legal

The Android OpenJDK runtime and native graphics components have their own applicable licenses and notices. If you redistribute the resulting APK, keep the required notices for every component actually packaged in it.
