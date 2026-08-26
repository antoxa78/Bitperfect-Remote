# Add project specific ProGuard rules here.

# Tink (transitive via security-crypto) references errorprone annotations that are
# compile-time only and not present at runtime. Suppress the R8 warning.
-dontwarn com.google.errorprone.annotations.**

# Keep Kotlin metadata for serialisation / reflection used by Compose and Coroutines.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
