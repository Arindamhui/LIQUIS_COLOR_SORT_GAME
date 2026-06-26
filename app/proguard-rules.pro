# ProGuard rules for Liquid Color Sort release builds

# ── Kotlin / coroutines ───────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── Hilt / Dagger ─────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ── DataStore ─────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── AdMob / Google Mobile Ads ─────────────────────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ── Gson (used for levels.json parsing) ───────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.liquidcolorsort.core.level.** { *; }

# ── Navigation ────────────────────────────────────────────────────────────────
-keep class * extends androidx.navigation.NavArgs { *; }

# ── General Android ───────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod
