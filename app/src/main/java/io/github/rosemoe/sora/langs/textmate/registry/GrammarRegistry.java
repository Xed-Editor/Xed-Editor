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
package io.github.rosemoe.sora.langs.textmate.registry;

import android.util.Pair;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IThemeSource;
import org.eclipse.tm4e.core.registry.Registry;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.dsl.LanguageDefinitionListBuilder;
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver;
import io.github.rosemoe.sora.langs.textmate.registry.reader.LanguageDefinitionReader;

public class GrammarRegistry {

    private static GrammarRegistry instance;

    private Registry registry = new Registry();

    private GrammarRegistry parent;

    private final Map</* scopeName */String, LanguageConfiguration> languageConfigurationMap = new LinkedHashMap<>();

    private final Map<String/* */, Integer> scopeName2GrammarId = new LinkedHashMap<>();

    private final Map</* name */String, String /* scopeName */> grammarFileName2ScopeName = new LinkedHashMap<>();

    private final Map<String, GrammarDefinition> scopeName2GrammarDefinition = new LinkedHashMap<>();

    public synchronized static GrammarRegistry getInstance() {
        if (instance == null) {
            instance = new GrammarRegistry();
            instance.initThemeListener();
        }
        return instance;
    }

    private GrammarRegistry() {
    }

    public GrammarRegistry(GrammarRegistry parent) {
        this.parent = parent;
    }

