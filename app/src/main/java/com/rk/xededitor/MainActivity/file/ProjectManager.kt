package com.rk.xededitor.MainActivity.file

import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MainActivity.Companion.activityRef
import com.rk.xededitor.MainActivity.MultiView
import com.rk.xededitor.MainActivity.file.filetree.FileTree
import com.rk.xededitor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.Queue
import android.view.MenuItem


//welcome to hell
class ProjectManager {
    //view project id : filetree view
    var views = HashMap<Int,View>()
    private val queue: Queue<File> = LinkedList()
    
    //file path : view project id
    val projects = HashMap<String,Int>()
    
    //menu item id : view project id
    val menuItems = HashMap<Int,Int>()
    
    private var currentProjectId: Int = -1
    
    private lateinit var multiView: MultiView
    
    fun onCreate(activity: MainActivity){
        multiView = activity.binding!!.maindrawer
        multiView.setViews(views)
    }
    
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
        if (activity.isPaused) {
            queue.add(file)
            return
        }
        
        
        val rail = activity.binding!!.navigationRail
        for (i in 0 until rail.menu.size()) {
            val item = rail.menu.getItem(i)
            val menuItemId = item.itemId
            if (menuItemId != R.id.add_new && !menuItems.contains(menuItemId)) {
                item.title = file.name
                item.isVisible = true
                item.isChecked = true
                
                if (projects.containsKey(file.absolutePath)){
                    multiView.switchTo(projects[file.absolutePath]!!)
                    currentProjectId = multiView.getCurrentViewId()
                    return
                }
                FileTree(activity,file.absolutePath,activity.binding!!.maindrawer)
                println(views.keys)
                projects[file.absolutePath] = multiView.getCurrentViewId()
                currentProjectId = multiView.getCurrentViewId()
                
                
                // hide + button if 6 projects are added
                if (activity.binding!!.navigationRail.menu.getItem(5).isVisible) {
                    activity.binding!!.navigationRail.menu.getItem(6).isVisible = false
                }
                menuItems[menuItemId] = currentProjectId
                break
            }
        }
        saveProjects(activity)
        
    }

    fun removeProject(activity: MainActivity, file: File, saveState: Boolean = true) {
       
        val filePath = file.absolutePath
        val rail = activity.binding!!.navigationRail
        for (i in 0 until rail.menu.size()) {
            val item = rail.menu.getItem(i)
            val menuItemId = item.itemId
            if (projects.containsKey(filePath)) {
                item.isChecked = false
                item.isVisible = false
                
                for (i in 0 until activity.binding!!.maindrawer.childCount) {
                    val view = activity.binding!!.maindrawer.getChildAt(i)
                    if (view.id == currentProjectId) {
                        activity.binding!!.maindrawer.removeView(view)
                    }
                }
                
                if (!rail.menu.getItem(6).isVisible) {
                    rail.menu.getItem(6).isVisible = true
                }
                
                fun selectItem(itemx: MenuItem) {
                    changeProject(itemx.itemId,activity)
                }
                
                if (i > 0) {
                    selectItem(rail.menu.getItem(i - 1).also { it.isChecked = true })
                } else if (i < rail.menu.size() - 1) {
                    selectItem(rail.menu.getItem(i + 1).also { it.isChecked = true })
                }
                
                if (saveState) {
                    saveProjects(activity)
                }
          
                
            }
            activity.binding!!.maindrawer.removeView(
                activity.binding!!.maindrawer.findViewById(file.absolutePath.hashCode())
            )
            
            break
        }
        val viewId = projects[file.absolutePath]
        projects.remove(file.absolutePath)
        views.remove(viewId)
        menuItems.remove(viewId)
        multiView.switchTo(views.keys.first())
        currentProjectId = multiView.getCurrentViewId()
        
    }



fun changeProject(menuItemId:Int, activity: MainActivity) {
        multiView.switchTo(menuItems[menuItemId]!!)
        currentProjectId = multiView.getCurrentViewId()
    }
    
    
    fun getSelectedProjectRootFile():File?{
        projects.entries.forEach{ e ->
            if (e.value == currentProjectId){
                return File(e.key)
            }
        }
        return null
    }
    
    fun restoreProjects(activity: MainActivity) {
        views.clear()
        projects.clear()
        multiView.clearView()
        val jsonString = PreferencesData.getString(PreferencesKeys.PROJECTS, "")
        if (jsonString.isNotEmpty()) {
            val gson = Gson()
            val projectsList = gson.fromJson(jsonString, Array<String>::class.java).toList()

            projectsList.forEach {
                val file = File(it)
                activity.binding!!.mainView.visibility = View.VISIBLE
                addProject(activity, file)
            }
        }
    }
    
    private fun saveProjects(activity: MainActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val gson = Gson()
            val uniqueProjects = projects.keys.toSet()
            val jsonString = gson.toJson(uniqueProjects.toList())
            PreferencesData.setString(PreferencesKeys.PROJECTS, jsonString)
        }
    }
}
