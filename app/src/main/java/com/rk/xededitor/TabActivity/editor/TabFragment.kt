package com.rk.xededitor.TabActivity.editor

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.Settings.SettingsData.getBoolean
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class TabFragment : Fragment() {

    lateinit var file: File
    var editor: CodeEditor? = null

    // see @MenuClickHandler.update()
    var setListener = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val filePath = it.getString(ARG_FILE_PATH)
            if (filePath != null) {
                file = File(filePath)
            }
        }
        
        val context = requireContext()

        editor = CodeEditor(context)

        val setupEditor = SetupEditor(editor!!,context)
        setupEditor.ensureTextmateTheme(context)
        
        lifecycleScope.launch(Dispatchers.Default) {
            launch(Dispatchers.IO) {
                try {
                    val inputStream: InputStream = FileInputStream(file)
                    val content = ContentIO.createFrom(inputStream)
                    inputStream.close()
                    withContext(Dispatchers.Main) {
                        editor!!.setText(content)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    editor!!.setText("file not found")
                }
            }
            launch(Dispatchers.Default) {
                setupEditor.setupLanguage(file.name)
            }
        }

        with(editor!!) {
            val tabSize = SettingsData.getString(Keys.TAB_SIZE, "4").toInt()
            props.deleteMultiSpaces = tabSize
            tabWidth = tabSize
            props.deleteEmptyLineFast = false
            props.useICULibToSelectWords = true
            setPinLineNumber(getBoolean(Keys.PIN_LINE_NUMBER, false))
            isLineNumberEnabled = getBoolean(Keys.SHOW_LINE_NUMBERS, true)
            isCursorAnimationEnabled = getBoolean(Keys.CURSOR_ANIMATION_ENABLED, true)
            isWordwrap = getBoolean(Keys.WORD_WRAP_ENABLED, false)
            typefaceText =
                Typeface.createFromAsset(requireContext().assets, "JetBrainsMono-Regular.ttf")
            setTextSize(SettingsData.getString(Keys.TEXT_SIZE, "14").toFloat())
        }
    }

    fun save(showToast: Boolean = true) {
        if (file.exists().not()) {
            rkUtils.runOnUiThread {
                Toast.makeText(context, "File no longer exists", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = withContext(Dispatchers.Main) {
                    editor?.text
                }

                val outputStream = FileOutputStream(file, false)
                if (content != null) {
                    ContentIO.writeTo(content, outputStream, true)
                    if (showToast) {
                        withContext(Dispatchers.Main) {
                            rkUtils.toast(context, "saved")
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    rkUtils.toast(context, e.message)
                }


            }

        }


    }

    fun undo() {
        editor?.undo()
    }

    fun redo() {
        editor?.redo()
    }

    private var isSearching: Boolean = false
    fun isSearching(): Boolean {
        return isSearching
    }

    fun setSearching(s: Boolean) {
        isSearching = s
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return editor
    }


    companion object {
        private const val ARG_FILE_PATH = "file_path"

        fun newInstance(file: File): TabFragment {
            val fragment = TabFragment()
            val args = Bundle().apply {
                putString(ARG_FILE_PATH, file.absolutePath)
            }
            fragment.arguments = args
            return fragment
        }


    }


}
