package com.rk.xededitor.MainActivity.tabs.editor

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.CustomScope
import com.rk.libcommons.editor.KarbonEditor
import com.rk.libcommons.editor.SearchPanel
import com.rk.libcommons.editor.SetupEditor
import com.rk.libcommons.application
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
import io.github.rosemoe.sora.event.ContentChangeEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset


@Suppress("NOTHING_TO_INLINE")
class EditorFragment(val context: Context) : CoreFragment {

    @JvmField
    var file: File? = null
    var editor: KarbonEditor? = null
    val scope = CustomScope()
    private var constraintLayout: ConstraintLayout? = null
    private lateinit var horizontalScrollView: HorizontalScrollView
    private lateinit var searchLayout: LinearLayout
    private lateinit var setupEditor: SetupEditor


    fun showArrowKeys(yes: Boolean) {
        horizontalScrollView.visibility = if (yes) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    fun showSearch(yes: Boolean) {
        searchLayout.visibility = if (yes) {
            View.VISIBLE
        } else {
            View.GONE
        }

        if (yes) {
            searchLayout.findViewById<EditText>(com.rk.libcommons.R.id.search_editor).requestFocus()
        }
    }

    override fun onCreate() {

        constraintLayout = ConstraintLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }



        editor = KarbonEditor(context).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT, 0
            )
            setupEditor = SetupEditor(this, context, scope)
        }

        horizontalScrollView = HorizontalScrollView(context).apply {
            id = View.generateViewId()
            visibility = if (PreferencesData.getBoolean(PreferencesKeys.SHOW_ARROW_KEYS, true)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
            addView(setupEditor.getInputView())
        }


        // Define the new LinearLayout
        searchLayout = SearchPanel(constraintLayout!!, editor!!).view


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
            connect(
                horizontalScrollView.id,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM
            )
            connect(horizontalScrollView.id, ConstraintSet.TOP, editor!!.id, ConstraintSet.BOTTOM)

            applyTo(constraintLayout)
        }
    }

   fun refreshEditorContent() {
        fun refresh(){
            scope.launch(Dispatchers.IO) {
                kotlin.runCatching {

                    file?.let {
                        editor?.loadFile(it,Charset.forName(PreferencesData.getString(PreferencesKeys.SELECTED_ENCODING,Charset.defaultCharset().name())))
                    }
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
                    file?.let {
                        if (fileset.contains(it.name)) {
                            fileset.remove(it.name)
                        }
                    }
                    withContext(Dispatchers.IO){
                        FilesContent.remove(file!!.absolutePath)
                    }

                }.onFailure {
                    rkUtils.toast(it.message)
                }

            }
        }


        if (isModified()) {
            MaterialAlertDialogBuilder(context).setTitle(strings.unsaved.getString())
                .setMessage(strings.ask_unsaved.getString())
                .setNegativeButton(strings.keep_editing.getString()) { _, _ ->

                }
                .setPositiveButton(strings.refresh.getString()) { _, _ ->
                    refresh()
                }.show()
        }else{
            refresh()
        }


    }


    object FilesContent {
        private val sharedPreferences =
            application!!.getSharedPreferences("files", Context.MODE_PRIVATE)
        private val mutex = Mutex()
        suspend fun containsKey(key: String): Boolean {
            mutex.withLock {
                return sharedPreferences.contains(key)
            }
        }

        suspend fun getContent(key: String): String {
            mutex.withLock {
                return sharedPreferences.getString(key, "").toString()
            }
        }

        suspend fun setContent(key: String, content: String) {
            mutex.withLock {
                sharedPreferences.edit().putString(key, content).commit()
            }
        }

        suspend fun remove(key: String) {
            mutex.withLock {
                sharedPreferences.edit().remove(key).commit()
            }
        }

    }

    override fun loadFile(file: File) {
        this.file = file
        scope.launch(Dispatchers.Default) {
            if (FilesContent.containsKey(this@EditorFragment.file!!.absolutePath)) {
                withContext(Dispatchers.Main) {
                    editor!!.setText(FilesContent.getContent(this@EditorFragment.file!!.absolutePath))
                }
            } else {
                launch {
                    editor!!.loadFile(file,Charset.forName(PreferencesData.getString(PreferencesKeys.SELECTED_ENCODING,Charset.defaultCharset().name())));FilesContent.setContent(
                    this@EditorFragment.file!!.absolutePath, editor!!.text.toString()
                )
                }
            }
            launch { setupEditor.setupLanguage(this@EditorFragment.file!!.name) }
            withContext(Dispatchers.Main) {
                setChangeListener()
                this@EditorFragment.file?.let {
                    if (it.name.endsWith(".txt") && PreferencesData.getBoolean(
                            PreferencesKeys.WORD_WRAP_TXT, true
                        )
                    ) {
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
            editor?.saveToFile(file!!,Charset.forName(PreferencesData.getString(PreferencesKeys.SELECTED_ENCODING,Charset.defaultCharset().name())))
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
                fileset.remove(file!!.name)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { rkUtils.toast(e.message) }
            }
            if (showToast) {
                withContext(Dispatchers.Main) { rkUtils.toast(rkUtils.getString(strings.saved)) }
            }


            if (file!!.parentFile!!.absolutePath == context.getTempDir().absolutePath && file!!.name.endsWith("&mut.js")){
                withContext(Dispatchers.IO){
                    Mutators.getMutators().forEach { mut ->
                        if (mut.name+"&mut.js" == file!!.name){
                            mut.script = editor?.text.toString()
                            Mutators.saveMutator(mut)
                        }
                    }
                }
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
        file?.let {
            if (fileset.contains(it.name)) {
                fileset.remove(it.name)
            }
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onClosed() {
        GlobalScope.launch(Dispatchers.IO) {
            FilesContent.remove(file!!.absolutePath)
            if (file!!.parentFile!!.absolutePath == context.getTempDir().absolutePath && file!!.name.endsWith("&mut.js")){
                file!!.delete()
            }
        }
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
        val fileset = HashSet<String>()
    }


    fun isModified(): Boolean {
        return fileset.contains(file!!.name)
    }

    private var t = 0
    private fun setChangeListener() {
        editor!!.subscribeAlways(ContentChangeEvent::class.java) {
            scope.launch {
                launch(Dispatchers.IO) {
                    FilesContent.setContent(file!!.absolutePath, editor!!.text.toString())
                }
                updateUndoRedo()

                if (t < 2) {
                    t++
                    return@launch
                }

                try {
                    val fileName = file!!.name
                    fun addStar() {
                        val index =
                            MainActivity.activityRef.get()!!.tabViewModel.fragmentFiles.indexOf(file)
                        val currentTitle =
                            MainActivity.activityRef.get()!!.tabViewModel.fragmentTitles[index]
                        // Check if the title doesn't already contain a '*'
                        if (!currentTitle.endsWith("*")) {
                            MainActivity.activityRef.get()!!.tabViewModel.fragmentTitles[index] =
                                "$currentTitle*"

                            scope.launch(Dispatchers.Main) {
                                MainActivity.activityRef.get()!!.tabLayout!!.getTabAt(index)?.text =
                                    MainActivity.activityRef.get()!!.tabViewModel.fragmentTitles[index]
                            }
                        }
                    }

                    if (fileset.contains(fileName)) {
                        addStar()
                    } else {
                        fileset.add(fileName)
                        addStar()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }


        }
    }


}