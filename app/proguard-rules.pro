# ProGuard rules for OSRS Grand Exchange Career Guild

# Filament 3D Engine rules
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**

# Room and SQLCipher rules
-keep class androidx.room.** { *; }
-keep class net.sqlcipher.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt

# Safe CLI Engine - protect sealed command hierarchy from being stripped
-keep class com.osrs.careerguild.core.cli.** { *; }
