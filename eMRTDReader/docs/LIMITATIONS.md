# Limitations

- Very strong motion blur or extremely low light will reduce MRZ accuracy.
- Some ID cards may require better MRZ zone detection; current detector is heuristic.
- CSCA chain validation requires relevant CSCA certificates in assets.
- MRZ scan UI shows live OCR status: when OCR is running but MRZ is not found yet, it displays an explicit "MRZ not detected yet" status plus a short OCR preview; if no frames reach OCR, the UI reports that the camera is not delivering frames.
