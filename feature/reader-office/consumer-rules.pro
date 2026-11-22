# Consumer ProGuard rules for feature\reader-office module
# See top-level proguard-rules.pro for main rules

# Keep LibreOfficeKit classes and native JNI methods
-keep class org.libreoffice.kit.** { *; }
-keepclassmembers class org.libreoffice.kit.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
