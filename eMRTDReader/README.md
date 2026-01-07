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
- The root project is **not** an Android project; it only declares Android Gradle Plugin versions.
- CameraX artifacts:
  - SDK uses only camera-core (and camera-camera2 if needed).
  - App module pins UI/lifecycle dependencies (camera-view, camera-lifecycle) to the same explicit version as camera-core/camera-camera2.
- Module namespaces:
  - App: `com.example.emrtdreader`
  - SDK: `com.example.emrtdreader.sdk`

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
- MRZ scan and NFC screens include a scrollable log panel that appends OCR/MRZ/NFC session events, including per-frame OCR lines prefixed with `ML:` or `TESS:` for troubleshooting, plus passive authentication results without clearing prior entries. Frame-level logs are emitted as `[frame] ts=<epoch_ms> mean=<luma> contrast=<stddev> sharp=<laplacian> engine=<OCR> mrzValid=<bool> mlLen=<len> tessLen=<len>`, and error entries are prefixed with `ERROR:`.
- The SDK exposes `GateMetrics` to capture gate-level quality metrics like brightness mean, contrast standard deviation, Laplacian blur variance, and motion MAD, plus `GateResult` with `GateRejectReason` values for pass/fail gating outcomes.

## Testing
Run the SDK unit test suite (includes MRZ parsing coverage):
```powershell
.\gradlew :sdk:testDebugUnitTest
```

Validate the root-only plugin configuration and run build checks:
```powershell
.\scripts\tests\gradle_checks.sh
```
