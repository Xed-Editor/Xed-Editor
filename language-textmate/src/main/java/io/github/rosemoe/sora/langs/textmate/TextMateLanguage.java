/*
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
 */
package io.github.rosemoe.sora.langs.textmate;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IThemeSource;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;

import java.io.Reader;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionHelper;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.utils.StringUtils;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.util.MyCharacter;

public class TextMateLanguage extends EmptyLanguage {

    private int tabSize = 4;

    private boolean useTab = false;

    private final IdentifierAutoComplete autoComplete = new IdentifierAutoComplete();
    boolean autoCompleteEnabled;
    final boolean createIdentifiers;

    TextMateAnalyzer textMateAnalyzer;

    GrammarRegistry grammarRegistry;

    ThemeRegistry themeRegistry;

    LanguageConfiguration languageConfiguration;

    TextMateNewlineHandler[] newlineHandlers;

    TextMateSymbolPairMatch symbolPairMatch;

    private TextMateNewlineHandler newlineHandler;

    protected TextMateLanguage(IGrammar grammar,
                               LanguageConfiguration languageConfiguration,
                               GrammarRegistry grammarRegistry,
                               ThemeRegistry themeRegistry,
                               boolean createIdentifiers) {

        this.grammarRegistry = grammarRegistry;
        this.themeRegistry = themeRegistry;
        // this.grammar = grammar;

        autoCompleteEnabled = true;

        this.createIdentifiers = createIdentifiers;

        symbolPairMatch = new TextMateSymbolPairMatch(this);

        createAnalyzerAndNewlineHandler(grammar, languageConfiguration);
    }


