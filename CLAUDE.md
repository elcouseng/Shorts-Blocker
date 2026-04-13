# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common commands

- Build (debug): `./gradlew assembleDebug`
- Install on connected device/emulator: `./gradlew installDebug`
- Full build CI runs: `./gradlew build` (this is the gate the CI workflow runs)
- Format code (required before PR): `./gradlew spotlessApply`
- Verify formatting (what CI runs): `./gradlew spotlessCheck --init-script gradle/init.gradle.kts --no-configuration-cache`
- Release APK: `./gradlew assembleRelease` (output: `app/build/outputs/apk/release/`)
- Logs while testing detectors: `adb logcat | grep -E "(Shorts-Blocker|ShortForm|Detector)"`

There are **no unit or instrumentation tests** in this repo — `src/test` and `src/androidTest` do not exist. Don't invent test commands. CI is lint + build only (`.github/workflows/ci.yml`).

## Toolchain

- **JDK 21** is required. The `docs/getting-started.md` says "17+" but the app module targets `JavaVersion.VERSION_21` and CI uses Temurin 21. Building on JDK 17 will fail.
- **AGP 9** — the project was recently migrated. There is a known unresolved issue in `app/build.gradle.kts` (the `androidComponents { onVariants { ... } }` block) around custom APK output filenames; AGP 9 removed `outputFile` manipulation. See the TODO/FIXME comment in that file before touching variant outputs.
- compileSdk 36, minSdk 24, targetSdk 36, namespace `dev.atick.shorts`.
- Version catalog is `libs` (`gradle/libs.versions.toml`). All plugin/dependency aliases route through it — don't hardcode versions.
- Release signing reads `keystore.properties` at the repo root and silently falls back to the debug key if missing. A missing keystore is **not** a build failure.

## Architecture

Single Gradle module (`:app`). MVVM on top of an Android Accessibility Service. Two halves to understand:

### 1. The blocker (background)

`services/ShortFormContentBlockerService.kt` is an `AccessibilityService` that receives UI events from any tracked app, dispatches them to a per-package detector, and performs `GLOBAL_ACTION_BACK` when the detector says "this is short-form content." A cooldown prevents repeated back-presses.

Detectors implement `services/detectors/ShortFormContentDetector.kt`. Each detector owns a single package (`getPackageName()`) and inspects the `AccessibilityNodeInfo` tree to recognize short-form UI. Current implementations: `YouTubeShortsDetector`, `InstagramReelsDetector`. The detection convention used throughout the codebase:

- BFS the node tree with an `ArrayDeque`
- Cap traversal (~50–150 nodes) for performance
- Compare `viewIdResourceName?.lowercase()` against substrings (e.g. `"reel_player"`, `"shorts_container"`)
- Avoid relying on visible text (locale-dependent)

**Adding a new platform requires editing three places** — the new detector file, the `detectors` map in `ShortFormContentBlockerService`, and `utils/PackageConstants.kt` (both the package constant and the `AVAILABLE_PACKAGES` list). The UI auto-renders from `AVAILABLE_PACKAGES`. `docs/getting-started.md` has a worked TikTok example.

### 2. The UI (foreground)

Jetpack Compose + Material 3, MVVM. `MainScreen`/`OnboardingScreen`/`ProminentDisclosureScreen` ↔ `MainViewModel`/`OnboardingViewModel`. Persistent state lives in `utils/UserPreferencesProvider.kt` (DataStore Preferences) — this is where the set of enabled tracked packages is stored. `AccessibilityServiceManager` checks/opens system accessibility settings.

The UI does not communicate with the service via IPC. The service reads the same DataStore preferences to know which packages the user has enabled.

### Other infra

- **Logging**: Timber, debug builds only. Use `Timber.i/d/v/w/e` — no `Log.*` calls.
- **Firebase**: Analytics + Crashlytics are wired in (`google-services.json` is checked in). The README claims "no internet permission / zero telemetry" — be aware of that mismatch if you touch Firebase code.
- **No DI framework** — detectors are constructed directly in the service's `detectors` map.

## Workflow gotchas

- **Accessibility services do not hot-reload.** After any code change to the service or detectors, you must `installDebug`, then go into Android Settings → Accessibility → Shorts Blocker, toggle it off and back on. Otherwise the old service code keeps running and you'll waste time debugging.
- Emulators don't ship YouTube/Instagram — detector work needs a real device.
- Spotless will fail CI if formatting drifts. Run `./gradlew spotlessApply` before committing. The Spotless config lives in `spotless/` (copyright headers) and is applied via `gradle/init.gradle.kts`.
- All Kotlin files must carry the Apache 2.0 copyright header from `spotless/copyright.kt` — Spotless enforces it.
