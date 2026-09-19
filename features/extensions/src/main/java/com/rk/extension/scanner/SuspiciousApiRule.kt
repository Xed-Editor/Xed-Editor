package com.rk.extension.scanner

import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

enum class ApiTarget {
    CLASS,
    METHOD,
    FIELD,
}

/**
 * A single suspicious API rule.
 *
 * @param target whether [descriptor] names a class, a method or a field.
 * @param descriptor for classes the type descriptor (`Lcom/foo/Bar;`), for members `Lcom/foo/Bar;->member`.
 * @param category the category assigned to matching findings.
 * @param severity the severity assigned to matching findings.
 * @param message human-readable description of why the API is suspicious.
 */
data class SuspiciousApiRule(
    val target: ApiTarget,
    val descriptor: String,
    val category: FindingCategory,
    val severity: FindingSeverity,
    val message: String,
) {
    fun matchesClass(type: String): Boolean = target == ApiTarget.CLASS && descriptor == type

    fun matchesMethod(owner: String, name: String): Boolean =
        target == ApiTarget.METHOD && descriptor == "$owner->$name"

    fun matchesField(owner: String, name: String): Boolean =
        target == ApiTarget.FIELD && descriptor == "$owner->$name"

    fun matches(reference: Reference): Boolean =
        when (reference) {
            is MethodReference ->
                matchesClass(reference.definingClass) || matchesMethod(reference.definingClass, reference.name)
            is FieldReference ->
                matchesClass(reference.definingClass) || matchesField(reference.definingClass, reference.name)
            is TypeReference -> matchesClass(reference.type)
            else -> false
        }
}

/**
 * The shared rule engine used by both [ExtensionScanner] (call-site analysis over dex references) and
 * [com.rk.extension.loader.ExtensionClassLoader] (class-resolution analysis over class descriptors).
 */
internal object SuspiciousApiRules {

    private fun classRule(
        descriptor: String,
        category: FindingCategory,
        severity: FindingSeverity,
        message: String,
    ) = SuspiciousApiRule(ApiTarget.CLASS, descriptor, category, severity, message)

    private fun methodRule(
        owner: String,
        name: String,
        category: FindingCategory,
        severity: FindingSeverity,
        message: String,
    ) = SuspiciousApiRule(ApiTarget.METHOD, "$owner->$name", category, severity, message)

