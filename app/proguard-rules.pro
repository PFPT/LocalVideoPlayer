-keep class org.libtorrent4j.swig.libtorrent_jni { *; }
-keep class org.libtorrent4j.** { *; }

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

-keep class com.arthenica.** { *; }
-dontwarn com.arthenica.**

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
