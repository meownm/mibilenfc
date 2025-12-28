# Software Requirements Specification (SRS) - eMRTD Reader Application

## Document Information
- **Document Title**: Software Requirements Specification for eMRTD Reader Application
- **Version**: 1.0
- **Date**: December 28, 2025
- **Project**: Android eMRTD Reader with MRZ and NFC

---

## 1. Purpose and Scope

### 1.1 Purpose
Mobile Android application for local reading and verification of electronic travel documents (eMRTD) without transmitting data to external services.

### 1.2 Scope of Application
- KYC / identification processes
- Internal corporate applications
- Demonstration and offline scenarios

### 1.3 Limitations
- Android platform only
- Local processing only
- No cloud or remote API dependencies

---

## 2. Normative and Technological Base

### 2.1 Standards
- **ICAO 9303** - MRZ format, chip structure, cryptography
- **jmrtd** - eMRTD handling
- **Android NFC**
- ISO/IEC 14443 (RFID)
- ISO/IEC 7816 (APDU)

---

## 3. Target Platform

### 3.1 Minimum Requirements
- Android 9 (API 28)+
- NFC (IsoDep)
- Camera with autofocus
- ARM64 architecture

### 3.2 Recommended Requirements
- Android 11+
- Camera ≥ 12 MP
- NFC with stable IsoDep support

---

## 4. Application Architecture

```
UI Layer
 ├─ Camera (MRZ)
 ├─ NFC Session
 └─ Result Screen

Domain Layer
 ├─ MRZ Detector
 ├─ MRZ Parser & Checksum
 ├─ NFC Reader
 ├─ Crypto Validator
 └─ Data Mapper

Infrastructure Layer
 ├─ CameraX
 ├─ OpenCV / OCR
 ├─ Android NFC
 ├─ jmrtd
 └─ BouncyCastle
```

---

## 5. Functional Requirements

### 5.1 MRZ Reading (Camera)

**FR-MRZ-1**: The application must open camera live-preview and automatically detect MRZ area.

**FR-MRZ-2**: MRZ recognition must be performed **locally**.

**FR-MRZ-3**: Supported formats:
- TD3 (passport, 2 rows × 44 characters)
- TD1 (ID card)

**FR-MRZ-4**: After OCR, the following must be performed:
- Normalization (`<`, A-Z, 0-9)
- Checksum validation (number, date of birth, expiration date)
- Document type identification

**FR-MRZ-5**: The user must see:
- MRZ frame
- Status "Scanned / Error"

### 5.2 Access Key Preparation

**FR-KEY-1**: Keys must be generated based on MRZ:
- Kseed
- Kenc
- Kmac

**FR-KEY-2**: Support:
- BAC (fallback)
- PACE (primary)

### 5.3 NFC Session and Chip Reading

**FR-NFC-1**: The application must automatically start NFC session after successful MRZ reading.

**FR-NFC-2**: Protocol support:
- IsoDep

**FR-NFC-3**: Mandatory reading:
- DG1 — personal data
- DG2 — face photo
- SOD — signatures and hashes

**FR-NFC-4**: Reading must be performed **locally**, without data transmission.

**FR-NFC-5**: The application must display reading progress of DG.

### 5.4 Cryptographic Verification

**FR-CRYPTO-1**: Mandatory implementation of **Passive Authentication**:
- SOD signature verification
- DG hash verification

**FR-CRYPTO-2**: Used certificates:
- CSCA (embedded or offline-updatable)
- DS (from SOD)

**FR-CRYPTO-3**: Verification result:
- VALID
- INVALID
- NOT VERIFIED (no trust root)

**FR-CRYPTO-4**: Without successful verification, data is **not considered trusted**.

### 5.5 Data Display

**FR-UI-1**: The screen must display:
- Full name
- Date of birth
- Citizenship
- Document number
- Expiration date
- Document type

**FR-UI-2**: Photo from DG2 must be displayed next to data.

**FR-UI-3**: Status must be displayed:
- MRZ OK / ERROR
- NFC OK / ERROR
- SIGNATURE VALID / INVALID

**FR-UI-4**: MRZ and DG1 data must be visually comparable (for control).

---

## 6. Non-functional Requirements

### 6.1 Security
- All data stored only in memory
- No logging of personal data
- Data cleanup on exit
- Screenshot prohibition (FLAG_SECURE)

### 6.2 Performance
| Stage | Requirement |
|-------|-------------|
| MRZ | ≤ 2 seconds |
| NFC DG1 + DG2 | ≤ 10 seconds |
| Display | ≤ 500 ms |

### 6.3 Reliability
- NFC connection loss handling
- DG read retry
- NFC session timeouts

### 6.4 UX
- Step-by-step flow
- Clear prompts ("Place passport near phone")
- Fallback on errors

---

## 7. Error Handling

| Code | Description |
|------|-------------|
| MRZ_01 | MRZ not found |
| MRZ_02 | Checksum error |
| NFC_01 | Chip not detected |
| NFC_02 | PACE/BAC error |
| CRYPTO_01 | Invalid signature |

---

## 8. Prohibited Simplifications (Critical)

❌ Using NFC without SOD verification  
❌ Reading DG without Passive Authentication  
❌ Accepting MRZ as authenticity confirmation  
❌ Transmitting data outside device  

---

## 9. Minimal Technology Stack

- Kotlin
- CameraX
- OpenCV / Tesseract (MRZ)
- Android NFC (IsoDep)
- **jmrtd**
- BouncyCastle

---

## 10. Acceptance Criteria

The application is considered ready if:
1. MRZ is read stably
2. NFC chip is read from at least 2 different passports
3. SOD signature is validated
4. Photo and data are displayed
5. No data leaks