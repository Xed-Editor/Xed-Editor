package com.rk.libcommons

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Environment
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesData.isDarkMode
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import com.google.gson.JsonParser
import com.rk.settings.PreferencesData.getBoolean
import com.rk.settings.PreferencesKeys
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader

class SetupEditor(val editor: KarbonEditor, private val ctx: Context, scope:CoroutineScope) {
    
    init {
        scope.launch { ensureTextmateTheme(ctx) }
        with(editor) {
            val tabSize = PreferencesData.getString(PreferencesKeys.TAB_SIZE, "4").toInt()
            props.deleteMultiSpaces = tabSize
            tabWidth = tabSize
            props.deleteEmptyLineFast = false
            props.useICULibToSelectWords = true
            setPinLineNumber(getBoolean(PreferencesKeys.PIN_LINE_NUMBER, false))
            isLineNumberEnabled = getBoolean(PreferencesKeys.SHOW_LINE_NUMBERS, true)
            isCursorAnimationEnabled = getBoolean(PreferencesKeys.CURSOR_ANIMATION_ENABLED, true)
            setTextSize(PreferencesData.getString(PreferencesKeys.TEXT_SIZE, "14").toFloat())
            getComponent(EditorAutoCompletion::class.java).isEnabled = true
            setWordwrap(getBoolean(PreferencesKeys.WORD_WRAP_ENABLED, false), getBoolean(PreferencesKeys.ANTI_WORD_BREAKING, true))
            showSuggestions(getBoolean(PreferencesKeys.SHOW_SUGGESTIONS, false))
        }
        File(Environment.getExternalStorageDirectory(), "karbon/font.ttf").let {
            editor.typefaceText =
                if (getBoolean(PreferencesKeys.EDITOR_FONT, false) and it.exists()) {
                    Typeface.createFromFile(it)
                } else {
                    Typeface.createFromAsset(ctx.assets, "JetBrainsMono-Regular.ttf")
                }
        }
    }

    suspend fun setupLanguage(fileName: String) {
        when (fileName.substringAfterLast('.', "")) {
            "java",
            "bsh" -> setLanguage("source.java")
            "html" -> setLanguage("text.html.basic")
            "kt",
            "kts" -> setLanguage("source.kotlin")
            "py" -> setLanguage("source.python")
            "xml" -> setLanguage("text.xml")
            "js" -> setLanguage("source.js")
            "md" -> setLanguage("text.html.markdown")
            "c" -> setLanguage("source.c")
            "cpp",
            "h" -> setLanguage("source.cpp")
            "json" -> setLanguage("source.json")
            "css" -> setLanguage("source.css")
            "cs" -> setLanguage("source.cs")
            "yml",
            "eyaml",
            "eyml",
            "yaml",
            "cff" -> setLanguage("source.yaml")
            "sh",
            "bash" -> setLanguage("source.shell")
            "rs" -> setLanguage("source.rust")
            "lua" -> setLanguage("source.lua")
            "php" -> setLanguage("source.php")
        }
    }

    companion object {
        private var isInit = false
        private var darkThemeRegistry: ThemeRegistry? = null
        private var oledThemeRegistry: ThemeRegistry? = null
        private var lightThemeRegistry: ThemeRegistry? = null

        suspend fun init(context: Context) {
            if (!isInit) {
                withContext(Dispatchers.IO){
                    initGrammarRegistry(context)
                    initTextMateTheme(context)
                }
                isInit = true
            }
        }

        private suspend fun initGrammarRegistry(context: Context) {
            FileProviderRegistry.getInstance()
                .addFileProvider(AssetsFileResolver(context.applicationContext?.assets))
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        }

        private suspend fun initTextMateTheme(context: Context) {
            darkThemeRegistry = ThemeRegistry()
            oledThemeRegistry = ThemeRegistry()
            lightThemeRegistry = ThemeRegistry()

            val darcula = context.assets.open("textmate/darcula.json")
            val darcula_oled = context.assets.open("textmate/black/darcula.json")
            val quietlight = context.assets.open("textmate/quietlight.json")

            try {
                darkThemeRegistry?.loadTheme(
                    ThemeModel(IThemeSource.fromInputStream(darcula, "darcula.json", null))
                )
                oledThemeRegistry?.loadTheme(
                    ThemeModel(IThemeSource.fromInputStream(darcula_oled, "darcula.json", null))
                )
                lightThemeRegistry?.loadTheme(
                    ThemeModel(IThemeSource.fromInputStream(quietlight, "quietlight.json", null))
                )
            } catch (e: Exception) {
                throw RuntimeException(e)
            } finally {
                try {
                    darcula.close()
                    darcula_oled.close()
                    quietlight.close()
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        }
    }

    private suspend fun setLanguage(languageScopeName: String) {
        val language =
            TextMateLanguage.create(languageScopeName, true /* true for enabling auto-completion */)
        val kw = ctx.assets.open("textmate/keywords.json")
        val reader = InputStreamReader(kw)
        val jsonElement = JsonParser.parseReader(reader)
        val keywordsArray = jsonElement.asJsonObject.getAsJsonArray(languageScopeName)
        if (keywordsArray != null){
            val keywords = Array(keywordsArray.size()) { "" }
            for (i in keywords.indices) {
                keywords[i] = keywordsArray[i].asString
            }
            language.setCompleterKeywords(keywords)
        }
        
        editor.setEditorLanguage(language as Language)
    }
    

    suspend fun ensureTextmateTheme(context: Context) {
        init(context)
        val themeRegistry =
            when {
                isDarkMode(ctx) && PreferencesData.isOled() -> oledThemeRegistry
                isDarkMode(ctx) -> darkThemeRegistry
                else -> lightThemeRegistry
            }

        themeRegistry?.let {
            val editorColorScheme: EditorColorScheme = TextMateColorScheme.create(it)
            if (isDarkMode(ctx) && PreferencesData.isOled()) {
                editorColorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.BLACK)
            }
            withContext(Dispatchers.Main){
                editor.colorScheme = editorColorScheme
            }
            
        }
    }
}
