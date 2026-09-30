# ProGuard and R8 rules for PDF Merger release builds

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Apache PDFBox Android & FontBox
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.fontbox.**

# Bouncy Castle Cryptography Provider
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# App Models
-keep class com.example.model.** { *; }

