# Room entities and DAOs are generated code; libraries ship their own consumer rules
# (kotlinx.serialization, MapLibre, Play Billing, AdMob, Glance).
-keepattributes *Annotation*, InnerClasses, Signature
-keep class vlc.transit.core.realtime.Notices$** { *; }
# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
