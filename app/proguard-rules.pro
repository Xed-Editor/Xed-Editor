# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn java.lang.management.ManagementFactory
-dontwarn javax.management.InstanceAlreadyExistsException
-dontwarn javax.management.InstanceNotFoundException
-dontwarn javax.management.JMException
-dontwarn javax.management.MBeanRegistrationException
-dontwarn javax.management.MBeanServer
-dontwarn javax.management.MalformedObjectNameException
-dontwarn javax.management.NotCompliantMBeanException
-dontwarn javax.management.ObjectInstance
-dontwarn javax.management.ObjectName
-dontwarn javax.script.ScriptEngineFactory
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
-keep class org.eclipse.jgit.util.SystemReader { *; }
-keep class org.eclipse.jgit.storage.file.FileBasedConfig { *; }
-dontwarn org.eclipse.jgit.**
# Preserve all fields for classes serialized/deserialized by Gson
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter { *; }
-keep class * extends com.google.gson.TypeAdapterFactory { *; }
-keep class * extends com.google.gson.JsonSerializer { *; }
-keep class * extends com.google.gson.JsonDeserializer { *; }
-keep class * extends com.google.gson.JsonElement { *; }

# Keep fields with @SerializedName annotation (used for serialization/deserialization)
-keepattributes *Annotation*

# Keep classes used by Gson for reflection-based deserialization
-keep class ** extends com.google.gson.reflect.TypeToken { *; }

# Keep generic signatures; needed for correct type resolution
-keepattributes Signature

# Keep class TypeToken (respectively its generic signature)
-keep class com.google.gson.reflect.TypeToken { *; }

# Keep any (anonymous) classes extending TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keep class org.joni.ast.QuantifierNode { *; }
-keep class com.rk.libPlugin.server.Manifest.** { *; }



# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Prevent R8 from leaving Data object members always null
-keepclasseswithmembers class * {
    <init>(...);
    @com.google.gson.annotations.SerializedName <fields>;
}
# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep class com.rk.xededitor.App { *; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.rk.libPlugin.server.Manifest { *; }


# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep the no-args constructor of the deserialized class
-keepclassmembers class com.rk.libPlugin.server.Manifest {
  <init>();
}

-keep,allowobfuscation,allowoptimization class com.rk.libPlugin.server.Manifest {
  @com.google.gson.annotations.SerializedName <fields>;
}


-keep class com.rk.libPlugin.server.Manifest {
   <fields>;
}



-keepclassmembers class com.rk.libPlugin.server.Manifest {
 !transient <fields>;
}

# Keep classes and members for all models used with Gson
-keep class com.rk.xededitor.** { *; }
-keep class com.rk.libPlugin.** { *; }

# Keep all classes that might be used by Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all classes with a no-arg constructor
-keepclassmembers class * {
    public <init>();
}

# Keep all classes that are referenced by your Manifest class
-keep class * extends l6.g { *; }

# If l6.g is an interface, use this instead:
# -keep interface l6.g { *; }
# -keep class * implements l6.g { *; }

# Explicitly keep the class mentioned in the error
-keep class l6.g { *; }

# If Gson is dynamically creating adapters, we need to keep all TypeAdapterFactory implementations
-keepclassmembers class * implements com.google.gson.TypeAdapterFactory {
    public <init>();
}

# If you're using any custom TypeAdapter, make sure to keep those as well
-keep class * extends com.google.gson.TypeAdapter { *; }