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

## MRZ field model
Use `MrzFields` (`com.example.emrtdreader.sdk.models.MrzFields`) to hold parsed MRZ data in an immutable container:

- `documentNumber`, `birthDateYYMMDD`, `expiryDateYYMMDD`
- `nationality`, `sex`, `surname`, `givenNames`

`MrzFields#toAccessKey()` returns an `AccessKey.Mrz` populated from the document number, birth date, and expiry date.

## MRZ parsing and validation
When you already have normalized MRZ lines (no whitespace, uppercase, filler `<` intact), feed them into
`MrzParserValidator` to detect the format and validate the ICAO checksums:

- Input model: `NormalizedMrz` (`com.example.emrtdreader.sdk.models.NormalizedMrz`) with `List<String> lines`.
- Output model: `MrzParseResult` (`com.example.emrtdreader.sdk.models.MrzParseResult`) containing parsed TD3 fields,
  checksum results, score components, and a `valid` flag that flips to true when all checksums pass.

TD3 passports are parsed into document type, issuing country, surname, given names, document number, nationality,
birth date, sex, expiry date, and personal number. The resulting `MrzFields` are attached to `MrzParseResult.fields`
so callers can derive `AccessKey.Mrz` values directly.

## Permissions
- Camera: `android.permission.CAMERA`
- NFC: `android.permission.NFC`
- Internet: optional (for Tesseract traineddata auto-download)

## OCR modes
- Auto (ML-first): ML Kit runs on lightweight MRZ preprocessing (no binarization); if the text does not achieve a perfect MRZ score, Tesseract runs on a scaled + binarized bitmap and the higher-scoring candidate is selected
- Auto mode tie-breaker: when dual OCR candidates tie on confidence, TD3 (passport) is preferred over TD1.
- ML Kit only
- Tesseract only

## OCR output model
When you need lightweight OCR telemetry, use `OcrOutput` (`com.example.emrtdreader.sdk.models.OcrOutput`) with:

- `rawText`: raw OCR text as emitted by the engine.
- `elapsedMs`: elapsed OCR runtime in milliseconds.
- `whitelistRatio`: ratio of characters that match the MRZ whitelist.
- `ltCount`: count of `<` filler characters in the OCR output.

## MRZ OCR engine interface
When you implement a synchronous MRZ OCR engine, use `MrzOcrEngine` (`com.example.emrtdreader.sdk.ocr.MrzOcrEngine`):

- Input model: `PreprocessedMrz` (`com.example.emrtdreader.sdk.ocr.PreprocessedMrz`) with a preprocessed `Bitmap` and its `rotationDegrees`.
- Output model: `OcrOutput` (`com.example.emrtdreader.sdk.models.OcrOutput`) containing raw text and telemetry.

For Tesseract-based MRZ recognition, `TesseractMrzEngine` (`com.example.emrtdreader.sdk.ocr.TesseractMrzEngine`)
expects already-prepared bitmaps, applies the MRZ whitelist `[A-Z0-9<]`, and disables Tesseract language models
by turning off DAWG dictionaries.

## MRZ formats
The SDK models MRZ formats as TD1, TD2, and TD3. TD2 is treated as a two-line format for access key parsing (document number, date of birth, date of expiry), while normalization and repair currently focus on TD1 and TD3.

## MRZ tracking models
When you need to capture ROI tracking state, use these SDK models:

- `MrzBox` (`com.example.emrtdreader.sdk.models.MrzBox`) stores the tracked ROI bounds as `left`, `top`, `right`, and `bottom`.
- `TrackResult` (`com.example.emrtdreader.sdk.models.TrackResult`) stores tracking state (`stable`, `stableCount`, `jitter`) alongside the tracked `MrzBox`.
- `MrzTracker` (`com.example.emrtdreader.sdk.models.MrzTracker`) tracks successive boxes, applies EMA smoothing (alpha default 0.5), increments stability when IoU ≥ 0.7, and marks results stable after 3 consecutive matches.

## ROI-aware motion/blur metrics
When providing an ROI hint, the SDK computes motion and blur signals within that ROI. If no ROI hint is available,
the metrics default to the lower 40% of the frame. Laplacian variance outputs are normalized by the ROI area so
values remain comparable across different ROI sizes.

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

### Checksum breakdown
For callers that need per-field validation, `MrzValidation` can expose the TD1/TD3 checksum breakdown:

- TD3: `MrzValidation.checksumsTd3(line2)`
- TD1: `MrzValidation.checksumsTd1(line1, line2, line3)`

Each method returns an `MrzChecksums` object with booleans for the document number, birth date,
expiry date, and composite (final) checksum, plus `passedCount` and `totalCount`.
## MRZ failure reasons
When downstream layers need to convey why MRZ parsing or validation failed, use
`com.example.emrtdreader.sdk.models.MrzFailReason`. The enum values are:

- `UNKNOWN_FORMAT`
- `BAD_LENGTH`
- `BAD_CHARSET`
- `CHECKSUM_FAIL`
- `LOW_STRUCTURE_SCORE`
- `LOW_CONFIDENCE`
- `INCONSISTENT_BETWEEN_FRAMES`
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

## MRZ pipeline state enum
For higher-level UX or analytics, the SDK exposes `MrzPipelineState` (`com.example.emrtdreader.sdk.analysis.MrzPipelineState`) with these phases:

- `SEARCHING`
- `TRACKING`
- `OCR_RUNNING`
- `OCR_COOLDOWN`
- `CONFIRMED`
- `TIMEOUT`

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
