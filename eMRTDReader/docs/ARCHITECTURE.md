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

Listener callbacks from `MrzImageAnalyzer` now include an error signal that surfaces critical analyzer failures to UI layers (e.g., MRZ scan activity toast/status updates) alongside the usual OCR and final MRZ callbacks.

## MRZ scan UI feedback
The MRZ scan activity renders a colored overlay on top of the camera preview to indicate the most recent analyzer outcome:
- Green when an MRZ is detected or finalized.
- Purple for ML Kit OCR updates.
- Blue for Tesseract OCR updates.
- Red when frames stop arriving or the analyzer reports an error.

## NFC pipeline
- Access key from MRZ (BAC) + PACE best-effort
- Read DG1/DG2/SOD
- Passive Authentication: verify CMS signature in SOD + compare DG hashes
- CSCA trust store: assets/csca/*.cer (optional for chain validation)
