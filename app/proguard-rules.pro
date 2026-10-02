# ProGuard rules for SafeGuardian Release Build
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep domain models and business logic
-keep class com.safeguardian.app.domain.** { *; }
-keep class com.safeguardian.app.domain.model.** { *; }
-keep class com.safeguardian.app.domain.ai.ml.** { *; }
-keep class com.safeguardian.app.data.** { *; }

# TensorFlow Lite runtime and native JNI bindings
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-keep class com.google.android.gms.tflite.** { *; }

# Preserve Enums and native method bindings
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# Android Architecture Components
-keep class androidx.lifecycle.** { *; }
