# Lunar Launcher 1.0 — Android Java runtime build

Этот исходник предназначен для сборки ARM64 APK, который содержит настоящий мобильный OpenJDK 21, Android GLFW compatibility JAR и native ARM64 runtime-библиотеки.

## Сборка

1. Создай новый GitHub repository.
2. Загрузи **содержимое папки `src`** в корень репозитория.
3. Открой **Actions → Build Lunar Launcher → Run workflow**.
4. После успешной сборки открой artifact `Lunar-Launcher-1.0-ready-arm64`.
5. Скачай `Lunar-Launcher-1.0-debug-arm64.apk` и установи на ARM64 Android.

CI не считает сборку успешной, если отсутствуют:

- `assets/runtime/arm64/bin/java`
- `libjli.so`
- `libjvm.so`
- Android ARM64 `.so`
- `lwjgl-glfw-classes.jar`

## Почему runtime не лежит в Git

JRE и native-библиотеки занимают сотни мегабайт и собираются/получаются CI-пайплайном. Сам исходник остаётся небольшим, а итоговый APK получает реальные runtime-файлы во время GitHub Actions.

## Что запускается

Lunar скачивает официальные Minecraft Java client/libraries/assets по Mojang version manifest. Пиратский client.jar в проект не включён.

Для Android ARM64 используется настоящий мобильный OpenJDK 21 и Android-совместимый GLFW/LWJGL слой. Официальный CI исходного Android launcher-проекта также получает JRE 21 таким способом и отдельно собирает `jre_lwjgl3glfw`.

## Важно

Этот проект ориентирован на `arm64-v8a`. На устройстве с другой ABI этот APK использовать нельзя.
