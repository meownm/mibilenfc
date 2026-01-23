# Repository Walk‑Through and Review

This document provides a guided walk‑through of the **eMRTDReader** repository and highlights key design points and opportunities for improvement identified during the initial analysis.

## Top‑level structure

The repository contains a single Gradle project, `eMRTDReader`, which builds both an Android application (`app/`) and a reusable SDK (`sdk/`).  Supporting scripts and documentation live alongside the source code.

```
eMRTDReader/
├── app/          – Android app module (Activities, layouts, resources)
├── sdk/          – Reusable library for MRZ scanning, OCR and NFC reading
├── docs/         – Project documentation (architecture, integration and file maps)
├── scripts/      – Gradle helper scripts for development and release
├── build.gradle  – Gradle configuration
└── README.md     – Usage instructions and high‑level overview
```

### `app/`

The app module drives the camera preview, presents the scanning overlay, handles manual data entry and kicks off the NFC read once MRZ data is available.  Key classes include:

- `MRZScanActivity.java` – orchestrates camera permissions, starts the `MrzImageAnalyzer` and updates UI state based on scan results.
- `NFCReadActivity.java` – uses `NfcPassportReader` to read the passport chip and displays the extracted data.

### `sdk/`

The SDK module encapsulates all MRZ detection, OCR, parsing and NFC reading logic.  It is divided into several packages:

- **`analysis`** – implements the MRZ processing pipeline (frame gating, localisation, OCR routing and parsing).
- **`analyzer`** – adapts CameraX `ImageProxy` frames into the pipeline.
- **`ocr`** – defines OCR engines (Tesseract, ML Kit) and preprocessing utilities.
- **`data`**, **`crypto`**, **`domain`**, **`models`**, **`error`** – support NFC reading, passive authentication and domain models.
- **`utils`** – low‑level helpers for checksums, normalisation and parsing.

Unit tests under `sdk/src/test/java` illustrate expected behaviour for many components, while instrumentation tests under `app/src/androidTest/java` exercise the end‑to‑end flow on device.

## Observations & recommendations

During the analysis a number of potential enhancements were identified to improve performance and robustness of the MRZ scanner:

1. **Early gating before heavy processing** – drop frames if the pipeline or OCR is busy before converting `ImageProxy` to `Bitmap`.  This avoids unnecessary CPU work.
2. **Avoid full‑frame rotation** – detect the MRZ region on the rotated frame but only crop and rotate the Region Of Interest (ROI) instead of the entire image, saving memory and time.
3. **Use CameraX RGBA output** – where possible request `OutputImageFormat.RGBA_8888` to avoid the intermediate YUV→JPEG→Bitmap conversion in `YuvBitmapConverter`.
4. **Compute metrics on downscaled or grayscale data** – move `FrameStats` calculations to the Y‑plane or a smaller bitmap to reduce allocations.
5. **Consolidate executors** – there are currently two `MrzPipelineExecutor` implementations with slightly different behaviour; unifying them could simplify the gating logic.
6. **Stabilise OCR policy** – run ML Kit OCR less frequently (e.g. every N frames) if Tesseract alone already produces sufficient MRZ results.
7. **Improve ROI stabilisation** – smoothing bounding boxes over several frames reduces jitter and false re‑triggers of OCR.

These suggestions are provided as guidance for future refactoring.  They are not implemented automatically by this patch, but documenting them here allows tracking and prioritisation of performance work.

For further details on individual files, refer to the file maps in `MRZ_OCR_FILE_MAP.md` and `NFC_FILE_MAP.md`.