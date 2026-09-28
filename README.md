# Lunar Launcher 1.0 — Android source

Android launcher UI for Minecraft Java with Lunar theme, profiles, JSON controls, renderer settings, diagnostics, author social links and a runtime-ready launch core.

## Important
This project **does not include Mojang's Minecraft client, Microsoft tokens, proprietary assets, or a Java runtime**. The launcher core can start a prepared Minecraft instance when a compatible Android Java runtime and instance are present in the app sandbox.

Expected runtime layout:

```
filesDir/
  runtimes/java21/bin/java
  instances/1.21.11/client.jar
```

The launch core also supports libraries, natives, assets, username, UUID and access token through `MinecraftLauncher.Config`.

A production launcher still needs a version manifest resolver, library/native downloader, asset downloader, Fabric/Forge/NeoForge installer, and official Microsoft authentication flow. Those components are intentionally separated from the UI so they can be added without rewriting the menu.

## Build

```bash
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug --no-daemon
```
