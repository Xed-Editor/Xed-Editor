package com.rk.editor

import android.content.Context
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
    private val languageCache = hashMapOf<String, TextMateLanguage>()

    suspend fun initGrammarRegistry() {
        if (grammarRegistryInitialized.isCompleted) return

        withContext(Dispatchers.IO) {
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(application!!.assets))
            GrammarRegistry.getInstance().loadGrammars(TEXTMATE_PREFIX + LANGUAGES_FILE)

            grammarRegistryInitialized.complete(Unit)
        }
    }

    suspend fun createLanguage(context: Context, textmateScope: String): TextMateLanguage {
        grammarRegistryInitialized.await()
        val cacheKey = getCacheKey(context) + "_" + textmateScope
        return languageCache.getOrPut(cacheKey) { TextMateLanguage.create(textmateScope, true) }
    }

    fun createLanguageBlocking(context: Context, textmateScope: String): TextMateLanguage = runBlocking {
        createLanguage(context, textmateScope)
    }
}
