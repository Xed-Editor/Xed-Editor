package com.rk.editor

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KeywordManager {
    private val keywordRegistryInitialized = CompletableDeferred<Unit>()
    private lateinit var keywords: Map<String, List<String>>

    suspend fun initKeywordRegistry(context: Context) {
        if (keywordRegistryInitialized.isCompleted) return

        withContext(Dispatchers.IO) {
            context.assets.open(TEXTMATE_PREFIX + KEYWORDS_FILE).use {
                val gson = Gson()
                val typeToken = object : TypeToken<Map<String, List<String>>>() {}
                keywords = gson.fromJson(InputStreamReader(it), typeToken)
            }

            keywordRegistryInitialized.complete(Unit)
        }
    }

    suspend fun getKeywords(textmateScope: String): List<String>? {
        keywordRegistryInitialized.await()
        return keywords[textmateScope]
    }
}
