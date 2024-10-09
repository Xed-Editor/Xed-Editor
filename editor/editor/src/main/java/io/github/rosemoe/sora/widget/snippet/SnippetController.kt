/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 ******************************************************************************/

package io.github.rosemoe.sora.widget.snippet

import android.util.Log
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.InterceptTarget
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SnippetEvent
import io.github.rosemoe.sora.lang.completion.snippet.CodeSnippet
import io.github.rosemoe.sora.lang.completion.snippet.InterpolatedShellItem
import io.github.rosemoe.sora.lang.completion.snippet.PlaceholderDefinition
import io.github.rosemoe.sora.lang.completion.snippet.PlaceholderItem
import io.github.rosemoe.sora.lang.completion.snippet.PlainPlaceholderElement
import io.github.rosemoe.sora.lang.completion.snippet.PlainTextItem
import io.github.rosemoe.sora.lang.completion.snippet.SnippetItem
import io.github.rosemoe.sora.lang.completion.snippet.VariableItem
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.snippet.variable.ClipboardBasedSnippetVariableResolver
import io.github.rosemoe.sora.widget.snippet.variable.CommentBasedSnippetVariableResolver
import io.github.rosemoe.sora.widget.snippet.variable.CompositeSnippetVariableResolver
import io.github.rosemoe.sora.widget.snippet.variable.EditorBasedSnippetVariableResolver
import io.github.rosemoe.sora.widget.snippet.variable.FileBasedSnippetVariableResolver
import io.github.rosemoe.sora.widget.snippet.variable.RandomBasedSnippetVariableResolver
import io.github.rosemoe.sora.widget.snippet.variable.TimeBasedSnippetVariableResolver
import io.github.rosemoe.sora.widget.snippet.variable.WorkspaceBasedSnippetVariableResolver
import io.github.rosemoe.sora.widget.subscribeEvent

/**
 * Manage snippet editing in editor
 *
 * @author Rosemoe
 */
class SnippetController(private val editor: CodeEditor) {

    /**
     * Language based variable resolver. User should set valid values when change language.
     */
    val commentVariableResolver = CommentBasedSnippetVariableResolver(null)

    /**
     * File based variable resolver. User should implement this class and set it when opening a new file in editor
     */
    var fileVariableResolver: FileBasedSnippetVariableResolver? = null
        set(value) {
            val old = field
            if (old != null) {
                variableResolver.removeResolver(old)
            }
            field = value
            if (value != null) {
                variableResolver.addResolver(value)
            }
        }

    /**
     * Workspace based variable resolver. User should implement this class and set it when workspace is updated
     */
    var workspaceVariableResolver: WorkspaceBasedSnippetVariableResolver? = null
        set(value) {
            val old = field
            if (old != null) {
                variableResolver.removeResolver(old)
            }
            field = value
            if (value != null) {
                variableResolver.addResolver(value)
            }
        }

    private var currentSnippet: CodeSnippet? = null
    var snippetIndex = -1
    private var tabStops: MutableList<PlaceholderItem>? = null
    private var currentTabStopIndex = -1
    private var inSequenceEdits = false

    private val variableResolver = CompositeSnippetVariableResolver().also {
        it.addResolver(ClipboardBasedSnippetVariableResolver(editor.clipboardManager))
        it.addResolver(EditorBasedSnippetVariableResolver(editor))
        it.addResolver(RandomBasedSnippetVariableResolver())
        it.addResolver(TimeBasedSnippetVariableResolver())
        it.addResolver(commentVariableResolver)
    }

