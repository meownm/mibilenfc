# eMRTDReader (MRZ + NFC)

Android app that reads eMRTD (ICAO 9303) documents using:
- MRZ scan (CameraX + ML Kit)
- NFC (IsoDep)
- Secure messaging: PACE (CAN/MRZ) → BAC fallback
- Passive Authentication: verifies SOD signature + DG hashes (no CSCA trust store)

## Requirements
- JDK 17
- Android SDK 33 (platforms;android-33, build-tools;33.x)
- A physical Android device with NFC
- CameraX artifacts (camera-core, camera-camera2, camera-lifecycle, camera-view) via the CameraX BOM
- Gradle repositories: `google()` and `mavenCentral()`
- Tess-two dependency: `com.rmtheis:tess-two:9.1.0`

## Setup (Windows / PowerShell)
1) Create `local.properties` in project root:
```properties
sdk.dir=C\:\Android\sdk
```

You can copy the template:
```powershell
copy local.properties.example local.properties
```

2) Build APK:
```powershell
gradle assembleRelease
```

If you have Gradle Wrapper files (`gradlew`), you can use:
```powershell
.\gradlew assembleRelease
```

APK output:
`app/build/outputs/apk/release/app-release.apk`

## Notes
- Many passports store face photo as JPEG2000. Android `BitmapFactory` can't decode it; in that case the app shows a placeholder.
- Passive Authentication uses JMRTD `SODFile` to read the digest algorithm and data group hashes from the SOD payload.
- Passive Authentication result is **UNKNOWN_CA** when signature and hashes are valid, because CSCA trust store is not bundled.

## Testing
Run the SDK unit test suite (includes MRZ parsing coverage):
```powershell
.\gradlew :sdk:testDebugUnitTest
```

Validate the Gradle sync configuration and Groovy DSL checks:
```powershell
.\gradlew verifyGradleSyncConfig
.\gradlew verifyGradleDsl
```
