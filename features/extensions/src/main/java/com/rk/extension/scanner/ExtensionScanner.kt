package com.rk.extension.scanner

import android.os.Build
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.DualReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.rk.extension.LocalExtension
import com.rk.extension.apkFile
import com.rk.utils.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


class ExtensionScanner {

    suspend fun scan(extension: LocalExtension, restrictedApis: RestrictedApiIndex? = null): List<Finding> =
        scan(extension.apkFile, extension.name, restrictedApis)

    suspend fun scan(
        apkFile: File,
        extensionName: String? = null,
        restrictedApis: RestrictedApiIndex? = null,
    ): List<Finding> {
        val index =
            restrictedApis
                ?: application?.let { RestrictedApiIndex.load(it) }
                ?: RestrictedApiIndex.EMPTY

        return withContext(Dispatchers.IO) {
            val findings = LinkedHashSet<Finding>()
            val container = DexFileFactory.loadDexContainer(apkFile, Opcodes.forApi(Build.VERSION.SDK_INT))

            for (entryName in container.dexEntryNames) {
                val dex = container.getEntry(entryName)?.dexFile ?: continue
                for (classDef in dex.classes) {
                    scanClass(classDef, index, findings)
                }
            }

            findings.toList()
        }
    }

    private fun scanClass(classDef: ClassDef, index: RestrictedApiIndex, findings: MutableSet<Finding>) {
        classDef.superclass?.let { superclass ->
            if (index.isClassRestricted(superclass)) {
                findings.add(restrictedClassFinding(classDef, null, superclass))
            }
        }

        for (iface in classDef.interfaces) {
            if (index.isClassRestricted(iface)) {
                findings.add(restrictedClassFinding(classDef, null, iface))
            }
        }

        for (method in classDef.methods) {
            val implementation = method.implementation ?: continue
            for (instruction in implementation.instructions) {
                if (instruction !is ReferenceInstruction) continue

                inspectReference(classDef, method, instruction.reference, index, findings)

                if (instruction is DualReferenceInstruction) {
                    inspectReference(classDef, method, instruction.reference2, index, findings)
                }
            }
        }
    }

    private fun inspectReference(
        classDef: ClassDef,
        method: Method,
        reference: Reference,
        index: RestrictedApiIndex,
        findings: MutableSet<Finding>,
    ) {
        when (reference) {
            is MethodReference -> {
                when {
                    index.isClassRestricted(reference.definingClass) ->
                        findings.add(restrictedClassFinding(classDef, method, reference.definingClass))
                    index.isMethodRestricted(reference) ->
                        findings.add(
                            Finding(
                                category = FindingCategory.RESTRICTED_API,
                                severity = FindingSeverity.CRITICAL,
                                message = "Calls restricted method ${methodSignature(reference)}",
                                className = classDef.type,
                                methodName = method.name,
                                reference = methodSignature(reference),
                            )
                        )
                }
            }
            is FieldReference -> {
                when {
                    index.isClassRestricted(reference.definingClass) ->
                        findings.add(restrictedClassFinding(classDef, method, reference.definingClass))
                    index.isFieldRestricted(reference) ->
                        findings.add(
                            Finding(
                                category = FindingCategory.RESTRICTED_API,
                                severity = FindingSeverity.CRITICAL,
                                message = "Accesses restricted field ${fieldSignature(reference)}",
                                className = classDef.type,
                                methodName = method.name,
                                reference = fieldSignature(reference),
                            )
                        )
                }
            }
            is TypeReference -> {
                if (index.isClassRestricted(reference.type)) {
                    findings.add(restrictedClassFinding(classDef, method, reference.type))
                }
            }
        }

        for (rule in SuspiciousApiRules.match(reference)) {
            findings.add(
                Finding(
                    category = rule.category,
                    severity = rule.severity,
                    message = rule.message,
                    className = classDef.type,
                    methodName = method.name,
                    reference = reference.toString(),
                )
            )
        }
    }

    private fun restrictedClassFinding(classDef: ClassDef, method: Method?, type: String) =
        Finding(
            category = FindingCategory.RESTRICTED_API,
            severity = FindingSeverity.CRITICAL,
            message = "Uses restricted class $type",
            className = classDef.type,
            methodName = method?.name,
            reference = type,
        )
}