    init {
        editor.subscribeEvent<SelectionChangeEvent> { event, _ ->
            if (isInSnippet()) {
                if (!checkIndex(event.left.index) || !checkIndex(event.right.index)) {
                    stopSnippet()
                }
            }
        }
        editor.subscribeEvent<ContentChangeEvent> { event, _ ->
            if (isInSnippet() && !inSequenceEdits) {
                if (event.action == ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                    stopSnippet()
                } else if (event.action == ContentChangeEvent.ACTION_INSERT) {
                    if (checkIndex(event.changeStart.index)) {
                        var exitOnEnd = false;
                        val addedTextLength = if (event.changedText.contains(lineSeparatorRegex)) {
                            exitOnEnd = true
                            event.changedText.indexOfFirst { it == '\r' || it == '\n' }
                        } else {
                            event.changedText.length
                        }
                        // Shift current text
                        val editing = getEditingTabStop()!!
                        editing.setIndex(editing.startIndex, editing.endIndex + addedTextLength)
                        var metEditing = false
                        currentSnippet!!.items.forEach {
                            if (metEditing) {
                                it.shiftIndex(event.changedText.length)
                            } else if (it == editing) {
                                metEditing = true
                            }
                        }
                        inSequenceEdits = true
                        val text = editor.text
                        var hasChangedText = false
                        val replacement = text.substring(editing.startIndex, editing.endIndex)
                        text.beginBatchEdit()
                        currentSnippet!!.items.forEachIndexed { index, snippetItem ->
                            if (isEditingRelated(snippetItem)) {
                                hasChangedText = true
                                val deltaIndex =
                                    replacement.length - (snippetItem.endIndex - snippetItem.startIndex)
                                text.replace(
                                    snippetItem.startIndex,
                                    snippetItem.endIndex,
                                    replacement
                                )
                                snippetItem.setIndex(
                                    snippetItem.startIndex,
                                    snippetItem.endIndex + deltaIndex
                                )
                                shiftItemsFrom(index + 1, deltaIndex)
                            }
                        }
                        text.endBatchEdit()
                        inSequenceEdits = false
                        if (exitOnEnd) {
                            stopSnippet()
                        } else if (hasChangedText) {
                            editor.getComponent<EditorAutoCompletion>().requireCompletion()
                        }
                    } else {
                        stopSnippet()
                    }
                } else if (event.action == ContentChangeEvent.ACTION_DELETE) {
                    if (!checkIndex(event.changeStart.index) || !checkIndex(event.changeEnd.index)) {
                        stopSnippet()
                    } else {
                        editor.text.undoManager.exitReplaceMode()
                        val deletedTextLength = event.changedText.length
                        val editing = getEditingTabStop()!!
                        editing.setIndex(editing.startIndex, editing.endIndex - deletedTextLength)
                        var metEditing = false
                        currentSnippet!!.items.forEach {
                            if (metEditing) {
                                it.shiftIndex(-deletedTextLength)
                            } else if (it == editing) {
                                metEditing = true
                            }
                        }
                        inSequenceEdits = true
                        val text = editor.text
                        val replacement = text.substring(editing.startIndex, editing.endIndex)
                        text.beginBatchEdit()
                        currentSnippet!!.items.forEachIndexed { index, snippetItem ->
                            if (isEditingRelated(snippetItem)) {
                                val deltaIndex =
                                    replacement.length - (snippetItem.endIndex - snippetItem.startIndex)
                                text.replace(
                                    snippetItem.startIndex,
                                    snippetItem.endIndex,
                                    replacement
                                )
                                snippetItem.setIndex(
                                    snippetItem.startIndex,
                                    snippetItem.endIndex + deltaIndex
                                )
                                shiftItemsFrom(index + 1, deltaIndex)
                            }
                        }
                        text.endBatchEdit()
                        inSequenceEdits = false
                    }
                }
            }
        }
    }

    private fun checkIndex(index: Int): Boolean {
        val editing = getEditingTabStop()!!
        return index >= editing.startIndex && index <= editing.endIndex
    }

