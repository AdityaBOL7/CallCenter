# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line numbers for readable crash stack traces, but hide the original
# source file name (so traces are useful via mapping.txt without leaking paths).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Strip debug/sensitive logging from the release build.
# R8 does NOT remove Log.* by default. This app logs OTP, auth tokens, emails
# and HTTP bodies at d/v/i level — none of that should reach production logcat.
# Log.w / Log.e are KEPT (genuine warnings/errors, no secrets).
# (Requires R8 with -optimizationpasses; works because minify is enabled.)
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ---------------------------------------------------------------------------
# kotlinx.serialization — keep generated serializers + the DTOs they serialize.
# Without this, R8 renames @Serializable classes and parsing breaks at runtime.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.example.callcenter.**$$serializer { *; }
-keepclassmembers class com.example.callcenter.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.callcenter.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep all DTOs (data/remote/dto) — they are (de)serialized by name.
-keep class com.example.callcenter.data.remote.dto.** { *; }

# ---------------------------------------------------------------------------
# Retrofit / OkHttp
# ---------------------------------------------------------------------------
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclasseswithmembers interface com.example.callcenter.data.remote.api.** { @retrofit2.http.* <methods>; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ---------------------------------------------------------------------------
# Hilt / Dagger generated code is handled by their own consumer rules, but keep
# entry points defensively.
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# ---------------------------------------------------------------------------
# Compose generally needs no rules (library ships consumer rules); leave default.
# ---------------------------------------------------------------------------