-dontobfuscate
-keep class org.apache.hadoop.** { *; }
-dontwarn org.apache.hadoop.**
-keep class org.apache.commons.logging.** { *; }
-dontwarn org.apache.commons.logging.**
-keepclassmembers class org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration {
    <init>(...);
}
# Keep all classes and their members (fields and methods)
-keep class * { *; }

# Keep all annotation attributes
-keepattributes *Annotation*

# Keep all signatures, which are used for generics
-keepattributes Signature

# Keep all
-keepclassmembers class *

-dontoptimize


# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable


# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
