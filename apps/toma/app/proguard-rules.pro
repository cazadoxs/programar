# kotlinx.serialization: keep generated serializers for the core models stored in Room and backups.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.cazadoxs.toma.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.cazadoxs.toma.core.**$$serializer { *; }
-dontnote kotlinx.serialization.**
