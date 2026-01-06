# Build & Gradle DSL

This project uses the **Groovy DSL** (`*.gradle`) exclusively. Do not introduce Kotlin DSL
syntax in Gradle files. The root project is **not** an Android project and only declares
Android Gradle Plugin versions in `build.gradle`.

The Android Gradle Plugin (AGP) version is configured in `settings.gradle` under
`pluginManagement.plugins`, and repository declarations are restricted to
`settings.gradle` (`pluginManagement.repositories` and `dependencyResolutionManagement.repositories`).
`build.gradle` must declare matching AGP versions, and the validation script enforces
this alignment.

## Build
```bash
./gradlew assembleRelease
```

## Dependency pinning (BouncyCastle)
The SDK pins the Android-safe `bcprov-jdk15to18`, `bcpkix-jdk15to18`, and
`bcutil-jdk15to18` versions to avoid Gradle resolving `*-jdk18on` or `*-jdk15on`
artifacts that can trigger Jetifier errors during dependency resolution and
ship Java 21 bytecode that is incompatible with the Android toolchain.

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
Run the namespace checks to ensure distinct module namespaces and SDK packages:
```bash
./gradlew verifyModuleNamespaces
./gradlew verifyDistinctNamespaces
./gradlew verifySdkPackageDeclarations
```

Run the SDK dependency resolution checks (positive + negative checks) to ensure
Android-safe BouncyCastle artifacts are pinned and Jetifier-triggering modules
are excluded:
```bash
./gradlew :sdk:verifySdkDependencies
```

Run the JMRTD integration dependency resolution check to confirm the resolved
graph uses Android-safe BouncyCastle modules:
```bash
./gradlew :sdk:verifySdkJmrtdResolution
```

Run the integration/unit test suite for the SDK:
```bash
./gradlew :sdk:testDebugUnitTest
```

Run the root checks and integration builds (positive + negative coverage):
```bash
./scripts/tests/gradle_checks.sh
```
