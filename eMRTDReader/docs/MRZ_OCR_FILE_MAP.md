# MRZ OCR File Map

This document provides a high‑level overview of the key Java classes involved in the MRZ scanning and OCR pipeline in the **eMRTDReader** project.  It is intended as a navigational aid when reviewing or modifying the code.

## Image acquisition & analysis

- **`sdk/analyzer/MrzImageAnalyzer.java`** – entry point for CameraX; receives `ImageProxy` frames from the camera, applies throttling via a rate limiter and hands frames into the MRZ pipeline.
- **`sdk/analyzer/RateLimiter.java`** – simple utility to drop frames when analysis is still in progress.
- **`sdk/analyzer/FrameEnvelope.java`** – wraps raw frames together with metadata (timestamp, rotation, etc.) for the pipeline.
- **`sdk/analyzer/YuvBitmapConverter.java`** and **`sdk/analyzer/ImageProxyUtils.java`** – convert YUV `ImageProxy` frames to bitmaps for downstream processing.

## Pipeline & state

- **`sdk/analysis/MrzPipelineFacade.java`** – orchestrates the full MRZ detection flow: gating, localisation, OCR, parsing and state updates.  It exposes a simple API to the analyzer.
- **`sdk/analysis/MrzFrameGate.java`** – quick quality gate (blur, brightness and contrast checks) to drop unusable frames before heavy processing.
- **`sdk/analysis/MrzLocalizer.java`** – locates the MRZ region in the frame (heuristic search).
- **`sdk/analysis/MrzPipelineExecutor.java`** – background executor that runs MRZ processing jobs one at a time.
- **`sdk/analysis/MrzPipelineParser.java`**, **`sdk/analysis/DefaultMrzPipelineParser.java`** – parse OCR output into a structured `MrzResult`.
- **`sdk/analysis/MrzStateMachine.java`** – finite‑state machine tracking overall scan state.
- **`sdk/analysis/ScanState.java`** – enumeration for high‑level scanner state (waiting, scanning, done, error).

## OCR & preprocessing

- **`sdk/ocr/MrzPreprocessor.java`** – preprocesses the MRZ bitmap (deskewing, cropping, greyscale, adaptive threshold).
- **`sdk/ocr/DualOcrRunner.java`** – coordinates Tesseract and ML Kit OCR engines; in *AUTO_DUAL* mode runs both engines on good frames.
- **`sdk/ocr/MrzOcrEngine.java`**, **`sdk/ocr/MrzTextProcessor.java`** – wrap the OCR engines and post‑process their output.
- **`sdk/ocr/MlKitOcrEngine.java`**, **`sdk/ocr/TesseractOcrEngine.java`** – concrete OCR implementations.
- **`sdk/ocr/MrzCandidateValidator.java`**, **`sdk/ocr/MrzAutoDetector.java`** – helpers to validate candidate MRZ blocks and select the best one.

## Models & parsing

- **`sdk/models/MrzResult.java`** – immutable representation of a parsed MRZ, including confidence and parsed fields.
- **`sdk/models/OcrResult.java`** – raw OCR output plus metrics (timings, brightness, contrast, sharpness).
- **`sdk/models/MrzFormat.java`** and **`sdk/models/MrzFields.java`** – enumerate known MRZ formats and parsed field indices.
- **`sdk/utils/MrzParser.java`** – converts a parsed MRZ into an `AccessKey.Mrz` object for NFC.

## UI & Activity

- **`app/src/main/java/com/example/emrtdreader/MRZScanActivity.java`** – Android activity driving the camera preview, MRZ analysis and navigation to NFC reading.
- **`sdk/ui/MrzDebugOverlayView.java`** – overlay view used to draw MRZ bounding boxes and debug metrics on top of the preview.

## Utilities

- **`sdk/ocr/RectAverager.java`** – smooths bounding box positions across frames.
- **`sdk/ocr/FrameStats.java`** – computes brightness, contrast and sharpness metrics from a bitmap.
- **`sdk/utils/MrzChecksum.java`**, **`sdk/utils/MrzValidation.java`**, **`sdk/utils/MrzRepair.java`** – helper classes for MRZ checksum validation and repair.

Use this file as a starting point when exploring the MRZ scanning pipeline.  Many classes have unit tests under `sdk/src/test/java` that illustrate expected behaviour.