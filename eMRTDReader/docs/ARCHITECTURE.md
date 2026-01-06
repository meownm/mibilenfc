# Architecture

## Modules
- `sdk` — reusable MRZ + OCR + NFC + crypto library (Java). Owns CameraX analysis only (camera-core, optional camera-camera2).
- `app` — demo application using the SDK. Owns UI + lifecycle CameraX pieces (PreviewView, lifecycle binding) and pins their versions explicitly alongside camera-core.

## MRZ pipeline (Camera)
1. CameraX frame (ImageAnalysis)
2. MRZ zone detection (heuristic)
3. ROI stabilization (moving average + IoU outlier rejection)
4. Quality gate (sharpness)
5. Preprocess (grayscale + contrast) for ML Kit input
6. Adaptive threshold (local) for Tesseract input (derived from the grayscale/contrast stage)
7. OCR (ML-first router: ML Kit runs on lightweight preprocessing, validated with conservative MRZ checks; if invalid, Tesseract runs on the heavy binarized bitmap)
8. MRZ normalization + checksum-guided repair (TD3/TD1)
9. Burst aggregation -> final MRZ

Listener callbacks from `MrzImageAnalyzer` now include `ScanState` emissions (from `com.example.emrtdreader.sdk.analysis.ScanState`) so UI layers can surface OCR progress, MRZ detection, and failures alongside the usual OCR and final MRZ callbacks.

## Analyzer lifecycle (CameraX)
- Each `analyze` call converts the incoming `ImageProxy` to a mutable `ARGB_8888` bitmap using `androidx.camera.core.internal.YuvToRgbConverter` (no manual plane-buffer access or NV21/JPEG round-trips), then normalizes brightness into a readable range before copying to an immutable bitmap for safe downstream processing.
- The analyzer always works on an immutable copy (`safeBitmap`) so rotation, MRZ detection, ROI cropping, and OCR remain safe even after the `ImageProxy` is closed asynchronously.
- The converter path avoids manual plane buffer access and NV21/JPEG round-trips, preserving per-pixel fidelity while keeping the MRZ band legible in low-light or overexposed frames.
- The `ImageProxy` is closed right after the safe bitmap copy completes, before MRZ detection or OCR begins.
- OCR is dispatched asynchronously via callbacks; the analyzer thread never blocks on OCR completion. Callbacks may arrive on background threads and should be treated as non-UI.
- Any conversion failure or OCR processing exception triggers the analyzer error callback, emits `ScanState.ERROR`, and still closes the `ImageProxy` if it has not been closed yet.
- Frame delivery is logged at the start of each `analyze` call as `FRAME ts=<epoch_ms> w=<width> h=<height>`. Expect ~15–30 fps depending on the configured analyzer interval; continuous log lines indicate steady camera frame delivery, while gaps suggest dropped or stalled frames.
- After bitmap conversion, `FRAME_STATS` logs capture per-frame metrics computed by `FrameStats` (mean brightness, contrast/stddev, Laplacian variance sharpness, and a local-mean residual noise estimate). These metrics are intended for diagnostics and for tuning thresholds that gate MRZ capture quality.

## MRZ scan UI feedback
The MRZ scan activity renders a colored overlay on top of the camera preview to indicate the most recent analyzer outcome. The `ScanState`-to-color mapping is:
- `WAITING`: gray (`@color/overlay_waiting_gray`).
- `ML_TEXT_FOUND`: purple (`@color/overlay_mlkit_purple`).
- `TESS_TEXT_FOUND`: blue (`@color/overlay_tess_blue`).
- `MRZ_FOUND`: green (`@color/overlay_mrz_green`).
- `ERROR`: red (`@color/overlay_error_red`).

## NFC pipeline
- Access key from MRZ (BAC) + PACE best-effort
- Read DG1/DG2/SOD
- Passive Authentication: verify CMS signature in SOD + compare DG hashes
- CSCA trust store: assets/csca/*.cer (optional for chain validation)
