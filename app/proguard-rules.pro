# kotlinx.serialization 在开启混淆后必须保留这些，否则解析会在运行时抛
# SerializationException: Serializer for class '...' is not found
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.example.tiebasearch.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.tiebasearch.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
