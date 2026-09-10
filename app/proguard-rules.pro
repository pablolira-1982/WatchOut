# Add project specific ProGuard rules here.
-keep class com.watchout.** { *; }
-keepclassmembers class com.watchout.** { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Kotlin
-keep class kotlin.** { *; }
-keepclassmembers class **$WhenMappings { *; }
