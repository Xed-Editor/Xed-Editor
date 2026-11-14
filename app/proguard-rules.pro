# ===========================================================================
# R8 / ProGuard rules for Xed.
#
# Only the playstore release is minified (see app/build.gradle.kts) and Google
# Play scores that build on how much of it R8 is allowed to shrink and obfuscate.
# Every blanket "-keep class some.library.** { *; }" lowers that score, so only
# add a rule here for code that is genuinely resolved *by name* at runtime:
# reflection, JNI, or a serialization format keyed by field name. Prefer naming
# the concrete class/member over a whole package.
# ===========================================================================

# --- Optional references to desktop/JDK-only classes ------------------------
# Desktop oriented libraries (JGit, Joni, ...) reference classes that do not
# exist on Android. They are never loaded on device, so suppress the noise.
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn java.lang.management.**
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.management.**
-dontwarn javax.script.**
-dontwarn javax.security.auth.login.**
-dontwarn javax.servlet.**
-dontwarn javax.swing.**
-dontwarn org.ietf.jgss.**
-dontwarn org.osgi.annotation.bundle.**
-dontwarn org.slf4j.impl.**
-dontwarn sun.misc.**
-dontwarn sun.security.x509.X509Key
-dontwarn com.google.auto.value.**
-dontwarn io.opentelemetry.api.incubator.**
-dontwarn kotlin.Cloneable$DefaultImpls

# --- Attributes needed by reflection based (de)serialization ----------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Metadata

# --- Gson -------------------------------------------------------------------
# Gson maps JSON to fields by name, so field names of serialized models have to
# survive obfuscation. Everything else in Gson may be shrunk and renamed.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Models persisted as JSON - their field names are part of the stored format.
-keepclassmembers class com.rk.settings.editor.FontRegistry$Font { <fields>; }
-keepclassmembers class com.rk.runner.** { <fields>; }

# --- kotlin-reflect ---------------------------------------------------------
# Preference.preferenceTypes() enumerates Settings' delegated properties with
# kotlin-reflect, so the reflection runtime and the Kotlin metadata must survive.
# The rest of the Kotlin stdlib is free to be shrunk and obfuscated.
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# --- LSP4J ------------------------------------------------------------------
# JSON-RPC messages are (de)serialized reflectively and the property names are
# the LSP wire format, so they must not be renamed.
-keep class org.eclipse.lsp4j.** { *; }
-keepclassmembers enum org.eclipse.lsp4j.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- JGit -------------------------------------------------------------------
# JGit selects its SystemReader/FS implementations and reads git config through
# reflection. The rest of JGit is called directly and can be obfuscated.
-keep class org.eclipse.jgit.util.SystemReader { *; }
-keep class org.eclipse.jgit.storage.file.FileBasedConfig { *; }

# --- Compose workaround -----------------------------------------------------
# Fix for AbstractMethodError: abstract method
# "double androidx.compose.ui.graphics.colorspace.DoubleFunction.invoke(double)"
-keep,allowoptimization interface androidx.compose.ui.graphics.colorspace.DoubleFunction { *; }
-keep,allowoptimization class androidx.compose.ui.graphics.colorspace.TransferParameters { *; }
-keep,allowoptimization class androidx.compose.ui.graphics.colorspace.Rgb { *; }
