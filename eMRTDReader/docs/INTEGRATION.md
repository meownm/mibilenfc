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
- Auto (Dual): ML Kit + Tesseract, best result selected by checksum score
- ML Kit only
- Tesseract only

## OCR threading
`MrzImageAnalyzer` and the OCR engines use a callback-based contract. OCR results and errors are delivered asynchronously (often on background threads), so UI layers should marshal updates onto the main thread as needed. The analyzer never blocks on OCR completion, allowing continuous frame delivery.

## Passive authentication
The SDK verifies SOD signatures and data group hashes using Bouncy Castle. Ensure the
`sdk` module keeps the Bouncy Castle provider and certificate converter classes on the
classpath (e.g. `org.bouncycastle.cert.jcajce.JcaX509CertificateConverter`) so passive
authentication can parse CMS certificates and validate signatures.
