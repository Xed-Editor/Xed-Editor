package com.rk.editor.intelligent

import androidx.compose.runtime.mutableStateListOf
import com.rk.editor.Editor
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.KeyBindingEvent

object IntelligentFeatureRegistry {
    val builtInFeatures = listOf(AutoCloseTag, BulletContinuation)

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
    open val triggerCharacters: List<Char> = emptyList()

    open fun handleInsertChar(triggerCharacter: Char, editor: Editor) {}

    open fun handleDeleteChar(triggerCharacter: Char, editor: Editor) {}

    open fun handleInsert(editor: Editor) {}

    open fun handleDelete(editor: Editor) {}

    open fun handleKeyEvent(event: EditorKeyEvent, editor: Editor) {}

    open fun handleKeyBindingEvent(event: KeyBindingEvent, editor: Editor) {}

    open fun isEnabled(): Boolean = true
}
