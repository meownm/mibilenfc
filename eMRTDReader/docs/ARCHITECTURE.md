# Architecture

## Modules
- `sdk` — reusable MRZ + OCR + NFC + crypto library (Java). Owns CameraX analysis only (camera-core, optional camera-camera2).
- `app` — demo application using the SDK. Owns UI + lifecycle CameraX pieces (PreviewView, lifecycle binding) and pins their versions explicitly alongside camera-core.

## MRZ pipeline (Camera)
1. CameraX frame (ImageAnalysis)
2. MRZ zone detection (heuristic)
3. ROI stabilization (moving average + IoU outlier rejection)
4. Quality gate (sharpness)
5. Preprocess (grayscale + contrast)
6. Adaptive threshold (local) + fallback
7. OCR (ML Kit / Tesseract / Dual, ML Kit + Tesseract run concurrently with timeout fallback)
8. MRZ normalization + checksum-guided repair (TD3/TD1)
9. Burst aggregation -> final MRZ

Listener callbacks from `MrzImageAnalyzer` now include `ScanState` emissions (from `com.example.emrtdreader.sdk.analysis.ScanState`) so UI layers can surface OCR progress, MRZ detection, and failures alongside the usual OCR and final MRZ callbacks.

## Analyzer lifecycle (CameraX)
- Each `analyze` call converts the incoming `ImageProxy` to a mutable `ARGB_8888` bitmap using `androidx.camera.core.internal.YuvToRgbConverter`, then normalizes brightness into a readable range before copying to an immutable bitmap for safe downstream processing.
- The converter path avoids manual plane buffer access and NV21/JPEG round-trips, preserving per-pixel fidelity while keeping the MRZ band legible in low-light or overexposed frames.
- The `ImageProxy` is closed right after the safe bitmap copy completes, before MRZ detection or OCR begins.
- Any conversion failure or OCR processing exception triggers the analyzer error callback, emits `ScanState.ERROR`, and still closes the `ImageProxy` if it has not been closed yet.

## MRZ scan UI feedback
The MRZ scan activity renders a colored overlay on top of the camera preview to indicate the most recent analyzer outcome. Suggested UI interpretations per `ScanState`:
- `WAITING`: show a neutral/idle state such as “Waiting for MRZ” (no MRZ detected yet).
- `ML_TEXT_FOUND`: indicate ML Kit OCR text was detected (e.g., purple highlight or “ML OCR text found”).
- `TESS_TEXT_FOUND`: indicate Tesseract OCR text was detected (e.g., blue highlight or “Tess OCR text found”).
- `MRZ_FOUND`: highlight success (e.g., green) when a valid MRZ is detected or finalized.
- `ERROR`: show error feedback (e.g., red) when the analyzer fails or frames stop arriving.

## NFC pipeline
- Access key from MRZ (BAC) + PACE best-effort
- Read DG1/DG2/SOD
- Passive Authentication: verify CMS signature in SOD + compare DG hashes
- CSCA trust store: assets/csca/*.cer (optional for chain validation)
