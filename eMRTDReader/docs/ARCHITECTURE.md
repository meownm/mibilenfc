# Architecture

## Modules
- `sdk` — reusable MRZ + OCR + NFC + crypto library (Java).
- `app` — demo application using the SDK.

## MRZ pipeline (Camera)
1. CameraX frame (ImageAnalysis)
2. MRZ zone detection (heuristic)
3. ROI stabilization (moving average + IoU outlier rejection)
4. Quality gate (sharpness)
5. Preprocess (grayscale + contrast)
6. Adaptive threshold (local) + fallback
7. OCR (ML Kit / Tesseract / Dual)
8. MRZ normalization + checksum-guided repair (TD3/TD1)
9. Burst aggregation -> final MRZ

## NFC pipeline
- Access key from MRZ (BAC) + PACE best-effort
- Read DG1/DG2/SOD
- Passive Authentication: verify CMS signature in SOD + compare DG hashes
- CSCA trust store: assets/csca/*.cer (optional for chain validation)
