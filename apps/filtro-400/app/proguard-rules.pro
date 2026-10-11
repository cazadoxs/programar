# kotlinx.serialization: conserva los serializadores generados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.cazadoxs.filtro400.** {
    *** Companion;
}
-keepclasseswithmembers class com.cazadoxs.filtro400.** {
    kotlinx.serialization.KSerializer serializer(...);
}
