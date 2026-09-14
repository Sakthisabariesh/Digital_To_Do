# ProGuard rules for MindEcho

# Keep Room generated files and models
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Glance app widget classes
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# Keep MindEcho local data entities
-keep class com.mindecho.app.data.local.** { *; }
