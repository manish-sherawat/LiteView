# ─── NexusDocsViewer ProGuard Rules ──────────────────────────────────────────
# This file is applied to the release build.

# ─── Android/Compose ─────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.content.ContentProvider

# ─── Kotlin ──────────────────────────────────────────────────────────────────
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.Metadata { *; }
-keepclassmembers class **.R$* { public static <fields>; }

# ─── Hilt DI ─────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep class **_HiltModules* { *; }
-keep class **_MembersInjector* { *; }

# ─── Room Database ────────────────────────────────────────────────────────────
# Room uses reflection to access entity fields and DAOs at runtime.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-dontwarn androidx.room.**

# ─── Coroutines ──────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ─── Navigation Compose ──────────────────────────────────────────────────────
-keepnames class androidx.navigation.** { *; }
-keep enum com.nexus.core.navigation.DocumentType { *; }
-keep class com.nexus.core.navigation.NexusRoute** { *; }

# ─── Apache POI (Phase 4 — will be activated) ────────────────────────────────
# Apache POI relies heavily on reflection and dynamic class loading for document schemas and parts.
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.apache.commons.** { *; }
-keep class org.apache.logging.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class * extends org.apache.xmlbeans.XmlObject { *; }
-keep class com.fasterxml.aalto.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn java.beans.**
-dontwarn com.graphbuilder.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
-dontwarn org.tukaani.xz.**
-dontwarn edu.umd.cs.findbugs.annotations.**

# ─── Pdfium (Phase 3 — JNI bridge) ───────────────────────────────────────────
-keep class com.shockwave.pdfium.** { *; }
-keep class com.artifex.mupdf.fitz.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ─── DataStore ────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ─── Remove logging in release ────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
}
-dontwarn com.microsoft.schemas.**
-dontwarn javax.xml.stream.**
-dontwarn org.etsi.uri.**
-dontwarn org.osgi.framework.**
-dontwarn org.w3.x2000.**
-dontwarn aQute.bnd.annotation.spi.**