package com.rk.xededitor.MainActivity.editor

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.Settings.SettingsData.getBoolean
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class TabFragment : Fragment() {
  
  var file: File? = null
  var editor: CodeEditor? = null
  
  // see @MenuClickHandler.update()
  var setListener = false
  
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      val context = requireContext()
      arguments?.let {
        val filePath = it.getString(ARG_FILE_PATH)
        if (filePath != null) {
          file = File(filePath)
        }
      }
      editor = CodeEditor(context)

      editor!!.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      editor!!.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

      val setupEditor = SetupEditor(editor!!, context)
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
            //this throw error for some reason
            //editor!!.setText(getString(R.string.file_exist_not))
          }
        }
        launch(Dispatchers.Default) {
          setupEditor.setupLanguage(file!!.name)
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
        getComponent(EditorAutoCompletion::class.java).isEnabled = true
      }

    }catch (e:Exception){
      //this fragment is detached and should be garbage collected
      e.printStackTrace()
    }

    

    



  }
  
  fun save(showToast: Boolean = true) {
    lifecycleScope.launch(Dispatchers.IO) {
      if (file!!.exists().not() and showToast){
        withContext(Dispatchers.Main){
          rkUtils.toast(getString(R.string.file_exist_not))
        }
      }
      try {
        val content = withContext(Dispatchers.Main) {
          editor?.text
        }

        
        val outputStream = FileOutputStream(file, false)
        if (content != null) {
          ContentIO.writeTo(content, outputStream, true)
          if (showToast) {
            withContext(Dispatchers.Main) {
              rkUtils.toast(getString(R.string.file_saved))
            }
          }
        }
        
        
        try {
          MainActivity.activityRef.get()?.let { activity ->
            val index = activity.tabViewModel.fragmentFiles.indexOf(file)
            activity.tabViewModel.fragmentTitles.let {
              if (file!!.name != it[index]) {
                it[index] = file!!.name
                withContext(Dispatchers.Main) {
                  activity.tabLayout.getTabAt(index)?.text = file!!.name
                }
              }
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
          withContext(Dispatchers.Main) {
            rkUtils.toast(e.message)
          }
        }
        
      } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
          rkUtils.toast(e.message)
        }
        
        
      }
      
    }
    
    
  }
  
  fun undo() {
    editor?.undo()
    MainActivity.activityRef.get()?.let {
      it.menu.findItem(R.id.redo).isEnabled = editor?.canRedo() == true
      it.menu.findItem(R.id.undo).isEnabled = editor?.canUndo() == true
    }
  }
  
  fun redo() {
    editor?.redo()
    MainActivity.activityRef.get()?.let {
      it.menu.findItem(R.id.redo).isEnabled = editor?.canRedo() == true
      it.menu.findItem(R.id.undo).isEnabled = editor?.canUndo() == true
    }
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
