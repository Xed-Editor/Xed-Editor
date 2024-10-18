package com.rk.xededitor.MainActivity.file

import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.rk.filetree.interfaces.FileClickListener
import com.rk.filetree.interfaces.FileLongClickListener
import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node
import com.rk.filetree.provider.file
import com.rk.filetree.widget.DiagonalScrollView
import com.rk.filetree.widget.FileTree
import com.rk.libcommons.After
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MainActivity.Companion.activityRef
import com.rk.xededitor.MainActivity.handlers.MenuItemHandler
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import java.io.File
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.Queue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ProjectManager {

    val projects = HashMap<Int, String>()
    private val queue: Queue<File> = LinkedList()

    fun processQueue(activity: MainActivity) {
        if (activityRef.get() == null) {
            activityRef = WeakReference(activity)
        }
        activity.lifecycleScope.launch(Dispatchers.Default) {
            while (queue.isNotEmpty()) {
                delay(100)
                withContext(Dispatchers.Main) { queue.poll()?.let { addProject(activity, it) } }
            }
        }
    }

    fun addProject(activity: MainActivity, file: File) {
        if (activityRef.get() == null) {
            activityRef = WeakReference(activity)
        }
        if (projects.size >= 6) {
            return
        }

        // BaseActivity.getActivity(MainActivity::class.java)?.let { activity ->
        if (activity.isPaused) {
            queue.add(file)
            return
        }
        val rail = activity.binding.navigationRail
        for (i in 0 until rail.menu.size()) {
            val item = rail.menu.getItem(i)
            val menuItemId = item.itemId
            if (menuItemId != R.id.add_new && !projects.contains(menuItemId)) {
                item.title = file.name
                item.isVisible = true
                item.isChecked = true

                synchronized(projects) { projects[menuItemId] = file.absolutePath }

                val fileObj = file(file)

                val fileTree = FileTree(activity)
                fileTree.loadFiles(fileObj)
                fileTree.setOnFileClickListener(fileClickListener)
                fileTree.setOnFileLongClickListener(fileLongClickListener)
                val scrollView =
                    FileTreeScrollViewManager.getFileTreeParentScrollView(activity, fileTree)
                scrollView.id = file.absolutePath.hashCode()

                activity.binding.maindrawer.addView(scrollView)

                changeProject(file, activity)

                // hide + button if 6 projects are added
                if (activity.binding.navigationRail.menu.getItem(5).isVisible) {
                    activity.binding.navigationRail.menu.getItem(6).isVisible = false
                }

                break
            }
        }
        saveProjects(activity)
        // }

    }

    fun removeProject(activity: MainActivity, file: File, saveState: Boolean = true) {
        val filePath = file.absolutePath
        // BaseActivity.getActivity(MainActivity::class.java)?.let { activity ->
        val rail = activity.binding.navigationRail
        for (i in 0 until rail.menu.size()) {
            val item = rail.menu.getItem(i)
            val menuItemId = item.itemId
            if (projects[menuItemId] == filePath) {
                item.isChecked = false
                item.isVisible = false

                for (i in 0 until activity.binding.maindrawer.childCount) {
                    val view = activity.binding.maindrawer.getChildAt(i)
                    if (view is DiagonalScrollView) {
                        if (view.id == file.absolutePath.hashCode()) {
                            activity.binding.maindrawer.removeView(view)
                        }
                    }
                }
                projects.remove(menuItemId)

                if (!rail.menu.getItem(6).isVisible) {
                    rail.menu.getItem(6).isVisible = true
                }

                fun selectItem(itemx: MenuItem) {
                    val previousFilePath = projects[itemx.itemId]
                    if (previousFilePath != null) {
                        itemx.isChecked = true
                        val previousFile = File(previousFilePath)
                        changeProject(previousFile, activity)
                    }
                }

                if (i > 0) {
                    selectItem(rail.menu.getItem(i - 1).also { it.isChecked = true })
                } else if (i < rail.menu.size() - 1) {
                    selectItem(rail.menu.getItem(i + 1).also { it.isChecked = true })
                }

                if (saveState) {
                    saveProjects(activity)
                }

                //                for (ix in 0 until activity.binding.maindrawer.childCount) {
                //                    val view = activity.binding.maindrawer.getChildAt(ix)
                //                    if (view is ViewGroup) {
                //                        activity.binding.maindrawer.removeView(view)
                //                    }
                //                }

                activity.binding.maindrawer.removeView(
                    activity.binding.maindrawer.findViewById(file.absolutePath.hashCode())
                )

                break
            }
        }
        // }
    }

    private var currentProjectId: Int = -1

    fun changeProject(file: File, activity: MainActivity) {
        for (i in 0 until activity.binding.maindrawer.childCount) {
            val view = activity.binding.maindrawer.getChildAt(i)
            if (view is ViewGroup) {
                if (view.id != file.absolutePath.hashCode()) {
                    view.visibility = View.GONE
                } else {
                    view.visibility = View.VISIBLE
                    currentProjectId = file.absolutePath.hashCode()
                }
            }
        }
    }

    fun clear(activity: MainActivity) {
        projects.clear()
        for (i in 0 until activity.binding.maindrawer.childCount) {
            val view = activity.binding.maindrawer.getChildAt(i)
            if (view is DiagonalScrollView) {
                activity.binding.maindrawer.removeView(view)
            }
        }
    }

    fun getSelectedProjectRootFilePath(activity: MainActivity): String? {
        return projects[activity.binding.navigationRail.selectedItemId]
    }

    private fun getSelectedView(activity: MainActivity): FileTree {
        val view: ViewGroup = activity.binding.maindrawer.findViewById(currentProjectId)
        return (view.getChildAt(0) as ViewGroup).getChildAt(0) as FileTree
    }

    object currentProject {
        fun get(activity: MainActivity): File {
            return File(getSelectedView(activity).getRootFileObject().getAbsolutePath())
        }

        fun refresh(activity: MainActivity) {
            getSelectedView(activity).reloadFileTree()
        }

        fun updateFileRenamed(activity: MainActivity, file: File, newFile: File) {
            getSelectedView(activity).onFileRenamed(file(file), file(newFile))
        }

        fun updateFileDeleted(activity: MainActivity, file: File) {
            getSelectedView(activity).onFileRemoved(file(file))
        }

        fun updateFileAdded(activity: MainActivity, file: File) {
            getSelectedView(activity).onFileAdded(file(file))
        }
    }

    fun changeCurrentProjectRoot(file: FileObject, activity: MainActivity) {
        getSelectedView(activity).loadFiles(file)
    }

    fun restoreProjects(activity: MainActivity) {
        clear(activity)
        val jsonString = PreferencesData.getString(PreferencesKeys.PROJECTS, "")
        if (jsonString.isNotEmpty()) {
            val gson = Gson()
            val projectsList = gson.fromJson(jsonString, Array<String>::class.java).toList()

            projectsList.forEach {
                val file = File(it)
                activity.binding.mainView.visibility = View.VISIBLE
                addProject(activity, file)
            }
        }
    }
    
    private fun saveProjects(activity: MainActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val gson = Gson()
            val uniqueProjects = projects.values.toSet()
            val jsonString = gson.toJson(uniqueProjects.toList())
            PreferencesData.setString(PreferencesKeys.PROJECTS, jsonString)
        }
    }
}
