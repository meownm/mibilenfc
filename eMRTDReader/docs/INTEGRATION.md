# Integration

## Add dependency
Use the `sdk` module as an Android library.

## Permissions
- Camera: `android.permission.CAMERA`
- NFC: `android.permission.NFC`
- Internet: optional (for Tesseract traineddata auto-download)

## OCR modes
- Auto (Dual): ML Kit + Tesseract, best result selected by checksum score
- ML Kit only
- Tesseract only
