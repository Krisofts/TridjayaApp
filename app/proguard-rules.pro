# Add project specific ProGuard rules here.

# kotlinx.serialization: keep the generated $$serializer companions and serializer() accessors
# for our own @Serializable models, otherwise R8 strips the reflection surface serialization needs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.krisoft.tridjayaelektronik.**$$serializer { *; }
-keepclassmembers class com.krisoft.tridjayaelektronik.** {
    *** Companion;
}
-keepclasseswithmembers class com.krisoft.tridjayaelektronik.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit: keep annotations/signatures on API interfaces (method definitions used via reflection).
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions

# OkHttp/Okio ship their own rules but silence platform warnings just in case.
-dontwarn okhttp3.**
-dontwarn okio.**

# Google Tink (used internally by androidx.security-crypto's EncryptedSharedPreferences)
# references errorprone's compile-time-only annotations, which aren't on the runtime classpath.
-dontwarn com.google.errorprone.annotations.**

# Room entities/DAOs are annotation-processed at compile time; nothing extra needed beyond
# the consumer rules Room already bundles, but keep entity fields to be safe against R8 field removal.
-keep class com.krisoft.tridjayaelektronik.data.local.*Entity { *; }
-keep class com.krisoft.tridjayaelektronik.data.local.ProductAggregate { *; }

# Keep our own network request/response models (fields are matched by name during (de)serialization).
-keep class com.krisoft.tridjayaelektronik.data.model.** { *; }
