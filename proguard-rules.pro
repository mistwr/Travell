# LUMINAI Travel ProGuard Rules

# Keep osmdroid
-keep class org.osmdroid.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep JSON
-keep class org.json.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.** { *; }

# Keep app model classes
-keep class com.luminai.travel.** { *; }
