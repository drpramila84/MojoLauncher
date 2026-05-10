# Wither Launcher

## Overview
Wither Launcher is an Android launcher for Minecraft: Java Edition, based on [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher). It allows running almost all Minecraft versions (from early alpha to recent snapshots) on Android devices, with support for modloaders like Forge and Fabric, and performance mods like OptiFine.

## Project Type
**Android Mobile Application** — This is NOT a web app. It compiles to an Android APK and runs on Android devices or emulators. It cannot be previewed in a browser.

## Architecture
- **Build System:** Gradle (via `./gradlew` wrapper, Gradle 8.14.3)
- **Languages:** Java (primary), C/C++ (JNI/native), Shell scripts
- **Modules:**
  - `app_pojavlauncher/` — Main Android application module
  - `jre_lwjgl3glfw/` — LWJGL3/GLFW compatibility layer for Android
  - `forge_installer/` — Minecraft Forge installation utility
  - `gl4es/` — OpenGL to OpenGL ES translation layer

## Building
To build a debug APK, run:
```
./gradlew :app_pojavlauncher:assembleDebug
```

The output APK will be in:
`app_pojavlauncher/build/outputs/apk/debug/`

**Note:** Building requires the Android SDK. The Replit environment has Java (GraalVM 19) available, but the Android SDK is not pre-installed. The build will attempt to download Android SDK components automatically via the Gradle Android plugin if `ANDROID_HOME` is configured.

## Key Source Paths
- Main Java source: `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/`
- Native JNI code: `app_pojavlauncher/src/main/jni/`
- Android resources: `app_pojavlauncher/src/main/res/`
- App manifest: `app_pojavlauncher/src/main/AndroidManifest.xml`

## User Preferences
- Follow existing Gradle project structure and conventions
