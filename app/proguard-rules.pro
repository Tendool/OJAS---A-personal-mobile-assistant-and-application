# Ojas — release shrinking rules.

# Room generated implementations are reflectively loaded by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Entities are also used by Room's generated cursor readers.
-keepclassmembers class com.ojas.assistant.data.local.entity.** { <fields>; <init>(...); }

# WorkManager instantiates workers by class name.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Broadcast receivers / services referenced only from the manifest.
-keep class com.ojas.assistant.alarm.** { *; }
-keep class com.ojas.assistant.screentime.** { *; }

# Kotlin coroutines / metadata noise.
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

# Keep OpenGL shader-facing model plain.
-keep class com.ojas.assistant.ui.galaxy.** { *; }

# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
