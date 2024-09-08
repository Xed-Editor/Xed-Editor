package com.rk.xededitor.MainActivity.tab

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.Settings.SettingsData.getBoolean
import com.rk.xededitor.SetupEditor
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class TabFragment : Fragment() {

    private lateinit var file: File
    var editor:CodeEditor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val filePath = it.getString(ARG_FILE_PATH)
            if (filePath != null) {
                file = File(filePath)
            }
        }

        editor = CodeEditor(requireContext())

        val setupEditor = SetupEditor(editor!!, requireContext())
        val isDarkMode = SettingsData.isDarkMode(requireContext())

        if (isDarkMode) {
            setupEditor.ensureTextmateTheme()
        }

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
                if (isDarkMode.not()) {
                    setupEditor.ensureTextmateTheme()
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
            typefaceText = Typeface.createFromAsset(requireContext().assets, "JetBrainsMono-Regular.ttf")
            setTextSize(SettingsData.getString(Keys.TEXT_SIZE, "14").toFloat())
        }





    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return editor
    }

   // override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
     //   super.onViewCreated(view, savedInstanceState)
      //  val textView: TextView = view.findViewById(R.id.textView)
//        readFileContent { content ->
//            textView.text = content
//        }
 //   }


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
