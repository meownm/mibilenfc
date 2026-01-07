# Integration

## Add dependency
Use the `sdk` module as an Android library.

Groovy DSL example:
```groovy
dependencies {
    implementation project(':sdk')
}
```

## Namespaces
- App module: `com.example.emrtdreader`
- SDK module: `com.example.emrtdreader.sdk`

## Permissions
- Camera: `android.permission.CAMERA`
- NFC: `android.permission.NFC`
- Internet: optional (for Tesseract traineddata auto-download)

## OCR modes
- Auto (ML-first): ML Kit runs on lightweight MRZ preprocessing (no binarization); if the text does not achieve a perfect MRZ score, Tesseract runs on a scaled + binarized bitmap and the higher-scoring candidate is selected
- Auto mode tie-breaker: when dual OCR candidates tie on confidence, TD3 (passport) is preferred over TD1.
- ML Kit only
- Tesseract only

## Tesseract preprocessing candidates
When Auto mode falls back to Tesseract, the SDK runs a fixed candidate set (`PreprocessParamSet`) and selects the result with the best MRZ validity score. Each candidate defines the adaptive threshold block size, C offset, scale factor, and blur radius:

- (block size 15, C 5, scale 2.0, blur 0)
- (block size 17, C 7, scale 2.25, blur 1)
- (block size 21, C 9, scale 2.5, blur 1)
- (block size 13, C 3, scale 1.75, blur 0)

For each candidate, the same ROI frame is preprocessed, Tesseract runs, and the normalized text is scored using the MRZ scoring rules below. The highest score is selected as the final Tesseract result (and is compared against the ML Kit score in Auto mode).

## Preprocess parameter persistence
To speed up Auto mode, the SDK stores the best-performing `PreprocessParams` per camera and resolution in `SharedPreferences`:

- Preferences file: `mrz_preprocess_params`
- Key format: `preprocess_params:<cameraId>:<width>x<height>`
- The `width`/`height` values are taken after frame rotation is applied, so portrait/landscape orientation maps to distinct entries.

Lifecycle:
- After a successful candidate selection, the best parameter set is persisted for that camera/resolution.
- On subsequent runs, the stored parameters are tried first alongside the default candidate list.
- Stored entries are overwritten when a newer candidate wins. Invalid or missing entries are ignored.

## MRZ scoring rules
The MRZ scorer parses OCR text into TD3 lines and assigns a normalized score (0..1):

- Parse OCR text into exactly two MRZ lines (TD3). If one 88-character line is present, it is split into two 44-character lines.
- Enforce strict formatting: line count = 2, line length = 44, allowed charset `[A-Z0-9<]`.
- Compute the four TD3 checksum validations and return the ratio of successful checks (`validChecks / 4`).

If strict formatting fails, the score is `0.0`.

### MRZ score components
The SDK also exposes a lightweight `MrzScore` data container in `com.example.emrtdreader.sdk.ocr` with component fields for checksum, length, charset, structure, and stability. Call `recalcTotal()` to compute a weighted total (checksum ×10, length ×2, charset ×2, structure ×3, stability ×5) when aggregating custom scoring signals.

## OCR threading
`MrzImageAnalyzer` and the OCR engines use a callback-based contract. OCR results and errors are delivered asynchronously (often on background threads), so UI layers should marshal updates onto the main thread as needed. The analyzer never blocks on OCR completion, allowing continuous frame delivery.

## Scan heartbeat events
`MrzImageAnalyzer.Listener` emits lightweight heartbeat callbacks so the UI can stay responsive even when no MRZ ROI is found or frames are skipped:

- `onFrameProcessed(ScanState.WAITING, "No MRZ ROI detected", timestampMs)` fires when `MrzAutoDetector` returns `null`.
- `onFrameProcessed(ScanState.WAITING, "Frame skipped: interval", timestampMs)` fires when frames are throttled by the analysis interval.
- `onFrameProcessed(ScanState.WAITING, "Frame skipped: OCR in flight", timestampMs)` fires when backpressure skips frames while OCR is running.

These callbacks also trigger `onScanState(ScanState.WAITING, message)` so UI overlays can remain in a "waiting" state without relying on OCR results.

## MRZ scan log window
The MRZ scan activity includes a log window intended for troubleshooting and capture review:

- The log is append-only; new entries are added at the bottom and existing lines are never overwritten.
- Log updates are marshaled onto the main thread to keep UI updates safe.
- Scan-state transitions append timestamped entries (format: `[state] ts=<epoch_ms> <message>`), including:
  - `ML text detected`
  - `Tess text detected`
  - `Waiting for MRZ`
  - `Error: <details>`

## Passive authentication
The SDK verifies SOD signatures and data group hashes using Bouncy Castle. Ensure the
`sdk` module keeps the Bouncy Castle provider and certificate converter classes on the
classpath (e.g. `org.bouncycastle.cert.jcajce.JcaX509CertificateConverter`) so passive
authentication can parse CMS certificates and validate signatures.
