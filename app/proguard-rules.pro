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
-keep class net.zetetic.database.** { *; }
# argon2kt: JNI binds these classes by name from native code.
-keep class com.lambdapioneer.argon2kt.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn net.zetetic.database.**

# Moshi Serialization Models & Generated Adapters
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep @com.squareup.moshi.JsonClass class * { <init>(...); <fields>; }
-keep class *JsonAdapter { public <init>(...); }

# Manifest Entry Points
-keep public class * extends android.service.autofill.AutofillService {
    public <init>();
}

-keep public class * extends androidx.core.content.FileProvider {
    public <init>();
}
