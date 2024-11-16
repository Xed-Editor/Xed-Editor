package com.rk.xededitor.MainActivity.tabs.editor

import android.content.Context
import android.util.Pair
import android.view.KeyEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.rk.libcommons.CustomScope
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.R
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.SymbolInputView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private typealias onClick = OnClickListener

@Suppress("NOTHING_TO_INLINE")
class EditorFragment(val context: Context) : CoreFragment {
    
    @JvmField
    var file: File? = null
    var editor: KarbonEditor? = null
    val scope = CustomScope()
    var constraintLayout: ConstraintLayout? = null
    private lateinit var horizontalScrollView: HorizontalScrollView
    private lateinit var searchLayout:LinearLayout
    
    
    fun showArrowKeys(yes: Boolean) {
        horizontalScrollView.visibility = if (yes) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
    
    fun showSearch(yes: Boolean){
        searchLayout.visibility = if (yes) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
    
    override fun onCreate() {
        constraintLayout = ConstraintLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        horizontalScrollView = HorizontalScrollView(context).apply {
            id = View.generateViewId()
            visibility = if (PreferencesData.getBoolean(PreferencesKeys.SHOW_ARROW_KEYS, true)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
            addView(getInputView())
        }
        
        editor = KarbonEditor(context).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,0
            )
        }
        
        
        
        
        // Define the new LinearLayout
        searchLayout = SearchPanel(constraintLayout!!,editor!!).view
        
        
        // Add the views to the constraint layout
        constraintLayout!!.addView(searchLayout)
        constraintLayout!!.addView(editor)
        constraintLayout!!.addView(horizontalScrollView)
        
        // Set up constraints for the layout
        ConstraintSet().apply {
            clone(constraintLayout)
            
            // Position the LinearLayout at the top of the screen
            connect(searchLayout.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            
            // Position the editor below the LinearLayout
            connect(editor!!.id, ConstraintSet.TOP, searchLayout.id, ConstraintSet.BOTTOM)
            connect(editor!!.id, ConstraintSet.BOTTOM, horizontalScrollView!!.id, ConstraintSet.TOP)
            
            // Position the HorizontalScrollView at the bottom
            connect(horizontalScrollView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            connect(horizontalScrollView.id, ConstraintSet.TOP, editor!!.id, ConstraintSet.BOTTOM)
            
            applyTo(constraintLayout)
        }
    }
    
    
    override fun loadFile(xfile: File) {
        file = xfile
        scope.launch(Dispatchers.Default) {
            launch { editor!!.loadFile(xfile) }
            launch { editor!!.setupEditor.setupLanguage(file!!.name) }
            withContext(Dispatchers.Main) {
                setChangeListener()
                file?.let {
                    if (it.name.endsWith(".txt") && PreferencesData.getBoolean(PreferencesKeys.WORD_WRAP_TXT, false)) {
                        editor?.isWordwrap = true
                    }
                }
            }
        }
        
    }
    
    override fun getFile(): File? = file
    
    
    fun save(showToast: Boolean = true, isAutoSaver: Boolean = false) {
        if (editor == null) {
            throw RuntimeException("editor is null")
        }
        if (isAutoSaver and (editor?.text?.isEmpty() == true)) {
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
                                activity.tabLayout!!.getTabAt(index)?.text = file!!.name
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
    
    override fun getView(): View? {
        return constraintLayout
    }
    
    override fun onDestroy() {
        scope.cancel()
        editor?.scope?.cancel()
        editor?.release()
        
    }
    
    override fun onClosed() {
        onDestroy()
    }
    
    
    inline fun undo() {
        editor?.undo()
        MainActivity.activityRef.get()?.let {
            it.menu!!.findItem(R.id.redo).isEnabled = editor?.canRedo() == true
            it.menu!!.findItem(R.id.undo).isEnabled = editor?.canUndo() == true
        }
    }
    
    inline fun redo() {
        editor?.redo()
        MainActivity.activityRef.get()?.let {
            it.menu!!.findItem(R.id.redo).isEnabled = editor?.canRedo() == true
            it.menu!!.findItem(R.id.undo).isEnabled = editor?.canUndo() == true
        }
    }
    
    private suspend inline fun updateUndoRedo() {
        withContext(Dispatchers.Main) {
            MainActivity.activityRef.get()?.let {
                it.menu!!.findItem(R.id.redo).isEnabled = editor?.canRedo() == true
                it.menu!!.findItem(R.id.undo).isEnabled = editor?.canUndo() == true
            }
        }
    }
    
    companion object {
        val set = HashSet<String>()
    }
    
    private var t = 0
    private fun setChangeListener() {
        editor!!.subscribeAlways(ContentChangeEvent::class.java) {
            scope.launch {
                updateUndoRedo()
                t++
                
                
                try {
                    val fileName = file!!.name
                    fun addStar() {
                        val index = MainActivity.activityRef.get()!!.tabViewModel.fragmentFiles.indexOf(file)
                        val currentTitle = MainActivity.activityRef.get()!!.tabViewModel.fragmentTitles[index]
                        // Check if the title doesn't already contain a '*'
                        if (!currentTitle.endsWith("*")) {
                            MainActivity.activityRef.get()!!.tabViewModel.fragmentTitles[index] = "$currentTitle*"
                            
                            scope.launch(Dispatchers.Main) {
                                MainActivity.activityRef.get()!!.tabLayout!!.getTabAt(index)?.text =
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
    
    
    private fun getInputView(): SymbolInputView {
        
        fun hapticFeedBack(view: View) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
        
        
        return SymbolInputView(context).apply {
            
            val keys = mutableListOf<Pair<String, OnClickListener>>().apply {
                
                add(Pair("->", onClick {
                    hapticFeedBack(it)
                    editor?.onKeyDown(KeyEvent.KEYCODE_TAB, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
                }))
                
                add(Pair("⌘", onClick {
                    hapticFeedBack(it)
                    rkUtils.toast("Not Implemented")
                    
                }))
                
                add(Pair("←", onClick {
                    hapticFeedBack(it)
                    editor?.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                }))
                
                add(Pair("↑", onClick {
                    hapticFeedBack(it)
                    editor?.onKeyDown(KeyEvent.KEYCODE_DPAD_UP, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
                    
                }))
                
                add(Pair("→", onClick {
                    hapticFeedBack(it)
                    editor?.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    
                }))
                
                add(Pair("↓", onClick {
                    hapticFeedBack(it)
                    editor?.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                    
                }))
                
                add(Pair("⇇", onClick {
                    hapticFeedBack(it)
                    editor?.onKeyDown(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME))
                }))
                
                add(Pair("⇉", onClick {
                    hapticFeedBack(it)
                    editor?.onKeyDown(KeyEvent.KEYCODE_MOVE_END, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END))
                }))
            }
            
            
            
            
            addSymbols(keys.toTypedArray())
            
            addSymbols(arrayOf("(", ")", "\"", "{", "}", "[", "]", ";"), arrayOf("(", ")", "\"", "{", "}", "[", "]", ";"))
            
            bindEditor(editor)
        }
    }
}