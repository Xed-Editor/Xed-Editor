package com.rk.extension.scanner

enum class FindingSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

enum class FindingCategory {
    /** The extension links against a class or member annotated with `@RestrictedAPI`. */
    RESTRICTED_API,
    /** Use of `java.lang.reflect` to bypass access checks or load classes by name. */
    REFLECTION,
    /** Loading executable code from outside the extension APK (dex/class loaders). */
    DYNAMIC_CODE_LOADING,
    /** Loading native libraries. */
    NATIVE_CODE,
    /** Spawning external processes or shell commands. */
    PROCESS_EXECUTION,
    /** Opening network connections or embedding a web view. */
    NETWORK,
    /** Accessing sensitive device data (location, contacts, accounts, ...). */
    SENSITIVE_DATA,
    /** Reaching into Android internals that are not part of the public SDK. */
    SYSTEM_INTERNAL,
    /** Accessing the file system outside the extension sandbox. */
    FILE_SYSTEM,
    /** Use of cryptography / encoding that is often seen in obfuscated payloads. */
    OBFUSCATION,
}


data class Finding(
    val category: FindingCategory,
    val severity: FindingSeverity,
    val message: String,
    val className: String,
    val methodName: String? = null,
    val reference: String? = null,
) {
    val isBlocking: Boolean
        get() = category == FindingCategory.RESTRICTED_API

    /**
     * The class descriptor [reference] points to, or `null` when no reference was recorded.
     *
     * References are formatted as a class descriptor (`Lcom/foo/Bar;`), a method (`Lcom/foo/Bar;->m()V`) or a field
     * (`Lcom/foo/Bar;->f:I`), so the owning class is everything before the `->` separator when present.
     */
    val referencedClass: String?
        get() = reference?.substringBefore("->")?.takeIf { it.isNotBlank() }
}
/**
 * Asks the user whether an extension that produced [findings] should still be installed.
 *
 * Suspending lets the install pipeline pause while the report is shown; returning `false` cancels the installation.
 */
typealias ScanApproval = suspend (extensionName: String, findings: List<Finding>) -> Boolean
