# kotlinx.serialization, Room, OkHttp and AndroidX ship their own R8 rules.
# Keep the names of @Serializable classes used for the backup file and AI responses readable in crash reports.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class io.github.kiraherk.smarttodo.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
