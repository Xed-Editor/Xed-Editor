package com.rk.editor

import com.rk.utils.application
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.SymbolPairMatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object LanguageManager {
    private val grammarRegistryInitialized = CompletableDeferred<Unit>()

    suspend fun initGrammarRegistry() {
        if (grammarRegistryInitialized.isCompleted) return

        withContext(Dispatchers.IO) {
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(application!!.assets))
            GrammarRegistry.getInstance().loadGrammars(TEXTMATE_PREFIX + LANGUAGES_FILE)

            grammarRegistryInitialized.complete(Unit)
        }
    }

    suspend fun createLanguage(textmateScope: String, createIdentifiers: Boolean = true): TextMateLanguage {
        grammarRegistryInitialized.await()
        return TextMateLanguage.create(textmateScope, createIdentifiers)
    }

    fun createLanguageBlocking(textmateScope: String, createIdentifiers: Boolean = true): TextMateLanguage =
        runBlocking {
            createLanguage(textmateScope, createIdentifiers)
        }

    fun wrapWithFormatter(language: Language, formatter: Formatter): Language {
        return object : Language {
            override fun getAnalyzeManager(): AnalyzeManager = language.analyzeManager
            override fun getInterruptionLevel(): Int = language.interruptionLevel
            override fun requireAutoComplete(
                content: ContentReference,
                position: CharPosition,
                publisher: CompletionPublisher,
                extraArguments: android.os.Bundle
            ) {
                language.requireAutoComplete(content, position, publisher, extraArguments)
            }
            override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int =
                language.getIndentAdvance(content, line, column)
            override fun useTab(): Boolean = language.useTab()
            override fun getFormatter(): Formatter = formatter
            override fun getSymbolPairs(): SymbolPairMatch = language.symbolPairs
            override fun getNewlineHandlers(): Array<NewlineHandler> = language.newlineHandlers ?: emptyArray()
            override fun destroy() = language.destroy()
        }
    }
}
