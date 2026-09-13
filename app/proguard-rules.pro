# ==============================================================================
# AtomicVault Release ProGuard & R8 Optimization Rules
# ==============================================================================

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# Compiler & external dependency warning suppressions
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
-dontwarn org.checkerframework.**
-dontwarn org.bouncycastle.**

# Preserve Enum values and valueOf for reflection
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# SQLCipher Native and Database
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn net.sqlcipher.**

# Moshi Serialization Models & Generated Adapters
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep @com.squareup.moshi.JsonClass class * { <init>(...); <fields>; }
-keep class *JsonAdapter { public <init>(...); }

# Manifest Entry Points
-keep public class * extends android.service.autofill.AutofillService {
    public <init>();
}

-keep public class * extends android.inputmethodservice.InputMethodService {
    public <init>();
}

-keep public class * extends androidx.core.content.FileProvider {
    public <init>();
}
