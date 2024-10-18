package com.rk.xededitor.MainActivity.editor.fragments.editor

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.rk.libcommons.CustomScope
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.editor.fragments.core.CoreFragment
import com.rk.xededitor.R
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.event.ContentChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Suppress("NOTHING_TO_INLINE")
class EditorFragment(val context: Context) : CoreFragment {
    
    @JvmField
    var file: File? = null
    var editor: KarbonEditor? = null
    val scope = CustomScope()
    var setupEditor: SetupEditor? = null
    
    
    override fun onCreate() {
        editor = KarbonEditor(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setupEditor = SetupEditor(editor!!, context)
        setupEditor?.ensureTextmateTheme(context)
    }
    
    override fun loadFile(xfile: File) {
        file = xfile
        scope.launch(Dispatchers.Default) {
            setupEditor?.setupLanguage(file!!.name)
            editor!!.loadFile(xfile)
            withContext(Dispatchers.Main){
                setChangeListener()
            }
        }
        
    }
    
    override fun getFile(): File? = file
    
    
    fun save(showToast: Boolean = true,isAutoSaver:Boolean = false) {
        if (editor == null){
            throw RuntimeException("editor is null")
        }
        if (isAutoSaver and (editor?.text?.isEmpty() == true)){
            return
        }
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
    
    override fun getView(): View?{
        return editor
    }
    
    override fun onDestroy() {
        scope.cancel()
        editor?.release()
    }
    
    
    inline fun undo() {
        editor?.undo()
        MainActivity.activityRef.get()?.let {
            it.menu.findItem(R.id.redo).isEnabled = editor?.canRedo() == true
            it.menu.findItem(R.id.undo).isEnabled = editor?.canUndo() == true
        }
    }
    
    inline fun redo() {
        editor?.redo()
        MainActivity.activityRef.get()?.let {
            it.menu.findItem(R.id.redo).isEnabled = editor?.canRedo() == true
            it.menu.findItem(R.id.undo).isEnabled = editor?.canUndo() == true
        }
    }
    
    private suspend inline fun updateUndoRedo() {
        withContext(Dispatchers.Main){
            MainActivity.activityRef.get()?.let {
                it.menu.findItem(R.id.redo).isEnabled = editor?.canRedo() == true
                it.menu.findItem(R.id.undo).isEnabled = editor?.canUndo() == true
            }
        }
    }
    
    companion object{
        val set = HashSet<String>()
    }
    
    private var t = 0
    private fun setChangeListener() {
        editor!!.subscribeAlways(ContentChangeEvent::class.java) {
            scope.launch {
                updateUndoRedo()
                t++
                println(t)
                
                
                try {
                    val fileName = file!!.name
                    fun addStar(){
                        val index = MainActivity.activityRef.get()!!.tabViewModel.fragmentFiles.indexOf(file)
                        val currentTitle = MainActivity.activityRef.get()!!.tabViewModel.fragmentTitles[index]
                        // Check if the title doesn't already contain a '*'
                        if (!currentTitle.endsWith("*")) {
                            MainActivity.activityRef.get()!!.tabViewModel.fragmentTitles[index] = "$currentTitle*"
                            
                            scope.launch(Dispatchers.Main) {
                                MainActivity.activityRef.get()!!.tabLayout.getTabAt(index)?.text =
                                    MainActivity.activityRef.get()!!.tabViewModel.fragmentTitles[index]
                            }
                        }
                    }
                    
                    if (set.contains(fileName)) {
                        addStar()
                    } else {
                        set.add(fileName)
                        addStar()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
            }
        }
    }
}