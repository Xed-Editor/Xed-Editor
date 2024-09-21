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

package io.github.rosemoe.sora.langs.textmate.registry.dsl

import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition
import org.eclipse.tm4e.core.registry.IGrammarSource
import java.nio.charset.Charset

fun languages(block: LanguageDefinitionListBuilder.() -> Unit): LanguageDefinitionListBuilder {
    return LanguageDefinitionListBuilder().also(block)
}

class LanguageDefinitionListBuilder {

    private val allBuilder = mutableListOf<LanguageDefinitionBuilder>()

    fun language(name: String, block: LanguageDefinitionBuilder.() -> Unit) {
        allBuilder.add(LanguageDefinitionBuilder(name).also(block))
    }

    fun build(): List<GrammarDefinition> =
        allBuilder.map {
            val grammarSource = IGrammarSource.fromInputStream(
                FileProviderRegistry.getInstance().tryGetInputStream(it.grammar),
                it.grammar, Charset.defaultCharset()
            )

            DefaultGrammarDefinition.withLanguageConfiguration(
                grammarSource,
                it.languageConfiguration,
                it.name,
                it.scopeName
            ).withEmbeddedLanguages(it.embeddedLanguages)

        }

}

class LanguageDefinitionBuilder(var name: String) {
    lateinit var grammar: String
    var scopeName: String? = null
    var languageConfiguration: String? = null

    var embeddedLanguages: MutableMap<String, String>? = null

    fun defaultScopeName(prefix: String = "source") {
        scopeName = "$prefix.$name"
    }

    fun embeddedLanguages(block: LanguageEmbeddedLanguagesDefinitionBuilder.() -> Unit) {
        embeddedLanguages = embeddedLanguages ?: mutableMapOf<String, String>()
        LanguageEmbeddedLanguagesDefinitionBuilder(checkNotNull(embeddedLanguages)).also(block)
    }

    fun embeddedLanguage(scopeName: String, languageName: String) {
        embeddedLanguages = embeddedLanguages ?: mutableMapOf<String, String>()
        embeddedLanguages?.put(scopeName, languageName)
    }
}

class LanguageEmbeddedLanguagesDefinitionBuilder(private val map: MutableMap<String, String>) {


    infix fun String.to(languageName: String) {
        map[this] = languageName
    }

}

