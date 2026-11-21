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
-keep class androidx.sqlite.** { *; }
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

# --- PDFBox (e-ticket text extraction only) ---
# No blanket -keep: ETicketAnalyzer's PDDocument/PDFTextStripper refs keep the
# reachable graph. FilterFactory/PDFontFactory hold direct Class literals, so
# decode filters and font subtypes stay without freezing multipdf/rendering/etc.
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn org.apache.commons.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**

# Play Console static-scans the AAB for Window.setStatusBarColor / setNavigationBarColor.
# Material BottomSheetDialog still calls EdgeToEdgeUtils setters (no-op on API 35+, but the
# invokevirtual stays in dex and triggers the deprecated edge-to-edge warning). Treat them as
# side-effect-free so R8 drops the call sites; themes already use transparent bar colors.
-assumenosideeffects class com.google.android.material.internal.EdgeToEdgeUtils {
    public static void setStatusBarColor(android.view.Window, int);
    public static void setNavigationBarColor(android.view.Window, int);
}
