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
import com.rk.file.FileObject
import com.rk.libcommons.CustomScope
import com.rk.libcommons.UI
import com.rk.libcommons.editor.KarbonEditor
import com.rk.libcommons.editor.SearchPanel
import com.rk.libcommons.editor.SetupEditor
import com.rk.libcommons.safeLaunch
import com.rk.libcommons.toast
import com.rk.libcommons.withCatching
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.SettingsKey
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.R
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.Charset


class EditorFragment(val context: Context) : CoreFragment {

    @JvmField
    var file: FileObject? = null
    var editor: KarbonEditor? = null
    val scope = CustomScope()
    private var constraintLayout: ConstraintLayout? = null
    private lateinit var horizontalScrollView: HorizontalScrollView
    private lateinit var searchLayout: LinearLayout
    var setupEditor: SetupEditor? = null


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
            visibility = if (Settings.getBoolean(SettingsKey.SHOW_ARROW_KEYS, true)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false

            addView(setupEditor!!.getInputView())
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

    fun refreshEditorContent() {
        fun refresh() {
            scope.safeLaunch(Dispatchers.IO) {
                isFileLoaded = false
                editor?.loadFile(
                    file!!.getInputStream(), Charset.forName(
                        Settings.getString(
                            SettingsKey.SELECTED_ENCODING,
                            Charset.defaultCharset().name()
                        )
                    )
                )
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
            return MainActivity.activityRef.get()?.tabViewModel?.fragmentContent?.containsKey(key)
                ?: false
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

    private var isFileLoaded = false
    override fun loadFile(file: FileObject) {
        this.file = file
        scope.safeLaunch {
            UI {
                setChangeListener()
                file.let {
                    if (it.getName().endsWith(".txt") && Settings.getBoolean(
                            SettingsKey.WORD_WRAP_TXT,
                            true
                        )
                    ) {
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
                        editor!!.setText(FilesContent.getContent(this@EditorFragment.file!!.getAbsolutePath()))
                        isFileLoaded = true
                    }
                }
            } else {
                editor!!.loadFile(
                    file.getInputStream(), Charset.forName(
                        Settings.getString(
                            SettingsKey.SELECTED_ENCODING,
                            Charset.defaultCharset().name()
                        )
                    )
                )
                FilesContent.setContent(
                    this@EditorFragment.file!!.getAbsolutePath(),
                    editor!!.text
                )
                isFileLoaded = true
            }
        }
    }

    override fun getFile(): FileObject? = file

    @OptIn(DelicateCoroutinesApi::class)
    fun save(showToast: Boolean = true, isAutoSaver: Boolean = false) {
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

        if (file!!.canWrite().not()) {
            if (isAutoSaver) {
                return
            }
            toast(strings.permission_denied)
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
            val charset = Settings.getString(
                SettingsKey.SELECTED_ENCODING, Charset.defaultCharset().name()
            )
            editor?.saveToFile(file!!.getOutPutStream(false), Charset.forName(charset))

            val isMutatorFile = file!!.getParentFile()
                ?.getAbsolutePath() == getTempDir().absolutePath && file!!.getName()
                .endsWith("&mut.js")

            if (isMutatorFile) {
                Mutators.getMutators().forEach { mut ->
                    if (mut.name + "&mut.js" == file!!.getName()) {
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

            if (showToast) {
                withContext(Dispatchers.Main) { toast(strings.saved.getString()) }
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
            if (fileset.contains(it.getName())) {
                fileset.remove(it.getName())
            }
        }


    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onClosed() {
        GlobalScope.launch(Dispatchers.IO) {
            withCatching {
                file?.getAbsolutePath()?.let { FilesContent.remove(it) }
                if (file?.getParentFile()
                        ?.getAbsolutePath() == getTempDir().absolutePath && file!!.getName()
                        .endsWith("&mut.js")
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
        return 2 >= t && isFileLoaded
    }

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

                    MainActivity.withContext {
                        tabLayout!!.getTabAt(index)?.text = fragmentTitles[index]
                    }
                }
            }
        }
    }
}