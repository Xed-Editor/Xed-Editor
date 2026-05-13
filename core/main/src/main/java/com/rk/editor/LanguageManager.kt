package com.rk.editor

import com.rk.utils.application
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
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
}
