# Gson: DTOs are plain POJOs with final fields; keep their names and @SerializedName.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit
-keepattributes Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowobfuscation interface <1>
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# OkHttp
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*

# Our DTO / domain model layer: keep for Gson reflection.
-keep class com.lpms.data.dto.** { *; }
-keep class com.lpms.core.error.** { *; }
-keep class com.lpms.core.auth.** { *; }

# Gson needs generic signatures for TypeToken / RxJava3CallAdapterFactory
-keepattributes Signature
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.reflect.TypeAdapterFactory
-keep class * implements com.google.gson.TypeAdapter

# RxJava3
-dontwarn java.util.concurrent.Flow*
-keepclassmembers class io.reactivex.rxjava3.internal.util.unsafe.** { *; }

# Hilt / Dagger generated components are referenced reflectively by the framework.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room (optional offline cache)
-keep class com.lpms.data.local.** { *; }

# ML Kit barcode
-keep class com.google.mlkit.** { *; }

# Line numbers useful in crash reports, but hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile