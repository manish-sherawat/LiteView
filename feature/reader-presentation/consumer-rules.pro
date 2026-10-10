# Keep JNI entrypoints for LibreOfficeKit
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.nexus.feature.reader.presentation.engine.** { *; }
