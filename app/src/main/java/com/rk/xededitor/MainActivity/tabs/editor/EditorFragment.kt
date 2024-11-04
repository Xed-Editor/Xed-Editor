package com.rk.xededitor.MainActivity.tabs.editor

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Pair
import android.view.KeyEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.rk.libcommons.CustomScope
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.R
import com.rk.xededitor.SetupEditor
import com.rk.xededitor.rkUtils
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
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
    var setupEditor: SetupEditor? = null
    var constraintLayout: ConstraintLayout? = null
    var isCmdActive = false
    
    override fun onCreate() {
        val showKeys = PreferencesData.getBoolean(PreferencesKeys.SHOW_ARROW_KEYS, true)
        
        // Initialize ConstraintLayout as the root view
        constraintLayout = ConstraintLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Initialize the KarbonEditor
        editor = KarbonEditor(context).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT, 0 // Let it be constrained by the bottom of the screen or HorizontalScrollView
            )
        }
        setupEditor = SetupEditor(editor!!, context)
        setupEditor?.ensureTextmateTheme(context)
        
        val horizontalScrollView = if (showKeys) {
            // Initialize the HorizontalScrollView
            HorizontalScrollView(context).apply {
                id = View.generateViewId()
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT
                )
                addView(getInputView())
            }
        } else {
            null
        }
        
        
        // Add views to ConstraintLayout
        constraintLayout!!.addView(editor)
        if (showKeys) {
            constraintLayout!!.addView(horizontalScrollView)
        }
        
        ConstraintSet().apply {
            clone(constraintLayout)
            
            // Set editor constraints
            connect(editor!!.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            if (showKeys) {
                connect(editor!!.id, ConstraintSet.BOTTOM, horizontalScrollView!!.id, ConstraintSet.TOP)
                
                // Set HorizontalScrollView constraints
                connect(horizontalScrollView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                connect(horizontalScrollView.id, ConstraintSet.TOP, editor!!.id, ConstraintSet.BOTTOM)
            }
            
            applyTo(constraintLayout)
        }
    }
    
    override fun loadFile(xfile: File) {
        file = xfile
        scope.launch(Dispatchers.Default) {
            setupEditor?.setupLanguage(file!!.name)
            editor!!.loadFile(xfile)
            withContext(Dispatchers.Main) {
                setChangeListener()
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
        
        val cmdInterceptor = CodeEditor.KeyInterceptor { keyCode, keyEvent ->
            
            rkUtils.toast("yo")
            
            //don't allow editor to get this key
            false
        }
        
        
        
        fun Int.dpToPx(): Int = (this * context.resources.displayMetrics.density).toInt()
        return SymbolInputView(context).apply {
            addSymbols(arrayOf("->"), arrayOf("\t"))
            
            val keys = mutableListOf<Pair<String, OnClickListener>>().apply {
                
                add(Pair("⌘", onClick {
                    hapticFeedBack(it)
                    isCmdActive = !isCmdActive
                    if (isCmdActive) {
                        editor?.interceptor = cmdInterceptor
                        it.background = ContextCompat.getDrawable(context, R.drawable.inset_background)
                    } else {
                        editor?.interceptor = null
                        it.background = ColorDrawable(Color.TRANSPARENT)
                    }
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
            }
            
            
            
            
            addSymbols(keys.toTypedArray())
            
            addSymbols(arrayOf("(", ")", "\"", "{", "}", "[", "]", ";"), arrayOf("(", ")", "\"", "{", "}", "[", "]", ";"))
            
            bindEditor(editor)
        }
    }
}