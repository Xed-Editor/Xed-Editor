package com.rk.extension.scanner

import android.app.Application
import android.os.Build
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.Annotatable
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RestrictedApiIndex private constructor(
    val classes: Set<String>,
    val methods: Set<String>,
    val fields: Set<String>,
) {
    val isEmpty: Boolean
        get() = classes.isEmpty() && methods.isEmpty() && fields.isEmpty()

    fun isClassRestricted(type: String): Boolean = type in classes

    fun isMethodRestricted(reference: MethodReference): Boolean = methodSignature(reference) in methods

    fun isFieldRestricted(reference: FieldReference): Boolean = fieldSignature(reference) in fields

    companion object {
        const val RESTRICTED_API_DESCRIPTOR = "Lcom/rk/extension/api/RestrictedAPI;"

        val EMPTY = RestrictedApiIndex(emptySet(), emptySet(), emptySet())

        @Volatile private var cached: RestrictedApiIndex? = null

        suspend fun load(application: Application): RestrictedApiIndex {
            cached?.let { return it }
            return withContext(Dispatchers.IO) {
                synchronized(this@Companion) {
                    cached ?: build(application).also { cached = it }
                }
            }
        }

        private fun build(application: Application): RestrictedApiIndex {
            val classes = mutableSetOf<String>()
            val methods = mutableSetOf<String>()
            val fields = mutableSetOf<String>()

            for (source in application.apkFiles()) {
                if (!source.exists()) continue

                val container =
                    runCatching {
                        DexFileFactory.loadDexContainer(source, Opcodes.forApi(Build.VERSION.SDK_INT))
                    }
                        .getOrNull() ?: continue

                for (entryName in container.dexEntryNames) {
                    val dex = container.getEntry(entryName)?.dexFile ?: continue
                    for (classDef in dex.classes) {
                        collectRestrictions(classDef, classes, methods, fields)
                    }
                }
            }

            return RestrictedApiIndex(classes, methods, fields)
        }

        private fun collectRestrictions(
            classDef: ClassDef,
            classes: MutableSet<String>,
            methods: MutableSet<String>,
            fields: MutableSet<String>,
        ) {
            if (classDef.isRestricted()) {
                classes.add(classDef.type)
            }

            for (method in classDef.methods) {
                if (method.isRestricted()) {
                    methods.add(methodSignature(method))
                }
            }

            for (field in classDef.fields) {
                if (field.isRestricted()) {
                    fields.add(fieldSignature(field))
                }
            }
        }

        private fun Annotatable.isRestricted(): Boolean =
            annotations.any { it.type == RESTRICTED_API_DESCRIPTOR }

        private fun Application.apkFiles(): List<File> {
            val info = applicationInfo
            return buildList {
                add(File(info.sourceDir))
                info.splitSourceDirs?.forEach { add(File(it)) }
            }
        }
    }
}

/** Formats a method reference the same way the index stores it: `Lowner;->name(params)return`. */
internal fun methodSignature(reference: MethodReference): String =
    buildString {
        append(reference.definingClass)
        append("->")
        append(reference.name)
        append('(')
        reference.parameterTypes.forEach { append(it) }
        append(')')
        append(reference.returnType)
    }

/** Formats a field reference the same way the index stores it: `Lowner;->name:type`. */
internal fun fieldSignature(reference: FieldReference): String =
    "${reference.definingClass}->${reference.name}:${reference.type}"
