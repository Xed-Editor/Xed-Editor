package com.rk.xededitor.MainActivity.tabs.editor

import android.content.Context
import android.graphics.Typeface
import android.os.Environment
import android.text.InputType
import android.util.AttributeSet
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesData.getBoolean
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

@Suppress("NOTHING_TO_INLINE")
class KarbonEditor : CodeEditor {
    constructor(context: Context) : super(context)
    
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    
    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)
    
    init {
        val tabSize = PreferencesData.getString(PreferencesKeys.TAB_SIZE, "4").toInt()
        props.deleteMultiSpaces = tabSize
        tabWidth = tabSize
        props.deleteEmptyLineFast = false
        props.useICULibToSelectWords = true
        setPinLineNumber(getBoolean(PreferencesKeys.PIN_LINE_NUMBER, false))
        isLineNumberEnabled = getBoolean(PreferencesKeys.SHOW_LINE_NUMBERS, true)
        isCursorAnimationEnabled = getBoolean(PreferencesKeys.CURSOR_ANIMATION_ENABLED, true)
        isWordwrap = getBoolean(PreferencesKeys.WORD_WRAP_ENABLED, false)
        setTextSize(PreferencesData.getString(PreferencesKeys.TEXT_SIZE, "14").toFloat())
        getComponent(EditorAutoCompletion::class.java).isEnabled = true
        
        loadTypeFace(context)
    }
    
    
    suspend fun loadFile(file:File){
        withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream = FileInputStream(file)
                val content = ContentIO.createFrom(inputStream)
                inputStream.close()
                withContext(Dispatchers.Main) { setText(content) }
            } catch (e: Exception) {
                e.printStackTrace()
                rkUtils.toast(e.message)
            }
        }
    }
    
    private inline fun loadTypeFace(context: Context){
        File(Environment.getExternalStorageDirectory(), "karbon/font.ttf").let {
            typefaceText =
                if (getBoolean(PreferencesKeys.EDITOR_FONT, false) and it.exists()) {
                    Typeface.createFromFile(it)
                } else {
                    Typeface.createFromAsset(
                        context.assets,
                        "JetBrainsMono-Regular.ttf",
                    )
                }
        }
    }
    
    fun showSuggestions(yes: Boolean) {
        inputType = if (yes) {
            InputType.TYPE_TEXT_VARIATION_NORMAL
        } else {
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
    }
    
    inline fun isShowSuggestion(): Boolean {
        return inputType != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }
    
    suspend fun saveToFile(file:File){
        try {
            withContext(Dispatchers.IO){
                val content = withContext(Dispatchers.Main) { text }
                val outputStream = FileOutputStream(file, false)
                ContentIO.writeTo(content, outputStream, true)
            }
        }catch (e:Exception){
            withContext(Dispatchers.Main){
                rkUtils.toast(e.message)
            }
        }
        
    }
    private var isSearching: Boolean = false
    
    fun isSearching(): Boolean {
        return isSearching
    }
    
    fun setSearching(s: Boolean) {
        isSearching = s
    }
    
    
    
    
}