    private void initThemeListener() {
        var themeRegistry = ThemeRegistry.getInstance();

        ThemeRegistry.ThemeChangeListener themeChangeListener = newTheme -> {
            try {
                setTheme(newTheme);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        if (!themeRegistry.hasListener(themeChangeListener)) {
            themeRegistry.addListener(themeChangeListener);
        }
    }


    @Nullable
    public IGrammar findGrammar(String scopeName) {
        return findGrammar(scopeName, true);
    }

    public IGrammar findGrammar(String scopeName, boolean findInParent) {

        var grammar = registry.grammarForScopeName(scopeName);

        if (grammar != null) {
            return grammar;
        }

        if (!findInParent) {
            return null;
        }

        if (parent == null) {
            return null;
        }


        return parent.findGrammar(scopeName, true);
    }


    /**
     * Adapted to use streams to read and load language configuration files by yourself {@link TextMateLanguage#create(IGrammarSource, Reader, IThemeSource)}.
     *
     * @param languageConfiguration loaded language configuration
     * @param grammar               Binding to grammar
     * @deprecated The grammar file and language configuration file should in most cases be on local file, use {@link GrammarDefinition#getLanguageConfiguration()} and {@link FileResolver} to read the language configuration file
     */
    @Deprecated
    public synchronized void languageConfigurationToGrammar(LanguageConfiguration languageConfiguration, IGrammar grammar) {
        languageConfigurationMap.put(grammar.getScopeName(), languageConfiguration);
    }

    @Nullable
    public LanguageConfiguration findLanguageConfiguration(String scopeName) {
        return findLanguageConfiguration(scopeName, true);
    }

    @Nullable
    public LanguageConfiguration findLanguageConfiguration(String scopeName, boolean findInParent) {
        var languageConfiguration = languageConfigurationMap.get(scopeName);

        if (languageConfiguration != null) {
            return languageConfiguration;
        }

        if (!findInParent) {
            return null;
        }

        if (parent == null) {
            return null;
        }

        return parent.findLanguageConfiguration(scopeName, true);
    }


    public Pair<IGrammar, LanguageConfiguration> loadLanguageAndLanguageConfiguration(GrammarDefinition grammarDefinition) {
        var grammar = loadGrammar(grammarDefinition);

        var languageConfiguration = findLanguageConfiguration(grammar.getScopeName(), false);

        return Pair.create(grammar, languageConfiguration);
    }

    public List<IGrammar> loadGrammars(LanguageDefinitionListBuilder builder) {
        return loadGrammars(builder.build());
    }

    public List<IGrammar> loadGrammars(List<GrammarDefinition> list) {
        prepareLoadGrammars(list);
        return list.stream().map(this::loadGrammar).collect(Collectors.toList());
    }

    public List<IGrammar> loadGrammars(String jsonPath) {
        return loadGrammars(LanguageDefinitionReader.read(jsonPath));
    }

    public synchronized IGrammar loadGrammar(GrammarDefinition grammarDefinition) {
        var languageName = grammarDefinition.getName();

        if (grammarFileName2ScopeName.containsKey(languageName) && grammarDefinition.getScopeName() != null) {
            //loaded
            return registry.grammarForScopeName(grammarDefinition.getScopeName());
        }


        var grammar = doLoadGrammar(grammarDefinition);

        if (grammarDefinition.getScopeName() != null) {
            grammarFileName2ScopeName.put(languageName, grammarDefinition.getScopeName());
            scopeName2GrammarDefinition.put(grammar.getScopeName(), grammarDefinition);
        }

        return grammar;

    }


    private synchronized IGrammar doLoadGrammar(GrammarDefinition grammarDefinition) {

        var languageConfigurationPath = grammarDefinition.getLanguageConfiguration();

        if (languageConfigurationPath != null) {

            var languageConfigurationStream = FileProviderRegistry.getInstance()
                    .tryGetInputStream(languageConfigurationPath);

            if (languageConfigurationStream != null) {

                var languageConfiguration = LanguageConfiguration.load(
                        new InputStreamReader(languageConfigurationStream)
                );

                languageConfigurationMap.put(grammarDefinition.getScopeName(), languageConfiguration);

            }
        }

        IGrammar grammar;

        if (!grammarDefinition.getEmbeddedLanguages().isEmpty()) {
            grammar = registry.addGrammar(grammarDefinition.getGrammar());
        } else {
            grammar = registry.addGrammar(
                    grammarDefinition.getGrammar(),
                    null,
                    getOrPullGrammarId(grammarDefinition.getScopeName()),
                    findGrammarIds(grammarDefinition.getEmbeddedLanguages())
            );
        }

        if (grammarDefinition.getScopeName() != null && !grammar.getScopeName().equals(grammarDefinition.getScopeName())) {
            throw new IllegalStateException(
                    String.format("The scope name loaded by the grammar file does not match the declared scope name, it should be %s instead of %s",
                            grammar.getScopeName(), grammarDefinition.getScopeName()));
        }

        return grammar;

    }


    private void prepareLoadGrammars(List<GrammarDefinition> grammarDefinitions) {
        for (var grammar : grammarDefinitions) {
            getOrPullGrammarId(grammar.getScopeName());
        }
    }

    public synchronized void setTheme(ThemeModel themeModel) throws Exception {
        if (!themeModel.isLoaded()) {
            themeModel.load(registry.getColorMap());
        }
        registry.setTheme(themeModel.getTheme());
    }


    private synchronized int getOrPullGrammarId(String scopeName) {
        var id = scopeName2GrammarId.get(scopeName);

        if (id == null) {
            id = scopeName2GrammarId.size() + 2;
        }

        scopeName2GrammarId.put(scopeName, id);

        return id;
    }


    private synchronized Map<String, Integer> findGrammarIds(Map<String, String> scopeName2LanguageName) {
        var result = new HashMap<String, Integer>();
        for (var entry : scopeName2LanguageName.entrySet()) {
            // scopeName (entry#getKey)
            result.put(entry.getKey(), getOrPullGrammarId(
                    getGrammarScopeName(entry.getValue())));
        }
        return result;
    }

    private String getGrammarScopeName(String name) {
        if (scopeName2GrammarDefinition.containsKey(name)) {
            return name;
        }
        var grammarName = grammarFileName2ScopeName.get(name);
        return grammarName == null ? name : grammarName;
    }

    public synchronized void dispose(boolean closeParent) {

        if (registry == null) {
            return;
        }

        registry = null;
        grammarFileName2ScopeName.clear();
        languageConfigurationMap.clear();
        scopeName2GrammarId.clear();
        scopeName2GrammarDefinition.clear();

        // if (parent == null) {
        // ? need?
        //FileProviderRegistry.getInstance().dispose();
        // }

        if (parent != null && closeParent) {
            parent.dispose(true);
        }


    }

    public void dispose() {
        dispose(false);
    }


}
