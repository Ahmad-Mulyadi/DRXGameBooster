# DRX GAME TURBO PRO - Production R8 / ProGuard Configuration

# Preserve stack trace line numbers for Google Play Console crash reporting
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Keep BuildConfig fields
-keep class com.example.BuildConfig { *; }

# Keep Room databases, entities, and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# Keep Android manifest components & ViewModels
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends androidx.work.ListenableWorker
-keep public class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Kotlin coroutines & state models
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class com.example.data.model.** { *; }
-keepnames class com.example.system.** { *; }

# Keep Web/System reflection-safe Parcelable creators
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
