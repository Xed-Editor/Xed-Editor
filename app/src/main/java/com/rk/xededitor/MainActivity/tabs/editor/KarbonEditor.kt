package com.rk.xededitor.MainActivity.tabs.editor

import android.content.Context
import android.graphics.Typeface
import android.os.Environment
import android.text.InputType
import android.util.AttributeSet
import com.rk.libcommons.CustomScope
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesData.getBoolean
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.DirectAccessProps
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    
    val scope = CustomScope()
    val setupEditor = SetupEditor(this, context,scope)
    
    suspend fun loadFile(file:File){
        withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream = FileInputStream(file)
                val content = ContentIO.createFrom(inputStream)
                inputStream.close()
                withContext(Dispatchers.Main) { setText(content) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main){
                    Toast.makeText(context,e.message,Toast.LENGTH_LONG).show()
                }
               
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
