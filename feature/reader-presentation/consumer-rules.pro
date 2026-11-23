# Keep JNI entrypoints for LibreOfficeKit
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.nexus.feature.reader.presentation.engine.** { *; }

# Apache POI and Aalto StAX for presentation module
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.apache.commons.** { *; }
-keep class org.apache.logging.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class com.fasterxml.aalto.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn java.awt.**
-dontwarn javax.xml.stream.**
-dontwarn com.microsoft.schemas.**
