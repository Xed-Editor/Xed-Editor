package com.rk.editor

import com.rk.events.Events
import com.rk.events.LSPEvent
import com.rk.settings.Settings
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.inlayHint.ColorInlayHint
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.lsp.editor.LspLanguage
import io.github.rosemoe.sora.lsp.utils.ColorUtils
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.TextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.milliseconds

class DefaultColorProvider(private val editor: Editor) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var updateJob: Job? = null
    private val colorPattern =
        Pattern.compile("(?<![A-Za-z0-9_])#([A-Fa-f0-9]{3}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})(?![A-Za-z0-9_])")
    private var cachedInlayHints: InlayHintsContainer? = null

    private val contentChangeSubscription =
        editor.subscribeAlways(ContentChangeEvent::class.java) {
            requestUpdate()
        }
    private val lspConnectionSubscription =
        Events.subscribe<LSPEvent.ConnectionCompleted> { event ->
            if (event.editor == editor) {
                requestUpdate()
            }
        }

    init {
        requestUpdate()
    }

    fun requestUpdate() {
        updateJob?.cancel()
        updateJob = scope.launch {
            delay(200.milliseconds)

            if (!Settings.show_color_previews) {
                if (cachedInlayHints != null) {
                    withContext(Dispatchers.Main) {
                        cachedInlayHints = null
                        editor.inlayHints = InlayHintsContainer()
                    }
                }
                return@launch
            }

            val language = editor.editorLanguage
            if (language is LspLanguage) {
                val lspEditor = language.editor
                if (lspEditor.isConnected) {
                    val capabilities = lspEditor.requestManager.capabilities
                    if (capabilities?.colorProvider?.left == true || capabilities?.colorProvider?.right != null) {
                        withContext(Dispatchers.Main) {
                            cachedInlayHints = null
                            editor.inlayHints = InlayHintsContainer()
                        }
                        return@launch
                    }
                }
            }

            val text = editor.text
            val container = InlayHintsContainer()

            for (i in 0 until text.lineCount) {
                val lineText = text.getLineString(i)
                val matcher = colorPattern.matcher(lineText)
                while (matcher.find()) {
                    val colorStr = matcher.group()
                    val color = ColorUtils.parseColor(colorStr) ?: continue
                    container.add(
                        ColorInlayHint(
                            i,
                            matcher.start(),
                            ConstColor(color),
                            TextRange(
                                CharPosition(i, matcher.start()),
                                CharPosition(i, matcher.end()),
                            ),
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                if (container.getLineNumbers().isEmpty()) {
                    cachedInlayHints = null
                    editor.inlayHints = InlayHintsContainer()
                } else {
                    cachedInlayHints = container
                    editor.inlayHints = container
                }
            }
        }
    }

    fun dispose() {
        contentChangeSubscription.unsubscribe()
        lspConnectionSubscription.unsubscribe()
        updateJob?.cancel()
        editor.inlayHints = InlayHintsContainer()
    }
}
