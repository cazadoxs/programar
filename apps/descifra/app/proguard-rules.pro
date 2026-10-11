# kotlinx.serialization: keep generated serializers for our models.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class com.cazadoxs.descifra.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.cazadoxs.descifra.**$$serializer { *; }

# Anthropic Java SDK uses Jackson reflection on its model classes.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * { @com.fasterxml.jackson.annotation.* *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn com.anthropic.**
-dontwarn java.beans.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn kotlin.reflect.jvm.internal.**

# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
