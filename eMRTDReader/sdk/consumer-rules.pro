# Keep SDK public models and API surface (safe for R8)
-keep class com.example.emrtdreader.models.** { *; }
-keep class com.example.emrtdreader.domain.** { *; }
-keep class com.example.emrtdreader.error.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# JMRTD / SCUBA / BouncyCastle
-dontwarn org.jmrtd.**
-dontwarn net.sf.scuba.**
-dontwarn org.bouncycastle.**

# Tess-two
-dontwarn com.googlecode.tesseract.android.**
-keep class com.googlecode.tesseract.android.** { *; }
