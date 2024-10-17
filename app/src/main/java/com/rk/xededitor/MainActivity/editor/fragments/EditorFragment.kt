package com.rk.xededitor.MainActivity.editor.fragments

import android.content.Context
import android.view.View
import com.rk.libcommons.CustomScope
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.editor.KarbonEditor
import com.rk.xededitor.MainActivity.editor.fragments.core.CoreFragment
import com.rk.xededitor.R
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorFragment(val context: Context) : CoreFragment {
    
    var file: File? = null
    var editor: KarbonEditor? = null
    val scope = CustomScope()
    var setupEditor: SetupEditor? = null
    
    override fun onCreate() {
        editor = KarbonEditor(context)
        setupEditor = SetupEditor(editor!!, context)
        setupEditor?.ensureTextmateTheme(context)
    }
    
    override fun loadFile(xfile: File) {
        file = xfile
        scope.launch {
            delay(1000)
            setupEditor?.setupLanguage(file!!.name)
            editor!!.loadFile(xfile)
        }
    }
    
    
    fun save(showToast: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            editor?.saveToFile(file!!)
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
                withContext(Dispatchers.Main) { rkUtils.toast(e.message) }
            }
            if (showToast) {
                withContext(Dispatchers.Main) { rkUtils.toast(rkUtils.getString(R.string.saved)) }
            }
        }
    }
    
    override fun getView(): View? = editor
    
    override fun onDestroy() {
        scope.cancel()
        editor?.release()
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
}