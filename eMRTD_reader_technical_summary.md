# Technical Summary - eMRTD Reader Application

## Overview
This document provides a technical summary of the eMRTD reader application based on the Software Requirements Specification (SRS). The application is designed to read electronic travel documents using MRZ scanning and NFC with cryptographic verification.

## Core Functionality
1. **MRZ Reading via Camera**: Locally process passport MRZ through camera OCR
2. **NFC eMRTD Reading**: Access eMRTD chip using MRZ-derived keys
3. **Cryptographic Verification**: Validate document signatures using Passive Authentication
4. **Data Display**: Show photo and personal data on screen

## Architecture Layers
- **UI Layer**: Camera preview, NFC session management, result display
- **Domain Layer**: MRZ detection, parsing, NFC reading, crypto validation, data mapping
- **Infrastructure Layer**: CameraX, OpenCV/OCR, Android NFC, jmrtd, BouncyCastle

## Key Technical Standards
- **ICAO 9303**: Defines MRZ format and eMRTD structure
- **ISO/IEC 14443**: NFC communication protocol
- **ISO/IEC 7816**: APDU command structure
- **jmrtd**: Java library for eMRTD operations
- **BouncyCastle**: Cryptographic operations

## Critical Security Requirements
- Local processing only (no external data transmission)
- Memory-only data storage
- Screenshot prohibition (FLAG_SECURE)
- Cryptographic verification before data trust

## Prohibited Shortcuts
- Cannot use NFC without SOD verification
- Cannot accept DG data without Passive Authentication
- Cannot consider MRZ alone as authenticity confirmation
- Cannot transmit data outside the device

## Success Criteria
1. Stable MRZ scanning across document types
2. Successful NFC reading from multiple passport models
3. Valid SOD signature verification
4. Proper display of photo and personal data
5. Zero data leakage to external systems