# Third-party components

Lunar Launcher is an independent launcher UI/application. The build may retrieve and package open-source runtime components under their original licenses.

## Android OpenJDK runtime
The CI workflow retrieves a mobile OpenJDK runtime from the public android-openjdk-build-multiarch project. Its upstream license/copyright files are retained in the generated runtime pack.

## Android GLFW Java compatibility layer and native runtime libraries
The CI workflow retrieves/builds the required Android-compatible GLFW/LWJGL runtime components from the public PojavLauncher open-source project. The generated APK must retain the upstream LGPL-3.0 and component notices supplied by that project.

These components are runtime dependencies; Lunar branding and application package names remain independent.
