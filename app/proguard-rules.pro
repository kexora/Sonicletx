# ProGuard and R8 optimization rules for Soniclet Production Release

# 1. R8 Bytecode Optimization & Repackaging
-allowaccessmodification
-repackageclasses 'com.kexora.soniclet.obf'

# 2. Source debugging & line numbers for Play Console stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 3. AndroidX Room Database & Entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.TypeConverter
-keep class **_Impl { *; }
-dontwarn androidx.room.**

# 4. Soniclet Data Models (Preserve fields for DB mapping)
-keepclassmembers class com.kexora.soniclet.data.model.** {
    <fields>;
    <init>(...);
}

# 5. Jaudiotagger (Audio Tag Parsing via Reflection)
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# 6. App Widgets & RemoteViews
-keep class * extends android.appwidget.AppWidgetProvider { *; }

# 7. Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# 8. Media3 / ExoPlayer & Coil warnings
-dontwarn androidx.media3.**
-dontwarn androidx.media.**
-dontwarn coil.**

# 9. General third-party annotation warnings
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
