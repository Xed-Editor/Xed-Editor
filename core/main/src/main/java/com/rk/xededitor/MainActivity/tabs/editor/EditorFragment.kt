package com.rk.xededitor.MainActivity.tabs.editor

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.file_wrapper.FileObject
import com.rk.libcommons.UI
import com.rk.libcommons.editor.KarbonEditor
import com.rk.libcommons.editor.SearchPanel
import com.rk.libcommons.editor.SetupEditor
import com.rk.libcommons.editor.getInputView
import com.rk.libcommons.errorDialog
import com.rk.libcommons.safeLaunch
import com.rk.libcommons.toast
import com.rk.libcommons.toastCatching
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.App.Companion.getTempDir
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.R
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.Charset


class EditorFragment(val context: Context,val scope:CoroutineScope) : CoreFragment {

    @JvmField
    var file: FileObject? = null
    var editor: KarbonEditor? = null
    private var constraintLayout: ConstraintLayout? = null
    private lateinit var horizontalScrollView: HorizontalScrollView
    private lateinit var searchLayout: LinearLayout
    var setupEditor: SetupEditor? = null
    private var isFileLoaded = false

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
            searchLayout.findViewById<EditText>(R.id.search_editor).requestFocus()
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
            visibility = if (Settings.show_arrow_keys) {
                View.VISIBLE
            } else {
                View.GONE
            }
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false

