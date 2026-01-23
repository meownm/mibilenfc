# NFC File Map

This document summarises the main classes involved in reading eMRTD (electronic passport) data over NFC within the **eMRTDReader** project.

## Core reading classes

- **`sdk/data/NfcPassportReader.java`** – top‑level API for reading data groups (DG1, DG2, SOD) from the passport chip using the provided MRZ access key (or CAN).  Handles retries, timeouts and wraps lower‑level helpers.
- **`sdk/data/JmrtdAccessController.java`** – uses the JMRtd library to perform BAC or PACE authentication against the chip based on the MRZ, then grants access to EF files.
- **`sdk/domain/PassportReadResult.java`** – container for the result of a passport read, including the parsed `PassportChipData` and the `VerificationResult` from passive authentication.
- **`sdk/models/PassportChipData.java`** – immutable model containing passport holder data extracted from DG1 and DG2 (e.g. document number, name, nationality, photo).

## Cryptography & authentication

- **`sdk/crypto/PassiveAuthVerifier.java`** – verifies the integrity of DG1/DG2 data against the signed object digest (SOD) using country signing certificate authority (CSCA) certificates.
- **`sdk/crypto/CscaStore.java`** – loads and caches CSCA certificates used for passive authentication.

## Exceptions & error handling

- **`sdk/error/PassportReadException.java`** – hierarchy of checked exceptions representing different failure modes (e.g. tag not ISO‑DEP, BAC authentication failure, I/O errors).

## Domain & keys

- **`sdk/domain/AccessKey.java`** – models MRZ‑derived keys (document number, date of birth, expiry) used for BAC/PACE.
- **`sdk/domain/MrzKey.java`** – supports PACE by generating chip access keys from the MRZ.

## NFC UI

- **`app/src/main/java/com/example/emrtdreader/NFCReadActivity.java`** – Android activity that performs the NFC read once MRZ data has been captured, displays progress and final results.

When working on NFC functionality, start at `NfcPassportReader` to understand the high‑level read flow.  Authentication helpers delegate to the [JMRtd](https://github.com/eclipse/jmrtd) library, while passive authentication uses custom code in `PassiveAuthVerifier`.