    val DEFAULT: List<SuspiciousApiRule> =
        listOf(
            // Reflection
            methodRule(
                "Ljava/lang/Class;",
                "forName",
                FindingCategory.REFLECTION,
                FindingSeverity.WARNING,
                "Loads classes dynamically by name",
            ),
            methodRule(
                "Ljava/lang/Class;",
                "getDeclaredMethod",
                FindingCategory.REFLECTION,
                FindingSeverity.WARNING,
                "Looks up methods reflectively",
            ),
            methodRule(
                "Ljava/lang/Class;",
                "getDeclaredMethods",
                FindingCategory.REFLECTION,
                FindingSeverity.WARNING,
                "Enumerates methods reflectively",
            ),
            methodRule(
                "Ljava/lang/Class;",
                "getDeclaredField",
                FindingCategory.REFLECTION,
                FindingSeverity.WARNING,
                "Looks up fields reflectively",
            ),
            methodRule(
                "Ljava/lang/Class;",
                "getDeclaredFields",
                FindingCategory.REFLECTION,
                FindingSeverity.WARNING,
                "Enumerates fields reflectively",
            ),
            methodRule(
                "Ljava/lang/Class;",
                "getDeclaredConstructor",
                FindingCategory.REFLECTION,
                FindingSeverity.WARNING,
                "Looks up constructors reflectively",
            ),
            methodRule(
                "Ljava/lang/reflect/AccessibleObject;",
                "setAccessible",
                FindingCategory.REFLECTION,
                FindingSeverity.WARNING,
                "Bypasses Java access checks",
            ),
            methodRule(
                "Ljava/lang/reflect/Method;",
                "invoke",
                FindingCategory.REFLECTION,
                FindingSeverity.WARNING,
                "Invokes methods reflectively",
            ),
            methodRule(
                "Ljava/lang/reflect/Constructor;",
                "newInstance",
                FindingCategory.REFLECTION,
                FindingSeverity.WARNING,
                "Instantiates classes reflectively",
            ),
            classRule(
                "Ljava/lang/reflect/Proxy;",
                FindingCategory.REFLECTION,
                FindingSeverity.WARNING,
                "Creates dynamic proxies",
            ),

            // Dynamic code loading
            classRule(
                "Ldalvik/system/DexClassLoader;",
                FindingCategory.DYNAMIC_CODE_LOADING,
                FindingSeverity.CRITICAL,
                "Loads executable code from outside the extension APK",
            ),
            classRule(
                "Ldalvik/system/InMemoryDexClassLoader;",
                FindingCategory.DYNAMIC_CODE_LOADING,
                FindingSeverity.CRITICAL,
                "Loads executable code from memory",
            ),
            classRule(
                "Ldalvik/system/PathClassLoader;",
                FindingCategory.DYNAMIC_CODE_LOADING,
                FindingSeverity.CRITICAL,
                "Creates a class loader over arbitrary paths",
            ),
            classRule(
                "Ldalvik/system/BaseDexClassLoader;",
                FindingCategory.DYNAMIC_CODE_LOADING,
                FindingSeverity.CRITICAL,
                "Creates a dex class loader",
            ),
            classRule(
                "Ldalvik/system/DexFile;",
                FindingCategory.DYNAMIC_CODE_LOADING,
                FindingSeverity.CRITICAL,
                "Opens dex files directly",
            ),
            methodRule(
                "Ljava/lang/ClassLoader;",
                "loadClass",
                FindingCategory.DYNAMIC_CODE_LOADING,
                FindingSeverity.WARNING,
                "Loads classes through a class loader",
            ),
            methodRule(
                "Ljava/lang/ClassLoader;",
                "defineClass",
                FindingCategory.DYNAMIC_CODE_LOADING,
                FindingSeverity.CRITICAL,
                "Defines a class from raw bytes",
            ),

            // Native code
            methodRule(
                "Ljava/lang/System;",
                "load",
                FindingCategory.NATIVE_CODE,
                FindingSeverity.CRITICAL,
                "Loads a native library from a path",
            ),
            methodRule(
                "Ljava/lang/System;",
                "loadLibrary",
                FindingCategory.NATIVE_CODE,
                FindingSeverity.CRITICAL,
                "Loads a native library",
            ),
            methodRule(
                "Ljava/lang/Runtime;",
                "load",
                FindingCategory.NATIVE_CODE,
                FindingSeverity.CRITICAL,
                "Loads a native library from a path",
            ),
            methodRule(
                "Ljava/lang/Runtime;",
                "loadLibrary",
                FindingCategory.NATIVE_CODE,
                FindingSeverity.CRITICAL,
                "Loads a native library",
            ),

            // Process execution
            methodRule(
                "Ljava/lang/Runtime;",
                "exec",
                FindingCategory.PROCESS_EXECUTION,
                FindingSeverity.CRITICAL,
                "Executes an external process",
            ),
            classRule(
                "Ljava/lang/ProcessBuilder;",
                FindingCategory.PROCESS_EXECUTION,
                FindingSeverity.CRITICAL,
                "Spawns an external process",
            ),
            methodRule(
                "Ljava/lang/System;",
                "exit",
                FindingCategory.PROCESS_EXECUTION,
                FindingSeverity.WARNING,
                "Terminates the host process",
            ),

            // Network
            classRule(
                "Ljava/net/Socket;",
                FindingCategory.NETWORK,
                FindingSeverity.WARNING,
                "Opens a raw network socket",
            ),
            classRule(
                "Ljava/net/URL;",
                FindingCategory.NETWORK,
                FindingSeverity.WARNING,
                "Opens a network connection",
            ),
            classRule(
                "Ljava/net/HttpURLConnection;",
                FindingCategory.NETWORK,
                FindingSeverity.WARNING,
                "Performs an HTTP request",
            ),
            classRule(
                "Lokhttp3/OkHttpClient;",
                FindingCategory.NETWORK,
                FindingSeverity.WARNING,
                "Performs HTTP requests through OkHttp",
            ),
            classRule(
                "Lretrofit2/Retrofit;",
                FindingCategory.NETWORK,
                FindingSeverity.WARNING,
                "Performs network requests through Retrofit",
            ),
            classRule(
                "Landroid/webkit/WebView;",
                FindingCategory.NETWORK,
                FindingSeverity.WARNING,
                "Embeds a web view",
            ),
            methodRule(
                "Landroid/webkit/WebView;",
                "addJavascriptInterface",
                FindingCategory.NETWORK,
                FindingSeverity.CRITICAL,
                "Exposes host objects to JavaScript",
            ),

            // Sensitive device data
            classRule(
                "Landroid/telephony/SmsManager;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.CRITICAL,
                "Sends SMS messages",
            ),
            classRule(
                "Landroid/telephony/TelephonyManager;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.WARNING,
                "Reads telephony information",
            ),
            classRule(
                "Landroid/location/LocationManager;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.WARNING,
                "Accesses device location",
            ),
            classRule(
                "Landroid/hardware/camera2/CameraManager;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.WARNING,
                "Accesses the camera",
            ),
            classRule(
                "Landroid/media/AudioRecord;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.WARNING,
                "Records audio",
            ),
            classRule(
                "Landroid/accounts/AccountManager;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.WARNING,
                "Accesses device accounts",
            ),
            classRule(
                "Landroid/content/ContentResolver;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.WARNING,
                "Reads or writes shared content providers",
            ),
            classRule(
                "Landroid/provider/ContactsContract;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.WARNING,
                "Accesses contacts",
            ),
            classRule(
                "Landroid/provider/CallLog;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.WARNING,
                "Accesses the call log",
            ),
            classRule(
                "Landroid/provider/MediaStore;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.INFO,
                "Accesses shared media",
            ),
            classRule(
                "Landroid/content/ClipboardManager;",
                FindingCategory.SENSITIVE_DATA,
                FindingSeverity.INFO,
                "Reads or writes the clipboard",
            ),

            // Android internals
            classRule(
                "Landroid/app/ActivityThread;",
                FindingCategory.SYSTEM_INTERNAL,
                FindingSeverity.CRITICAL,
                "Uses hidden Android internals",
            ),
            classRule(
                "Landroid/os/ServiceManager;",
                FindingCategory.SYSTEM_INTERNAL,
                FindingSeverity.CRITICAL,
                "Reaches into Android system services",
            ),
            classRule(
                "Landroid/app/ActivityManager;",
                FindingCategory.SYSTEM_INTERNAL,
                FindingSeverity.WARNING,
                "Inspects or manipulates running processes",
            ),
            methodRule(
                "Landroid/app/ActivityManager;",
                "killBackgroundProcesses",
                FindingCategory.SYSTEM_INTERNAL,
                FindingSeverity.CRITICAL,
                "Kills background processes",
            ),
            classRule(
                "Landroid/os/Process;",
                FindingCategory.SYSTEM_INTERNAL,
                FindingSeverity.CRITICAL,
                "Manipulates OS processes",
            ),

            // File system
            classRule(
                "Landroid/os/Environment;",
                FindingCategory.FILE_SYSTEM,
                FindingSeverity.WARNING,
                "Accesses external storage paths",
            ),

            // Cryptography / encoding
            classRule(
                "Ljavax/crypto/Cipher;",
                FindingCategory.OBFUSCATION,
                FindingSeverity.INFO,
                "Uses cryptography",
            ),
            classRule(
                "Landroid/util/Base64;",
                FindingCategory.OBFUSCATION,
                FindingSeverity.INFO,
                "Uses Base64 encoding",
            ),
        )

    fun matchClass(type: String): List<SuspiciousApiRule> = DEFAULT.filter { it.matchesClass(type) }

    fun matchMethod(owner: String, name: String): List<SuspiciousApiRule> =
        DEFAULT.filter { it.matchesMethod(owner, name) }

    fun matchField(owner: String, name: String): List<SuspiciousApiRule> =
        DEFAULT.filter { it.matchesField(owner, name) }

    fun match(reference: Reference): List<SuspiciousApiRule> = DEFAULT.filter { it.matches(reference) }
}
