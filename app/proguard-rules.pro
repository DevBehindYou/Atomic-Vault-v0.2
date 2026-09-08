# ==============================================================================
# AtomicVault Release ProGuard & R8 Optimization Rules
# ==============================================================================

# --- Standard Android & Kotlin Metadata Preservation ---
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Jetpack Compose & Material 3 ---
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.Stable *;
    @androidx.compose.runtime.Immutable *;
}
-dontwarn androidx.compose.**

# ==============================================================================
# 1. SQLCipher (Encrypted Database, JNI & Native Handles)
# ==============================================================================
# Preserve all SQLCipher Java wrappers and native method bindings.
# JNI code accesses SQLiteDatabase, SQLiteCursor, and statement handles directly.
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Preserve native methods and their enclosing classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Do not warn about missing internal platform dependencies for SQLCipher
-dontwarn net.sqlcipher.**

# ==============================================================================
# 2. Moshi (JSON Serialization, Codegen & Reflection Fallbacks)
# ==============================================================================
# Keep Moshi annotations and core classes
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Keep all models annotated with @JsonClass and preserve their properties & constructors
-keep @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}

# Keep all generated JsonAdapter classes looked up via reflection convention
-keep class *JsonAdapter {
    public <init>(...);
}

# Preserve fields annotated with @Json and custom @JsonQualifier annotations
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonQualifier <fields>;
}
-keep @interface com.squareup.moshi.JsonQualifier { *; }

# Preserve synthetic constructors needed for Kotlin data classes with default arguments
-keepclassmembers class * {
    synthetic <init>(..., kotlin.jvm.internal.DefaultConstructorMarker);
}

# ==============================================================================
# 3. AndroidX Biometric & Cryptographic Authentication
# ==============================================================================
# Keep BiometricPrompt, fragments, and authentication callbacks
-keep class androidx.biometric.** { *; }
-keep interface androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Keep CryptoObject and cryptographic wrapper types used in BiometricPrompt
-keep class androidx.biometric.BiometricPrompt$CryptoObject { *; }
-keep class javax.crypto.Cipher { *; }
-keep class java.security.Signature { *; }
-keep class javax.crypto.Mac { *; }

# Keep Android KeyStore and cryptographic providers
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class androidx.security.crypto.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keepclasseswithmembers class * {
    public static javax.crypto.Cipher getInstance(...);
    public static java.security.KeyStore getInstance(...);
    public static javax.crypto.KeyGenerator getInstance(...);
    public static javax.crypto.SecretKeyFactory getInstance(...);
}

# ==============================================================================
# 4. System Services (Autofill & Secure Keyboard)
# ==============================================================================
# Android AutofillService
-keep public class * extends android.service.autofill.AutofillService {
    public <init>();
    public *;
}

# Android InputMethodService (Virtual Secure Keyboard)
-keep public class * extends android.inputmethodservice.InputMethodService {
    public <init>();
    public *;
}

# ==============================================================================
# 5. AtomicVault Core Models, Crypto & Trust Ledger
# ==============================================================================
# Preserve domain entities, cryptographic algorithms, and backup formats (.atvb)
-keep class com.atomicvault.android.model.** { *; }
-keep class com.atomicvault.android.data.** { *; }
-keep class com.atomicvault.android.backup.** { *; }
-keep class com.atomicvault.android.crypto.** { *; }
-keep class com.atomicvault.android.trust.** { *; }
