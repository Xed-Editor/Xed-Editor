package com.rk.xededitor.MainActivity.editor

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rk.libcommons.After
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.StaticData
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.Settings.SettingsData.getBoolean
import com.rk.xededitor.Settings.SettingsData.getString
import com.rk.xededitor.rkUtils.runOnUiThread
import com.rk.xededitor.SetupEditor
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class DynamicFragment : Fragment {
    lateinit var fileName: String
    var file: File? = null
    private var ctx: Context = BaseActivity.getActivity(MainActivity::class.java)!!
    lateinit var editor: CodeEditor
    var content: Content? = null
    var isModified: Boolean = false
    var isSearching = false

    //this is used in onOnCreateView to prevent crash
    private var editorx: CodeEditor? = null
    


    //this constructor is called when theme change, it will remove itself
    constructor() {
        After(100) {
            runOnUiThread {
                val fragmentManager =
                    BaseActivity.getActivity(MainActivity::class.java)?.supportFragmentManager
                val fragmentTransaction = fragmentManager?.beginTransaction()
                fragmentTransaction?.remove(this)
                fragmentTransaction?.commitNowAllowingStateLoss()
            }
        }
    }

    constructor(file: File) {
        this.file = file
        this.fileName = file.name
        editor = CodeEditor(ctx)
        editorx = editor

        val setupEditor = SetupEditor(editor, ctx)
        val isDarkMode = SettingsData.isDarkMode(ctx)

        if (isDarkMode) {
            setupEditor.ensureTextmateTheme()
        }

        lifecycleScope.launch(Dispatchers.Default){
            launch(Dispatchers.IO){
                try {
                    val inputStream: InputStream = FileInputStream(file)
                    content = ContentIO.createFrom(inputStream)
                    inputStream.close()
                    withContext(Dispatchers.Main){
                        editor.setText(content)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            launch(Dispatchers.Default){
                if (isDarkMode.not()){
                    setupEditor.ensureTextmateTheme()
                }
            }
            launch(Dispatchers.Default){
                setupEditor.setupLanguage(fileName)
            }
}

            with(editor){
                val tabSize = getString(Keys.TAB_SIZE, "4").toInt()
                props.deleteMultiSpaces = tabSize
                tabWidth = tabSize
                props.deleteEmptyLineFast = false
                props.useICULibToSelectWords = true
                setPinLineNumber(getBoolean(Keys.PIN_LINE_NUMBER,false))
                isCursorAnimationEnabled = getBoolean(Keys.CURSOR_ANIMATION_ENABLED, true)
                isWordwrap = getBoolean(Keys.WORD_WRAP_ENABLED,false)
                typefaceText = Typeface.createFromAsset(ctx.assets, "JetBrainsMono-Regular.ttf")
                setTextSize(getString(Keys.TEXT_SIZE, "14").toFloat())
            }
        
        setListener()
    }

    private fun setListener() {
        editor.subscribeAlways(
            ContentChangeEvent::class.java
        ) {
            updateUndoRedo()
            val tab = StaticData.mTabLayout.getTabAt(StaticData.mTabLayout.selectedTabPosition)
            if (isModified) {
                tab!!.setText("$fileName*")
            }
            isModified = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return editorx
    }

    fun updateUndoRedo() {
        if (StaticData.menu == null && !isSearching) {
            return
        }
        StaticData.menu.findItem(R.id.redo)?.setEnabled(editor.canRedo())
        StaticData.menu.findItem(R.id.undo)?.setEnabled(editor.canUndo())
    }

    fun releaseEditor() {
        editor.release()
        content = null
    }

    fun Undo() {
        if (editor.canUndo()) {
            editor.undo()
        }
    }

    fun Redo() {
        if (editor.canRedo()) {
            editor.redo()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        //fragments get removed by the tab adapter but just to be safe
        After(10000){
            if (StaticData.fragments.contains(this)){
                StaticData.fragments.remove(this)
            }
        }

    }
}