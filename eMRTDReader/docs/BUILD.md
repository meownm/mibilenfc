# Build & Gradle DSL

This project uses the **Groovy DSL** (`*.gradle`) exclusively. Do not introduce Kotlin DSL
syntax in Gradle files.

## Build
```bash
./gradlew assembleRelease
```

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
Run the DSL validation task (positive + negative checks):
```bash
./gradlew verifyGradleDsl
```

Run the integration/unit test suite for the SDK:
```bash
./gradlew :sdk:testDebugUnitTest
```
