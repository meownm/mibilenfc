# Build & Gradle DSL

This project uses the **Groovy DSL** (`*.gradle`) exclusively. Do not introduce Kotlin DSL
syntax in Gradle files.

The Android Gradle Plugin (AGP) version is configured in `settings.gradle` under
`pluginManagement.plugins`, and repository declarations are restricted to
`settings.gradle` (`pluginManagement.repositories` and `dependencyResolutionManagement.repositories`).

## Build
```bash
./gradlew assembleRelease
```

## Dependency pinning (BouncyCastle)
The SDK pins the Android-safe `bcprov-jdk15to18`, `bcpkix-jdk15to18`, and
`bcutil-jdk15to18` versions to avoid Gradle resolving `*-jdk18on` or `*-jdk15on`
artifacts that can trigger Jetifier errors during dependency resolution.

## Groovy examples
```groovy
plugins {
    id 'com.android.application'
}

dependencies {
    implementation 'androidx.core:core:1.13.1'
    implementation project(':sdk')
}

include ':app', ':sdk'
```

## Validation
Run the Gradle sync configuration checks (positive + negative checks):
```bash
./gradlew verifyGradleSyncConfig
```

Run the DSL validation task (positive + negative checks):
```bash
./gradlew verifyGradleDsl
```

Run the SDK dependency resolution checks (positive + negative checks) to ensure
Android-safe BouncyCastle artifacts are pinned and Jetifier-triggering modules
are excluded:
```bash
./gradlew :sdk:verifySdkDependencies
```

Run the integration/unit test suite for the SDK:
```bash
./gradlew :sdk:testDebugUnitTest
```
