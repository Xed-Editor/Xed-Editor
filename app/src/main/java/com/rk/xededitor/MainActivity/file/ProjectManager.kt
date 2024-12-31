package com.rk.xededitor.MainActivity.file

import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.UriWrapper
import com.rk.filetree.widget.DiagonalScrollView
import com.rk.filetree.widget.FileTree
import com.rk.libcommons.application
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MainActivity.Companion.activityRef
import com.rk.xededitor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.Queue


object ProjectManager {

    val projects = HashMap<Int, String>()
    private val queue: Queue<com.rk.file.FileObject> = LinkedList()

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

    fun addProject(activity: MainActivity, file: com.rk.file.FileObject) {

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
        val rail = activity.binding!!.navigationRail
        for (i in 0 until rail.menu.size()) {
            val item = rail.menu.getItem(i)
            val menuItemId = item.itemId
            if (menuItemId != R.id.add_new && !projects.contains(menuItemId)) {
                item.title = file.getName()
                item.isVisible = true
                item.isChecked = true

                synchronized(projects) { projects[menuItemId] = file.getAbsolutePath() }

                val fileTree = FileTree(activity)
                fileTree.loadFiles(file)
                fileTree.setOnFileClickListener(fileClickListener)
                fileTree.setOnFileLongClickListener(fileLongClickListener)
                val scrollView =
                    FileTreeScrollViewManager.getFileTreeParentScrollView(activity, fileTree)
                scrollView.id = file.getAbsolutePath().hashCode()

                activity.binding!!.maindrawer.addView(scrollView)

                changeProject(file.getAbsolutePath(), activity)

                // hide + button if 6 projects are added
                if (activity.binding!!.navigationRail.menu.getItem(5).isVisible) {
                    activity.binding!!.navigationRail.menu.getItem(6).isVisible = false
                }

                break
            }
        }
        saveProjects(activity)
        // }

    }

    fun removeProject(activity: MainActivity, file: com.rk.file.FileObject, saveState: Boolean = true) {

        val rail = activity.binding!!.navigationRail
        for (i in 0 until rail.menu.size()) {
            val item = rail.menu.getItem(i)
            val menuItemId = item.itemId
            if (projects[menuItemId] == file.getAbsolutePath()) {
                item.isChecked = false
                item.isVisible = false

                for (i in 0 until activity.binding!!.maindrawer.childCount) {
                    val view = activity.binding!!.maindrawer.getChildAt(i)
                    if (view is DiagonalScrollView) {
                        if (view.id == file.getAbsolutePath().hashCode()) {
                            activity.binding!!.maindrawer.removeView(view)
                        }
                    }
                }
                projects.remove(menuItemId)

                if (!rail.menu.getItem(6).isVisible) {
                    rail.menu.getItem(6).isVisible = true
                }

                fun selectItem(itemx: MenuItem) {
                    val previousFile = projects[itemx.itemId]
                    if (previousFile != null) {
                        itemx.isChecked = true
                        changeProject(previousFile, activity)
                    }
                }

                if (i > 0) {
                    selectItem(rail.menu.getItem(i - 1).also { it.isChecked = true })
                } else if (i < rail.menu.size() - 1) {
                    selectItem(rail.menu.getItem(i + 1).also { it.isChecked = true })
                }

                if (saveState) {
                    if (file is com.rk.file.UriWrapper) {
                        kotlin.runCatching {
                            activity.getContentResolver().releasePersistableUriPermission(
                                file.file.uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                    }

                    saveProjects(activity)
                }

                //                for (ix in 0 until activity.binding!!.maindrawer.childCount) {
                //                    val view = activity.binding!!.maindrawer.getChildAt(ix)
                //                    if (view is ViewGroup) {
                //                        activity.binding!!.maindrawer.removeView(view)
                //                    }
                //                }

                activity.binding!!.maindrawer.removeView(
                    activity.binding!!.maindrawer.findViewById(file.getAbsolutePath().hashCode())
                )

                break
            }
        }
        // }
    }

    private var currentProjectId: Int = -1

    fun changeProject(path: String, activity: MainActivity) {
        for (i in 0 until activity.binding!!.maindrawer.childCount) {
            val view = activity.binding!!.maindrawer.getChildAt(i)
            if (view is ViewGroup) {
                if (view.id != path.hashCode()) {
                    view.visibility = View.GONE
                } else {
                    view.visibility = View.VISIBLE
                    currentProjectId = path.hashCode()
                }
            }
        }
    }

    fun clear(activity: MainActivity) {
        projects.clear()
        for (i in 0 until activity.binding!!.maindrawer.childCount) {
            val view = activity.binding!!.maindrawer.getChildAt(i)
            if (view is DiagonalScrollView) {
                activity.binding!!.maindrawer.removeView(view)
            }
        }
    }

    fun getSelectedProjectRootFile(activity: MainActivity): com.rk.file.FileObject? {
        projects[activity.binding!!.navigationRail.selectedItemId]?.let {
            val file = File(it)
            return if (file.exists()) {
                com.rk.file.FileWrapper(file)
            } else {
                com.rk.file.UriWrapper(application!!, Uri.parse(it))
            }
        }
        return null
    }

    private fun getSelectedView(activity: MainActivity): FileTree {
        val view: ViewGroup = activity.binding!!.maindrawer.findViewById(currentProjectId)
        return (view.getChildAt(0) as ViewGroup).getChildAt(0) as FileTree
    }

    object currentProject {
        fun get(activity: MainActivity): FileObject {
            return getSelectedView(activity).getRootFileObject()
        }

        fun refresh(activity: MainActivity) {
            getSelectedView(activity).reloadFileTree()
        }

        fun updateFileRenamed(activity: MainActivity, file: com.rk.file.FileObject, newFile: FileObject) {
            getSelectedView(activity).onFileRenamed(file, file)
        }

        fun updateFileDeleted(activity: MainActivity, file: com.rk.file.FileObject) {
            getSelectedView(activity).onFileRemoved(file)
        }

        fun updateFileAdded(activity: MainActivity, file: com.rk.file.FileObject) {
            getSelectedView(activity).onFileAdded(file)
        }
    }

    fun changeCurrentProjectRoot(file: com.rk.file.FileObject, activity: MainActivity) {
        getSelectedView(activity).loadFiles(file)
    }

    fun restoreProjects(activity: MainActivity) {
        clear(activity)
        val jsonString = PreferencesData.getString(PreferencesKeys.PROJECTS, "")
        if (jsonString.isNotEmpty()) {
            val gson = Gson()
            val projectsList = gson.fromJson(jsonString, Array<String>::class.java).toList()

            projectsList.forEach {
                if (projects.values.contains(it)) {
                    return
                }
                activity.binding!!.mainView.visibility = View.VISIBLE

                val file = File(it)
                if (file.exists()) {
                    addProject(activity, com.rk.file.FileWrapper(file))
                } else {
                    addProject(activity, com.rk.file.UriWrapper(application!!, Uri.parse(it)))
                }


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
