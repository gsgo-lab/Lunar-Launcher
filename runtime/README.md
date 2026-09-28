# Lunar mobile Java runtime

The Android launcher requires a real Android-targeted OpenJDK runtime, not the desktop JDK and not Android ART.

For ARM64 builds, the GitHub Actions workflow downloads the OpenJDK 21 Android artifact used by the public multi-architecture build pipeline and places the runtime payload under `app/src/main/assets/runtime/arm64/` before Gradle packages the APK.

The runtime is kept separate from Minecraft game files. Minecraft client files are downloaded from Mojang's official version metadata by the launcher after authentication.

If a project maintainer redistributes the runtime, the applicable OpenJDK and third-party license/copyright notices must be shipped with it.
