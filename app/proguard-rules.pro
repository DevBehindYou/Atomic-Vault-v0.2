# ==============================================================================
# AtomicVault Release ProGuard & R8 Optimization Rules
# ==============================================================================

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Compose
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn net.sqlcipher.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep @com.squareup.moshi.JsonClass class * { <init>(...); <fields>; }
-keep class *JsonAdapter { public <init>(...); }

# Crypto and Android Keystore
-keep class androidx.biometric.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn org.bouncycastle.**

# Manifest instantiated services
-keep public class * extends android.service.autofill.AutofillService {
    public <init>();
    public *;
}

-keep public class * extends android.inputmethodservice.InputMethodService {
    public <init>();
    public *;
}

# ==============================================================================
# Actual AtomicVault runtime packages
# Source currently uses com.example.* packages while the application id is
# com.atomicvault.android. Keep runtime classes that are reached by Android,
# reflection, SQLCipher, and crypto flows.
# ==============================================================================
-keep class com.example.** { *; }

-keep class com.example.database.** { *; }
-keep class com.example.crypto.** { *; }
-keep class com.example.keystore.** { *; }
-keep class com.example.autofill.** { *; }

# Keep generated/runtime models
-keep class com.atomicvault.android.** { *; }
