# Architecture

## Modules
- `sdk` — reusable MRZ + OCR + NFC + crypto library (Java). Owns CameraX analysis only (camera-core, optional camera-camera2).
- `app` — demo application using the SDK. Owns UI + lifecycle CameraX pieces (PreviewView, lifecycle binding) and pins their versions explicitly alongside camera-core.

## MRZ pipeline (Camera)
1. CameraX frame (ImageAnalysis)
2. MRZ zone detection (heuristic). If detection fails, fall back to a bottom-of-frame ROI (~35–40% height with small side margins) so OCR can still run.
3. ROI stabilization (moving average + IoU outlier rejection)
4. Quality gate (sharpness)
5. ML Kit OCR on raw/minimal input (no binarization)
6. Tesseract preprocessing: calibrate by iterating stored/default preprocessing candidates (scale + adaptive threshold)
7. OCR routing rules:
   - Run ML Kit first.
   - If ML Kit returns non-empty text, accept it as the source.
   - If ML Kit returns empty text, run the calibrated Tesseract candidate loop and take the best candidate.
   - If Tesseract yields a valid MRZ (confidence ≥ 3), boost MRZ confidence by one step (capped at 4).
   - When dual OCR candidates tie on confidence, prefer TD3 (passport) over TD1.
8. MRZ normalization + checksum-guided repair (TD3/TD1)
9. Burst aggregation -> final MRZ

Listener callbacks from `MrzImageAnalyzer` now include `ScanState` emissions (from `com.example.emrtdreader.sdk.analysis.ScanState`) so UI layers can surface OCR progress, MRZ detection, and failures alongside the usual OCR and final MRZ callbacks.

## Analyzer lifecycle (CameraX)
- Each `analyze` call converts the incoming `ImageProxy` to a mutable `ARGB_8888` bitmap through the SDK-owned `YuvBitmapConverter` wrapper, then normalizes brightness into a readable range before copying to an immutable bitmap for safe downstream processing.
- `YuvBitmapConverter` defines a small `Converter` interface (`yuvToRgb(Image, Bitmap)`) so the SDK depends only on `android.media.Image`, `android.graphics.Bitmap`, and CameraX `ImageProxy` at its boundary. The default adapter lives in the SDK and can be swapped in tests or by callers without exposing CameraX-internal classes to the rest of the pipeline.
- The analyzer always works on an immutable copy (`safeBitmap`) so rotation, MRZ detection, ROI cropping, and OCR remain safe even after the `ImageProxy` is closed asynchronously.
- The conversion path avoids manual plane-buffer access and NV21/JPEG round-trips, preserving per-pixel fidelity while keeping the MRZ band legible in low-light or overexposed frames.
- OCR routing and preprocessing parameter selection are keyed using the rotated frame dimensions (post-rotation width/height) alongside the camera ID, so portrait vs. landscape routing stays consistent after rotation is applied.
- Tradeoff: per-frame conversion plus brightness normalization adds some CPU work and may slightly compress highlight/shadow detail when scaling luma, but it avoids JPEG artifacts and keeps OCR quality stable across exposure changes.
- The `ImageProxy` is closed right after the safe bitmap copy completes, before MRZ detection or OCR begins.
- OCR is dispatched asynchronously via callbacks; the analyzer thread never blocks on OCR completion. Callbacks may arrive on background threads and should be treated as non-UI.
- Any conversion failure or OCR processing exception triggers the analyzer error callback, emits `ScanState.ERROR`, and still closes the `ImageProxy` if it has not been closed yet.
- Frame delivery is logged at the start of each `analyze` call as `FRAME ts=<epoch_ms> w=<width> h=<height>`. Expect ~15–30 fps depending on the configured analyzer interval; continuous log lines indicate steady camera frame delivery, while gaps suggest dropped or stalled frames.
- After bitmap conversion, `FRAME_STATS` logs capture per-frame metrics computed by `FrameStats` (mean brightness, contrast/stddev, Laplacian variance sharpness, and a local-mean residual noise estimate). These metrics are intended for diagnostics and for tuning thresholds that gate MRZ capture quality.
- When MRZ auto-detection fails, the analyzer emits a `WAITING` scan state with a fallback ROI message, updates the ROI stabilizer with the fallback rectangle, and continues OCR. This keeps scan-state transitions (ML/Tesseract text found, MRZ found) flowing even without a detected MRZ band.

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
