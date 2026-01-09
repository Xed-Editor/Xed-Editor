package com.rk.editor.intelligent

import androidx.compose.runtime.mutableStateListOf
import com.rk.editor.Editor

object IntelligentFeatureRegistry {
    val builtInFeatures = listOf(AutoCloseTag)

    private val mutableFeatures = mutableStateListOf<IntelligentFeature>()
    val extensionFeatures: List<IntelligentFeature>
        get() = mutableFeatures.toList()

    val allFeatures: List<IntelligentFeature>
        get() = builtInFeatures + mutableFeatures

    fun registerFeature(feature: IntelligentFeature) {
        if (!mutableFeatures.contains(feature)) {
            mutableFeatures.add(feature)
        }
    }

    fun unregisterFeature(feature: IntelligentFeature) {
        mutableFeatures.remove(feature)
    }
}

abstract class IntelligentFeature {
    abstract val id: String
    abstract val supportedExtensions: List<String>
    abstract val triggerCharacters: List<Char>

    abstract fun handle(triggerCharacter: Char, editor: Editor)

    open fun isEnabled(): Boolean = true
}
