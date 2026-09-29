# Keep Shizuku API classes
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Keep our classes
-keep class com.gj.gt30light.** { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep ViewModel and LiveData
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep Material Components
-keep class com.google.android.material.** { *; }

# Suppress warnings
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**
-dontwarn com.gj.gt30light.**

# Optimization
-optimizationpasses 5
-allowaccessmodification