    @Deprecated
    public static IGrammar prepareLoad(IGrammarSource grammarSource, @Nullable Reader languageConfiguration, IThemeSource themeSource) {
        var definition = DefaultGrammarDefinition.withGrammarSource(grammarSource, StringUtils.getFileNameWithoutExtension(grammarSource.getFilePath()), null);
        var languageRegistry = GrammarRegistry.getInstance();
        var grammar = languageRegistry.loadGrammar(definition);
        if (languageConfiguration != null) {
            languageRegistry.languageConfigurationToGrammar(LanguageConfiguration.load(languageConfiguration), grammar);
        }
        var themeRegistry = ThemeRegistry.getInstance();
        try {
            themeRegistry.loadTheme(themeSource);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return grammar;
    }

    @Deprecated
    public static TextMateLanguage create(IGrammarSource grammarSource, Reader languageConfiguration, IThemeSource themeSource) {
        var grammar = prepareLoad(grammarSource, languageConfiguration, themeSource);
        return create(grammar.getScopeName(), true);
    }

    @Deprecated
    public static TextMateLanguage create(IGrammarSource grammarSource, IThemeSource themeSource) {
        var grammar = prepareLoad(grammarSource, null, themeSource);
        return create(grammar.getScopeName(), true);
    }

    @Deprecated
    public static TextMateLanguage createNoCompletion(IGrammarSource grammarSource, Reader languageConfiguration, IThemeSource themeSource) {
        var grammar = prepareLoad(grammarSource, languageConfiguration, themeSource);
        return create(grammar.getScopeName(), false);
    }

    @Deprecated
    public static TextMateLanguage createNoCompletion(IGrammarSource grammarSource, IThemeSource themeSource) {
        var grammar = prepareLoad(grammarSource, null, themeSource);
        return create(grammar.getScopeName(), false);
    }

    public static TextMateLanguage create(String languageScopeName, boolean autoCompleteEnabled) {
        return create(languageScopeName, GrammarRegistry.getInstance(), autoCompleteEnabled);
    }

    public static TextMateLanguage create(String languageScopeName, GrammarRegistry grammarRegistry, boolean autoCompleteEnabled) {
        return create(languageScopeName, grammarRegistry, ThemeRegistry.getInstance(), autoCompleteEnabled);
    }

    public static TextMateLanguage create(String languageScopeName, GrammarRegistry grammarRegistry, ThemeRegistry themeRegistry, boolean autoCompleteEnabled) {
        var grammar = grammarRegistry.findGrammar(languageScopeName);

        if (grammar == null) {
            throw new IllegalArgumentException(String.format("Language with %s scope name not found", grammarRegistry));
        }

        var languageConfiguration = grammarRegistry.findLanguageConfiguration(grammar.getScopeName());


        return new TextMateLanguage(grammar, languageConfiguration, grammarRegistry, themeRegistry, autoCompleteEnabled);
    }


    public static TextMateLanguage create(GrammarDefinition grammarDefinition, boolean autoCompleteEnabled) {
        return create(grammarDefinition, GrammarRegistry.getInstance(), autoCompleteEnabled);
    }

    public static TextMateLanguage create(GrammarDefinition grammarDefinition, GrammarRegistry grammarRegistry, boolean autoCompleteEnabled) {
        return create(grammarDefinition, grammarRegistry, ThemeRegistry.getInstance(), autoCompleteEnabled);
    }

    public static TextMateLanguage create(GrammarDefinition grammarDefinition, GrammarRegistry grammarRegistry, ThemeRegistry themeRegistry, boolean autoCompleteEnabled) {
        var grammar = grammarRegistry.loadGrammar(grammarDefinition);

        if (grammar == null) {
            throw new IllegalArgumentException(String.format("Language with %s scope name not found", grammarRegistry));
        }

        var languageConfiguration = grammarRegistry.findLanguageConfiguration(grammar.getScopeName());

        return new TextMateLanguage(grammar, languageConfiguration, grammarRegistry, themeRegistry, autoCompleteEnabled);
    }


    /**
     * When you update the {@link TextMateColorScheme} for editor, you need to synchronize the updates here
     *
     * @param theme IThemeSource creates from file
     * @deprecated Use {@link ThemeRegistry#setTheme(String)}
     */
    @WorkerThread
    @Deprecated
    public void updateTheme(IThemeSource theme) throws Exception {
        //if (textMateAnalyzer != null) {
        //  textMateAnalyzer.updateTheme(theme);
        //}
        themeRegistry.loadTheme(theme);
    }


    private void createAnalyzerAndNewlineHandler(IGrammar grammar, LanguageConfiguration languageConfiguration) {
        var old = textMateAnalyzer;
        if (old != null) {
            old.setReceiver(null);
            old.destroy();
        }
        try {
            textMateAnalyzer = new TextMateAnalyzer(this, grammar, languageConfiguration, /*grammarRegistry,*/ themeRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.languageConfiguration = languageConfiguration;
        newlineHandler = new TextMateNewlineHandler(this);
        newlineHandlers = new TextMateNewlineHandler[]{newlineHandler};
        if (languageConfiguration != null) {
            // because the editor will only get the symbol pair matcher once
            // (caching object to stop repeated new object created),
            // the symbol pair needs to be updated inside the symbol pair matcher.
            symbolPairMatch.updatePair();
        }

    }

    public void updateLanguage(String scopeName) {
        var grammar = grammarRegistry.findGrammar(scopeName);
        var languageConfiguration = grammarRegistry.findLanguageConfiguration(grammar.getScopeName());
        createAnalyzerAndNewlineHandler(grammar, languageConfiguration);
    }

    public void updateLanguage(GrammarDefinition grammarDefinition) {
        var grammar = grammarRegistry.loadGrammar(grammarDefinition);

        var languageConfiguration = grammarRegistry.findLanguageConfiguration(grammar.getScopeName());

        createAnalyzerAndNewlineHandler(grammar, languageConfiguration);
    }

    @NonNull
    @Override
    public AnalyzeManager getAnalyzeManager() {
        if (textMateAnalyzer == null) {
            return EmptyAnalyzeManager.INSTANCE;
        }
        return textMateAnalyzer;
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    /**
     * Set tab size. The tab size is used to compute code blocks.
     */
    public void setTabSize(int tabSize) {
        this.tabSize = tabSize;
    }

    public int getTabSize() {
        return tabSize;
    }


    @Override
    public boolean useTab() {
        return useTab;
    }

    public void useTab(boolean useTab) {
        this.useTab = useTab;
    }

    public TextMateNewlineHandler getNewlineHandler() {
        return newlineHandler;
    }

    public LanguageConfiguration getLanguageConfiguration() {
        return languageConfiguration;
    }

    @Override
    public TextMateSymbolPairMatch getSymbolPairs() {
        return symbolPairMatch;
    }

    @Override
    public NewlineHandler[] getNewlineHandlers() {
        return newlineHandlers;
    }

    public boolean isAutoCompleteEnabled() {
        return autoCompleteEnabled;
    }

    public void setAutoCompleteEnabled(boolean autoCompleteEnabled) {
        this.autoCompleteEnabled = autoCompleteEnabled;
    }

    @Override
    public void requireAutoComplete(@NonNull ContentReference content, @NonNull CharPosition position, @NonNull CompletionPublisher publisher, @NonNull Bundle extraArguments) {
        if (!autoCompleteEnabled) {
            return;
        }
        var prefix = CompletionHelper.computePrefix(content, position, MyCharacter::isJavaIdentifierPart);
        final var idt = textMateAnalyzer.syncIdentifiers;
        autoComplete.requireAutoComplete(content, position, prefix, publisher, idt);
    }

    public IdentifierAutoComplete getAutoCompleter() {
        return autoComplete;
    }

    public void setCompleterKeywords(String[] keywords) {
        autoComplete.setKeywords(keywords, false);
    }
}
