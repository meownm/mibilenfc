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
- ML Kit only
- Tesseract only

## Tesseract preprocessing candidates
When Auto mode falls back to Tesseract, the SDK runs a fixed candidate set (`PreprocessParamSet`) and selects the result with the best MRZ validity score. Each candidate defines the adaptive threshold block size, C offset, scale factor, and blur radius:

- (block size 15, C 5, scale 2.0, blur 0)
- (block size 17, C 7, scale 2.25, blur 1)
- (block size 21, C 9, scale 2.5, blur 1)
- (block size 13, C 3, scale 1.75, blur 0)

For each candidate, the same ROI frame is preprocessed, Tesseract runs, and the normalized text is scored using the MRZ scoring rules below. The highest score is selected as the final Tesseract result (and is compared against the ML Kit score in Auto mode).

## MRZ scoring rules
The MRZ scorer parses OCR text into TD3 lines and assigns a normalized score (0..1):

- Parse OCR text into exactly two MRZ lines (TD3). If one 88-character line is present, it is split into two 44-character lines.
- Enforce strict formatting: line count = 2, line length = 44, allowed charset `[A-Z0-9<]`.
- Compute the four TD3 checksum validations and return the ratio of successful checks (`validChecks / 4`).

If strict formatting fails, the score is `0.0`.

## OCR threading
`MrzImageAnalyzer` and the OCR engines use a callback-based contract. OCR results and errors are delivered asynchronously (often on background threads), so UI layers should marshal updates onto the main thread as needed. The analyzer never blocks on OCR completion, allowing continuous frame delivery.

## Passive authentication
The SDK verifies SOD signatures and data group hashes using Bouncy Castle. Ensure the
`sdk` module keeps the Bouncy Castle provider and certificate converter classes on the
classpath (e.g. `org.bouncycastle.cert.jcajce.JcaX509CertificateConverter`) so passive
authentication can parse CMS certificates and validate signatures.