    /**
     * Start a new snippet editing. The given [CodeSnippet] must pass the checks in [CodeSnippet.checkContent].
     * Otherwise, the snippet editing will not be started.
     * No matter whether a new snippet editing is started, the existing snippet editing will get cancelled after
     *  calling this method.
     */
    fun startSnippet(index: Int, snippet: CodeSnippet, selectedText: String = "") {
        if (snippetIndex != -1) {
            stopSnippet()
        }
        // Stage 1: verify the snippet structure
        if (!snippet.checkContent() || snippet.items.size == 0) {
            Log.e("SnippetController", "invalid code snippet")
            return
        }
        val clonedSnippet = snippet.clone()
        currentSnippet = clonedSnippet
        currentTabStopIndex = -1
        snippetIndex = index
        // Stage 2: resolve the variables and execute shell codes
        val elements = clonedSnippet.items!!
        val variableItemMapping = mutableMapOf<String, PlaceholderDefinition>()
        var maxTabStop = 0;
        elements.forEach {
            if (it is PlaceholderItem && it.definition.id > maxTabStop) {
                maxTabStop = it.definition.id
            }
        }
        for (i in 0 until elements.size) {
            val item = elements[i]
            if (item is VariableItem) {
                var value = when {
                    variableResolver.canResolve(item.name) -> variableResolver.resolve(item.name)
                    item.name == "selection" -> selectedText
                    item.defaultValue != null -> item.defaultValue
                    else -> null
                }
                if (value != null) {
                    // Resolved variable value
                    value = TransformApplier.doTransform(value, item.transform)
                    val deltaIndex = value.length - (item.endIndex - item.startIndex)
                    elements[i] = PlainTextItem(
                        value,
                        item.startIndex,
                        item.startIndex + value.length
                    )
                    shiftItemsFrom(i + 1, deltaIndex)
                } else {
                    // Convert to placeholder
                    val def = if (variableItemMapping.contains(item.name)) {
                        variableItemMapping[item.name]!!
                    } else {
                        val definition = PlaceholderDefinition(++maxTabStop)
                        definition.text = item.name
                        variableItemMapping[item.name] = definition
                        definition
                    }
                    elements[i] = PlaceholderItem(def, item.startIndex)
                    val deltaIndex = item.name.length - (item.endIndex - item.startIndex)
                    shiftItemsFrom(i + 1, deltaIndex)
                }
            } else if (item is InterpolatedShellItem) {
                var value = try {
                    val proc = Runtime.getRuntime().exec("sh")
                    proc.outputStream.apply {
                        write(item.shellCode.encodeToByteArray())
                        write("\nexit\n".encodeToByteArray())
                        flush()
                    }
                    proc.inputStream.bufferedReader().readText()
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
                val lastChar = value[value.lastIndex]
                if (value.isNotEmpty() && lastChar == '\n' || lastChar == '\r') {
                    value =
                        if (lastChar == '\r' || (value.lastIndex > 0 && value[value.lastIndex - 1] != '\r') || value.lastIndex == 0) {
                            value.substring(0, value.lastIndex)
                        } else {
                            value.substring(0, value.lastIndex - 1)
                        }
                }
                val deltaIndex = value.length - (item.endIndex - item.startIndex)
                elements[i] = PlainTextItem(
                    value,
                    item.startIndex,
                    item.startIndex + value.length
                )
                shiftItemsFrom(i + 1, deltaIndex)
            }
        }
        // Stage 3: clean useless items and shift all items to editor index
        val itr = clonedSnippet.items.iterator()
        while (itr.hasNext()) {
            val item = itr.next()
            if ((item is PlainTextItem && item.text.isEmpty())) {
                itr.remove()
            }
        }
        shiftItemsFrom(0, index)
        // Stage 4: make correct indentation
        val text = editor.text
        val pos = text.indexer.getCharPosition(index)
        val line = text.getLine(pos.line)
        var indentEnd = 0
        for (i in 0 until pos.column) {
            val char = line[i]
            if (char == ' ' || char == '\t') {
                indentEnd++
            } else {
                break
            }
        }
        val indentText = line.substring(0, indentEnd)
        val items = clonedSnippet.items
        for (i in 0 until items.size) {
            val snippetItem = items[i]
            if (snippetItem is PlainTextItem && snippetItem.text.contains(lineSeparatorRegex)) {
                var first = true
                val sb = StringBuilder()

                snippetItem.text.split(lineSeparatorRegex).forEach {
                    if (first) {
                        sb.append(it)
                        first = false
                    } else {
                        sb.append(editor.lineSeparator.content)
                            .append(indentText)
                            .append(it)
                    }
                }
                val deltaIndex = sb.length - snippetItem.text.length
                snippetItem.text = sb.toString()
                snippetItem.setIndex(snippetItem.startIndex, snippetItem.endIndex + deltaIndex)
                shiftItemsFrom(i + 1, deltaIndex)
            } else if (snippetItem is PlaceholderItem) {
                val definition = snippetItem.definition
                if (definition.elements.isEmpty()) {
                    continue
                }

                val sb = StringBuilder()
                var deltaIndex = 0
                for (element in snippetItem.definition.elements) {
                    if (element is PlainPlaceholderElement) {
                        sb.append(element.text)
                        deltaIndex += element.text.length
                    } else if (element is VariableItem) {
                        var value = when {
                            variableResolver.canResolve(element.name) -> variableResolver.resolve(element.name)
                            element.name == "selection" -> selectedText
                            element.defaultValue != null -> element.defaultValue
                            else -> null
                        }

                        if (value != null) {
                            value = TransformApplier.doTransform(value, element.transform)
                            sb.append(value)
                            deltaIndex += value.length
                        } else {
                            sb.append(element.name)
                            deltaIndex += element.name.length
                        }
                    }
                }

                definition.text = sb.toString()
                snippetItem.setIndex(snippetItem.startIndex, snippetItem.endIndex + deltaIndex)
                shiftItemsFrom(i + 1, deltaIndex)
            }
        }
        // Stage 5: collect tab stops and placeholders
        val tabStops = mutableListOf<PlaceholderItem>()
        clonedSnippet.items.forEach { item ->
            if (item is PlaceholderItem) {
                if (item.definition.id != 0 && tabStops.find { it.definition == item.definition } == null) {
                    tabStops.add(item)
                }
            }
        }
        tabStops.sortWith { a, b ->
            a.definition.id.compareTo(b.definition.id)
        }
        var end =
            clonedSnippet.items.find { it is PlaceholderItem && it.definition.id == 0 } as PlaceholderItem?
        if (end == null) {
            end = PlaceholderItem(
                PlaceholderDefinition(0), elements.last().endIndex
            )
            clonedSnippet.items.add(end)
        }
        tabStops.add(end)
        this.tabStops = tabStops
        // Stage 6: insert the text
        val sb = StringBuilder()
        clonedSnippet.items.forEach {
            if (it is PlainTextItem) {
                sb.append(it.text)
            } else if (it is PlaceholderItem) {
                val definition = it.definition
                if (!definition.text.isNullOrEmpty()) {
                    sb.append(definition.text)
                }
            }
        }
        text.insert(pos.line, pos.column, sb)
        // Stage 7: shift to the first tab stop
        if ((editor.dispatchEvent(
                SnippetEvent(
                    editor,
                    SnippetEvent.ACTION_START,
                    currentTabStopIndex,
                    tabStops.size
                )
            ) and InterceptTarget.TARGET_EDITOR) != 0
        ) {
            stopSnippet()
            return
        }
        shiftToTabStop(0)
    }

    /**
     * Check whether the editor in snippet editing
     */
    fun isInSnippet() = snippetIndex != -1 && currentTabStopIndex != -1

    fun getEditingTabStop() = if (snippetIndex == -1) null else tabStops!![currentTabStopIndex]

    fun getTabStopAt(index: Int) = tabStops?.get(index)

    fun getTabStopCount() = tabStops?.size ?: 0

    fun getEditingRelatedTabStops(): List<SnippetItem> {
        val editing = getEditingTabStop()
        if (editing != null) {
            return currentSnippet!!.items!!.filter { it is PlaceholderItem && it.definition == editing.definition && it != editing }
        }
        return emptyList()
    }

    fun isEditingRelated(it: SnippetItem): Boolean {
        val editing = getEditingTabStop()
        if (editing != null) {
            return it is PlaceholderItem && it.definition == editing.definition && it != editing
        }
        return false
    }

    fun getInactiveTabStops(): List<SnippetItem> {
        val editing = getEditingTabStop()
        if (editing != null) {
            return currentSnippet!!.items!!.filter { (it is PlaceholderItem && it.definition != editing.definition) }
        }
        return emptyList()
    }

    fun shiftToPreviousTabStop() {
        if (snippetIndex != -1 && currentTabStopIndex > 0) {
            shiftToTabStop(currentTabStopIndex - 1)
        }
    }

    fun shiftToNextTabStop() {
        if (snippetIndex != -1 && currentTabStopIndex < tabStops!!.size - 1) {
            shiftToTabStop(currentTabStopIndex + 1)
        }
    }

    private fun shiftToTabStop(index: Int) {
        if (snippetIndex == -1) {
            return
        }
        if (index != currentTabStopIndex && currentTabStopIndex != -1) {
            // apply transform
            val tabStop = tabStops!![currentTabStopIndex]
            if (tabStop.definition.transform != null) {
                editor.text.replace(
                    tabStop.startIndex,
                    tabStop.endIndex,
                    TransformApplier.doTransform(
                        editor.text.substring(
                            tabStop.startIndex,
                            tabStop.endIndex
                        ), tabStop.definition.transform
                    )
                )
            }
        }
        val tabStop = tabStops!![index]
        val indexer = editor.text.indexer
        val left = indexer.getCharPosition(tabStop.startIndex)
        val right = indexer.getCharPosition(tabStop.endIndex)
        currentTabStopIndex = index
        editor.setSelectionRegion(left.line, left.column, right.line, right.column)
        editor.dispatchEvent(
            SnippetEvent(
                editor,
                SnippetEvent.ACTION_SHIFT,
                currentTabStopIndex,
                tabStops!!.size
            )
        )
        if (index == tabStops!!.size - 1) {
            stopSnippet()
        }
    }

    private fun shiftItemsFrom(itemIndex: Int, deltaIndex: Int) {
        if (deltaIndex == 0) {
            return
        }
        val items = currentSnippet!!.items!!
        for (i in itemIndex until items.size) {
            items[i].shiftIndex(deltaIndex)
        }
    }

    /**
     * Stop snippet editing
     */
    fun stopSnippet() {
        if (!isInSnippet()) {
            return
        }
        currentSnippet = null
        snippetIndex = -1
        tabStops = null
        currentTabStopIndex = -1
        editor.dispatchEvent(SnippetEvent(editor, SnippetEvent.ACTION_STOP, currentTabStopIndex, 0))
        editor.invalidate()
    }

    companion object {
        private val lineSeparatorRegex = Regex("\\r|\\n|\\r\\n")
    }


}