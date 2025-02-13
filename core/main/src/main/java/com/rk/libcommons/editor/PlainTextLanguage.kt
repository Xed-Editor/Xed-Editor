package com.rk.libcommons.editor

import android.os.Bundle
import androidx.annotation.NonNull
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.QuickQuoteHandler
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.lang.StringBuilder


class PlainTextLanguage : Language {

    val manager = object : AsyncIncrementalAnalyzeManager<Unit, HighlightToken>() {

        override fun generateSpansForLine(lineResult: IncrementalAnalyzeManager.LineTokenizeResult<Unit, HighlightToken>): List<Span> {
            return lineResult.tokens.map {
                SpanFactory.obtain(it.offset, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
            }
        }

        @NonNull
        override fun getInitialState(): Unit = Unit
        override fun computeBlocks(
            text: Content?,
            delegate: CodeBlockAnalyzeDelegate?
        ): MutableList<CodeBlock> {
            return emptyList<CodeBlock>().toMutableList()
        }

        override fun stateEquals(state: Unit, another: Unit): Boolean = true

        override fun tokenizeLine(line: CharSequence, state: Unit, lineIndex: Int): IncrementalAnalyzeManager.LineTokenizeResult<Unit, HighlightToken> {
            return IncrementalAnalyzeManager.LineTokenizeResult(
                Unit,
                listOf(HighlightToken(0))
            ) // Treat whole line as single token
        }

        override fun reset(content: ContentReference, extraArguments: Bundle) {
            super.reset(content, extraArguments)
        }
    }

    class HighlightToken(val offset: Int)



    override fun getAnalyzeManager(): AnalyzeManager {
        return manager
    }

    override fun getQuickQuoteHandler(): QuickQuoteHandler? {
        return null
    }

    override fun destroy() {}

    override fun getInterruptionLevel(): Int {
        return Language.INTERRUPTION_LEVEL_NONE
    }

    override fun requireAutoComplete(
        content: ContentReference, position: CharPosition,
        publisher: CompletionPublisher, extraArguments: Bundle
    ) {
        publisher.addItem(SimpleCompletionItem("hello",0,"Hello"))
    }


    override fun getIndentAdvance(text: ContentReference, line: Int, column: Int): Int = 0
    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter {
        return object : Formatter{
            override fun format(text: Content, cursorRange: TextRange) {}
            override fun formatRegion(
                text: Content,
                rangeToFormat: TextRange,
                cursorRange: TextRange
            ){}
            override fun setReceiver(receiver: Formatter.FormatResultReceiver?) {}
            override fun isRunning(): Boolean { return false }
            override fun destroy() {}
        }
    }

    override fun getSymbolPairs(): SymbolPairMatch = SymbolPairMatch()
    override fun getNewlineHandlers(): Array<NewlineHandler>? = null

    companion object {
        private val COMMON_WORDS: List<String> = mutableListOf("hello", "world")
    }
}
