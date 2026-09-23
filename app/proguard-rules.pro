# LipaBill R8 / ProGuard — release hardening
# Goal: obfuscate app logic; keep only what the runtime / libraries require.

-allowaccessmodification
-repackageclasses 'o'
-overloadaggressively

# --- Strip logging (no menu labels / session traces in release) ---
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# --- Kotlin ---
-dontwarn kotlin.**
-dontwarn kotlinx.**

# --- SQLCipher / SQLite ---
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keep class androidx.sqlite.** { *; }
-dontwarn net.sqlcipher.**
-dontwarn net.zetetic.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-dontwarn androidx.room.paging.**

# Keep enum names used in Room TypeConverters
-keepclassmembers enum com.lipabill.app.data.model.TransactionType { *; }
-keepclassmembers enum com.lipabill.app.ussd.RepeatOutcome { *; }

# --- Security crypto / Tink (EncryptedSharedPreferences) ---
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**

# --- Biometric ---
-keep class androidx.biometric.** { *; }

# --- Accessibility service (manifest-referenced; keep entry explicitly) ---
-keep class com.lipabill.app.ussd.UssdAccessibilityService { *; }

# --- SMS receiver ---
-keep class com.lipabill.app.data.sms.MpesaSmsReceiver { *; }

# --- Application / Activity (manifest) ---
-keep class com.lipabill.app.LipaBillApp { *; }
-keep class com.lipabill.app.MainActivity { *; }

# --- Compose / Navigation (library consumer rules usually enough) ---
-dontwarn androidx.compose.**

# --- Firebase Crashlytics (readable stacks after R8) ---
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-dontwarn com.google.firebase.crashlytics.**

# --- PDFBox (e-ticket text extraction) ---
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.commons.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**
