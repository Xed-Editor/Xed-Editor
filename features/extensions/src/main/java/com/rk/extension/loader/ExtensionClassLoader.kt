package com.rk.extension.loader

import com.rk.extension.LocalExtension
import com.rk.extension.api.RestrictedAPI
import com.rk.extension.api.logWarn
import com.rk.extension.apkFile
import com.rk.extension.scanner.SuspiciousApiRule
import com.rk.extension.scanner.SuspiciousApiRules
import dalvik.system.PathClassLoader
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * A [PathClassLoader] used to load the APK of a single [LocalExtension].
 *
 * Two checks run while classes are resolved:
 * - Classes or members annotated with [RestrictedAPI] are denied with a [RestrictedApiException].
 * - Classes matching a rule from [SuspiciousApiRules] are reported through the extension logger; loading continues.
 *
 * Only *class-level* suspicious rules can be evaluated here: a class loader observes which classes are linked, not
 * which members are invoked. Member-level analysis is left to [com.rk.extension.scanner.ExtensionScanner].
 *
 * @param extension the extension whose APK is loaded.
 * @param parent the parent class loader that exposes the editor APIs.
 * @param ignoredPackages classes whose binary name starts with one of these prefixes are not inspected for restricted
 *   members, keeping the check cheap and avoiding walking the Android/Kotlin runtime.
 */
class ExtensionClassLoader(
    val extension: LocalExtension,
    parent: ClassLoader?,
    private val ignoredPackages: Set<String> = DEFAULT_IGNORED_PACKAGES,
) : PathClassLoader(extension.apkFile.absolutePath, parent) {

    private val checked = ConcurrentHashMap.newKeySet<String>()
    private val denied = ConcurrentHashMap.newKeySet<String>()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        val clazz = super.loadClass(name, resolve)
        verify(clazz)
        return clazz
    }

    private fun verify(clazz: Class<*>) {
        val name = clazz.name

        // A denied class stays denied: the VM has already defined it, so every future lookup must keep failing.
        if (denied.contains(name)) {
            throw RestrictedApiException(name, extensionName = extension.name)
        }

        // `checked` deduplicates the work and prevents recursion through self-referential class graphs.
        if (!checked.add(name)) {
            return
        }

        reportSuspiciousClass(name)

        if (!shouldVerify(name)) {
            return
        }

        val member = findRestrictedMember(clazz)
        if (member != null) {
            denied.add(name)
            throw RestrictedApiException(member, extensionName = extension.name)
        }
    }

    private fun shouldVerify(name: String): Boolean = ignoredPackages.none { name.startsWith(it) }

    private fun reportSuspiciousClass(name: String) {
        val descriptor = descriptorOf(name)
        for (rule in SuspiciousApiRules.matchClass(descriptor)) {
            logSuspicious(rule, descriptor)
        }
    }

    private fun findRestrictedMember(clazz: Class<*>): String? {
        if (clazz.isAnnotationPresent(RestrictedAPI::class.java)) {
            return clazz.name
        }

        val methods = try { clazz.declaredMethods } catch (_: Throwable) { emptyArray<Method>() }
        for (method in methods) {
            if (method.isAnnotationPresent(RestrictedAPI::class.java)) {
                return "${clazz.name}#${method.name}"
            }
        }

        val fields = try { clazz.declaredFields } catch (_: Throwable) { emptyArray<Field>() }
        for (field in fields) {
            if (field.isAnnotationPresent(RestrictedAPI::class.java)) {
                return "${clazz.name}#${field.name}"
            }
        }

        val constructors =
            try {
                clazz.declaredConstructors
            } catch (_: Throwable) {
                emptyArray<Constructor<*>>()
            }
        for (constructor in constructors) {
            if (constructor.isAnnotationPresent(RestrictedAPI::class.java)) {
                return "${clazz.name}<init>"
            }
        }

        return null
    }

    private fun logSuspicious(rule: SuspiciousApiRule, reference: String) {
        extension.id.logWarn("[classloader] ${rule.severity} ${rule.category}: ${rule.message} ($reference)")
    }

    private fun descriptorOf(name: String): String =
        if (name.startsWith("[")) name.replace('.', '/') else "L${name.replace('.', '/')};"

    companion object {
        private val DEFAULT_IGNORED_PACKAGES =
            setOf(
                "java.",
                "javax.",
                "sun.",
                "com.sun.",
                "dalvik.",
                "libcore.",
                "android.",
                "androidx.",
                "kotlin.",
                "kotlinx.",
                "org.jetbrains.",
                "org.intellij.",
                "org.w3c.",
                "org.xml.",
                "org.json.",
                "org.apache.",
                "com.google.",
                "com.android.",
            )
    }
}

/** Thrown when an extension attempts to resolve a class or member annotated with [RestrictedAPI]. */
class RestrictedApiException(api: String, extensionName: String?) :
    SecurityException(
        buildString {
            append("Access to restricted API '")
            append(api)
            append("' is denied")
            if (extensionName != null) {
                append(" for extension '")
                append(extensionName)
                append("'")
            }
            append(": the API is annotated with @RestrictedAPI")
        }
    )