            addView(getInputView(editor!!))
        }

        searchLayout = SearchPanel(constraintLayout!!, editor!!).view

        constraintLayout!!.addView(searchLayout)
        constraintLayout!!.addView(editor)
        constraintLayout!!.addView(horizontalScrollView)

        ConstraintSet().apply {
            clone(constraintLayout)
            connect(searchLayout.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(editor!!.id, ConstraintSet.TOP, searchLayout.id, ConstraintSet.BOTTOM)
            connect(editor!!.id, ConstraintSet.BOTTOM, horizontalScrollView.id, ConstraintSet.TOP)
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

    fun refreshEditorContent(autoRefresher: Boolean = false) {
        if (autoRefresher){
            if (System.currentTimeMillis() - lastSaveTime < 500){
                return
            }
        }
        fun refresh() {
            scope.safeLaunch(Dispatchers.IO) {
                isFileLoaded = false
                editor?.loadFile(file!!, Charset.forName(Settings.encoding))
                UI {
                    MainActivity.withContext {
                        val index = tabViewModel.fragmentFiles.indexOf(file)
                        tabViewModel.fragmentTitles.let {
                            if (file!!.getName() != it[index]) {
                                it[index] = file!!.getName()
                                tabLayout!!.getTabAt(index)?.text = file!!.getName()
                            }
                        }
                    }
                }
                file?.let {
                    if (fileset.contains(it.getName())) {
                        fileset.remove(it.getName())
                    }
                }
                FilesContent.remove(file!!.getAbsolutePath())
                isFileLoaded = true

            }
        }


        if (isModified()) {
            if (autoRefresher){
                return
            }
            MaterialAlertDialogBuilder(context).setTitle(strings.unsaved.getString())
                .setMessage(strings.ask_unsaved.getString())
                .setNegativeButton(strings.keep_editing.getString(), null)
                .setPositiveButton(strings.refresh.getString()) { _, _ ->
                    refresh()
                }.show()
        } else {
            refresh()
        }


    }

    object FilesContent {
        fun containsKey(key: String): Boolean {
            return MainActivity.activityRef.get()?.tabViewModel?.fragmentContent?.containsKey(key) == true
        }

        fun getContent(key: String): Content? {
            return MainActivity.activityRef.get()?.tabViewModel?.fragmentContent?.get(key)
        }

        fun setContent(key: String, content: Content) {
            MainActivity.activityRef.get()?.tabViewModel?.fragmentContent?.put(key, content)
        }

        fun remove(key: String) {
            MainActivity.activityRef.get()?.tabViewModel?.fragmentContent?.remove(key)
        }

    }

    override fun loadFile(file: FileObject) {
        this.file = file
        scope.safeLaunch {
            UI {
                setChangeListener()
                file.let {
                    if (it.getName().endsWith(".txt") && Settings.word_wrap_for_text) {
                        editor?.isWordwrap = true
                    }
                }
            }
            safeLaunch {
                setupEditor!!.setupLanguage(this@EditorFragment.file!!.getName())
            }
            if (FilesContent.containsKey(this@EditorFragment.file!!.getAbsolutePath())) {
                mutex.withLock {
                    withContext(Dispatchers.Main) {
                        isFileLoaded = false
                        editor!!.setText(FilesContent.getContent(this@EditorFragment.file!!.getAbsolutePath()))
                        isFileLoaded = true
                    }
                }
            } else {
                isFileLoaded = false
                editor!!.loadFile(file, Charset.forName(Settings.encoding))
                FilesContent.setContent(
                    this@EditorFragment.file!!.getAbsolutePath(),
                    editor!!.text
                )
                isFileLoaded = true
            }
        }
    }

    override fun getFile(): FileObject? = file


    private var lastSaveTime = 0L
    private val saveMutex = Mutex()

    @androidx.annotation.OptIn(ExperimentalBadgeUtils::class)
    @OptIn(DelicateCoroutinesApi::class)
    fun save(isAutoSaver: Boolean = false) {
        if (isAutoSaver && isReadyToSave().not()) {
            return
        }

        if (file == null) {
            if (isAutoSaver) {
                return
            }
            toast(strings.file_err)
            return
        }

        if (isFileLoaded.not()) {
            if (isAutoSaver) {
                return
            }
            toast(strings.file_not_loaded)
            return
        }

        if (file!!.exists().not()) {
            if (isAutoSaver) {
                return
            }
            toast(strings.file_exist_not)
            return
        }

        GlobalScope.safeLaunch(Dispatchers.IO) {
            saveMutex.withLock{
                runCatching {
                    val charset = Settings.encoding
                    val text = editor?.text.toString()
                    if (isAutoSaver && text.isBlank()){
                        return@safeLaunch
                    }
                    file!!.writeText(text,charset = Charset.forName(charset))
                    lastSaveTime = System.currentTimeMillis()
                }.onFailure {
                    if (it is SecurityException){
                        if (isAutoSaver.not()){
                            toast(strings.read_only_file)
                        }
                        return@safeLaunch
                    }else{
                        if (isAutoSaver.not()){
                            errorDialog(it)
                        }
                        it.printStackTrace()
                    }
                }
            }


            toastCatching {
                val charset = Settings.encoding
                file!!.writeText(editor?.text.toString(),charset = Charset.forName(charset))
                withContext(Dispatchers.Main){
                    MainActivity.withContext {
                        badge?.let {
                            BadgeUtils.detachBadgeDrawable(it, binding!!.toolbar, R.id.action_save)
                        }
                    }
                }
            }?.let{
                if (it is SecurityException){
                    toast(strings.read_only_file)
                    return@safeLaunch
                }else{
                    it.printStackTrace()
                }
            }

            val isMutatorFile = file!!.getParentFile()
                ?.getAbsolutePath() == getTempDir().absolutePath && file!!.getName()
                .endsWith(".mut")

            if (isMutatorFile) {
                Mutators.getMutators().forEach { mut ->
                    if (mut.name + ".mut" == file!!.getName()) {
                        mut.script = editor?.text.toString()
                        Mutators.saveMutator(mut)
                    }
                }
            }

            MainActivity.activityRef.get()?.let { activity ->
                val index = activity.tabViewModel.fragmentFiles.indexOf(file)
                activity.tabViewModel.fragmentTitles.let {
                    if (file!!.getName() != it[index]) {
                        it[index] = file!!.getName()
                        withContext(Dispatchers.Main) {
                            activity.tabLayout!!.getTabAt(index)?.text = file!!.getName()
                        }
                    }
                }
            }
            fileset.remove(file!!.getName())
        }
    }

    override fun getView(): View? {
        return constraintLayout
    }

    override fun onDestroy() {
        editor?.release()
        file?.let {
            if (fileset.contains(it.getName())) {
                fileset.remove(it.getName())
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onClosed() {
        GlobalScope.launch(Dispatchers.IO) {
            toastCatching {
                file?.getAbsolutePath()?.let { FilesContent.remove(it) }
                if (file?.getParentFile()
                        ?.getAbsolutePath() == getTempDir().absolutePath && file!!.getName()
                        .endsWith(".mut")
                ) {
                    file?.delete()
                }
            }
        }
        onDestroy()

    }

    suspend fun undo() {
        editor?.undo()
        updateUndoRedo()
    }

    suspend fun redo() {
        editor?.redo()
        updateUndoRedo()
    }

    private suspend inline fun updateUndoRedo() {
        withContext(Dispatchers.Main) {
            MainActivity.activityRef.get()?.let {
                it.menu?.findItem(R.id.redo)?.isEnabled = editor?.canRedo() == true
                it.menu?.findItem(R.id.undo)?.isEnabled = editor?.canUndo() == true
            }
        }
    }

    companion object {
        val fileset = HashSet<String>()
    }


    fun isModified(): Boolean {
        return fileset.contains(file!!.getName())
    }

    private var t = 0
    private val mutex = Mutex()
    private fun isReadyToSave(): Boolean {
        return 4 >= t && isFileLoaded
    }

    @androidx.annotation.OptIn(ExperimentalBadgeUtils::class)
    private fun setChangeListener() = editor!!.subscribeAlways(ContentChangeEvent::class.java) {
        scope.safeLaunch {
            updateUndoRedo()

            //return if the file is being loaded
            if (t < 2) {
                t++;return@safeLaunch
            }

            fileset.add(file!!.getName())

            MainActivity.activityRef.get()?.tabViewModel?.apply {
                val index = fragmentFiles.indexOf(file)
                val currentTitle = fragmentTitles[index]

                if (currentTitle.endsWith("*").not()) {
                    fragmentTitles[index] = "$currentTitle*"

                    scope.launch(Dispatchers.Main){
                        MainActivity.withContext {
                            tabLayout!!.getTabAt(index)?.text = fragmentTitles[index]
                        }
                        MainActivity.withContext {
                            badge?.let {
                                BadgeUtils.attachBadgeDrawable(it, binding!!.toolbar, R.id.action_save)
                            }
                        }
                    }



                }
            }
        }
    }
}
