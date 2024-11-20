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
import com.rk.xededitor.rkUtils
import com.jcraft.jsch.*
import com.rk.xededitor.DefaultScope

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
        // Safety check for activity reference
        if (activityRef.get() == null) {
            activityRef = WeakReference(activity)
        }
        
        // Early returns for invalid states
        if (projects.size >= 6) {
            return
        }
        if (activity.isPaused) {
            queue.add(file)
            return
        }
        
        if (projects.containsKey(file.absolutePath)){
            rkUtils.toast("Project already opened")
            return
        }
        
        try {
            val rail = activity.binding?.navigationRail ?: return
            
            // Find available menu item safely
            val availableMenuItem = (0 until rail.menu.size())
                .map { rail.menu.getItem(it) }
                .find { item ->
                    item.itemId != R.id.add_new && !menuItems.containsKey(item.itemId)
                } ?: return
            
            // Setup menu item
            val menuItemId = availableMenuItem.itemId
            availableMenuItem.apply {
                title = file.name
                isVisible = true
                isChecked = true
            }
            
            // Create and store project
            FileTree(activity, file.absolutePath, activity.binding!!.maindrawer)
            val newViewId = multiView.getCurrentViewId()
            
            // Update state maps atomically
            synchronized(this) {
                projects[file.absolutePath] = newViewId
                menuItems[menuItemId] = newViewId
                currentProjectId = newViewId
            }
            
            // Update UI state
            if (rail.menu.getItem(5).isVisible) {
                rail.menu.getItem(6).isVisible = false
            }
            
            // Save state
            activity.lifecycleScope.launch {
                saveProjects(activity)
            }
            
        } catch (e: Exception) {
            // Log error and restore consistent state if needed
            println("Error adding project: ${e.message}")
            // Could add error recovery logic here if needed
        }
    }

    fun addRemoteFolder(activity: MainActivity, connectionString: String) {
        if (activityRef.get() == null) {
            activityRef = WeakReference(activity)
        }
        if (projects.size >= 6) {
            return
        }
        if (projects.containsKey(connectionString)){
            rkUtils.toast("Project already opened")
            return
        }
        val parts = connectionString.split("@", ":", "/", limit = 5)
        val path = "/${parts[4]}"
        val jsch = JSch()
        val session = jsch.getSession(parts[0], parts[2], parts[3].toInt())
        session.setPassword(parts[1])
        session.setConfig("StrictHostKeyChecking", "no")
        DefaultScope.launch(Dispatchers.IO) {
            try {
                session.connect(5000)
                if (session.isConnected) {
                    val sftpChannel = session.openChannel("sftp") as ChannelSftp
                    sftpChannel.connect()
                    val rail = activity.binding?.navigationRail ?: return
                    val availableMenuItem = (0 until rail.menu.size())
                        .map { rail.menu.getItem(it) }
                        .find { item ->
                            item.itemId != R.id.add_new && !menuItems.containsKey(item.itemId)
                        } ?: return
                    val menuItemId = availableMenuItem.itemId
                    availableMenuItem.apply {
                        title = path
                        isVisible = true
                        isChecked = true
                    }
                    val newViewId = multiView.getCurrentViewId()
                    synchronized(this) {
                        projects[connectionString] = newViewId
                        menuItems[menuItemId] = newViewId
                        currentProjectId = newViewId
                    }
                    if (rail.menu.getItem(5).isVisible) {
                        rail.menu.getItem(6).isVisible = false
                    }
                    activity.lifecycleScope.launch {
                        saveProjects(activity)
                    }
                } else {
                    rkUtils.toast("Failed. Check your connection data!")
                }
            } catch (e: Exception) {
                rkUtils.toast("Error: ${e.message}")
            }
            session.disconnect()
        }
    }
    
    fun changeProject(menuItemId: Int, activity: MainActivity) {
        try {
            // Safety check for valid menu item ID
            val viewId = menuItems[menuItemId] ?: return
            
            // Safety check for valid view
            if (!views.containsKey(viewId)) {
                return
            }
            
            // Perform view switch on main thread if needed
            if (!activity.isFinishing) {
                activity.runOnUiThread {
                    try {
                        multiView.switchTo(viewId)
                        currentProjectId = multiView.getCurrentViewId()
                        
                        // Update menu item states
                        activity.binding?.navigationRail?.let { rail ->
                            // Uncheck all menu items
                            for (i in 0 until rail.menu.size()) {
                                rail.menu.getItem(i).isChecked = false
                            }
                            // Check the selected item
                            rail.menu.findItem(menuItemId)?.isChecked = true
                        }
                    } catch (e: Exception) {
                        println("Error switching view: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error changing project: ${e.message}")
        }
    }
    
    fun removeProject(activity: MainActivity, file: File, saveState: Boolean = true) {
        val filePath = file.absolutePath
        val projectId = projects[filePath] ?: return // Early return if project doesn't exist
        val rail = activity.binding!!.navigationRail
        
        // First find and clear the menu item
        val menuItemEntry = menuItems.entries.find { it.value == projectId }
        if (menuItemEntry != null) {
            val menuItemId = menuItemEntry.key
            val menuItem = rail.menu.findItem(menuItemId)
            menuItem?.apply {
                isChecked = false
                isVisible = false
            }
            
            // Show the add button if it was hidden
            rail.menu.getItem(6).isVisible = true
            
            // Remove the view associated with this project
            activity.binding!!.maindrawer.findViewById<View>(projectId)?.let { view ->
                activity.binding!!.maindrawer.removeView(view)
            }
            
            // Select next/previous project if available
            val menuIndex = (0 until rail.menu.size()).find { rail.menu.getItem(it).itemId == menuItemId }
            if (menuIndex != null) {
                when {
                    menuIndex > 0 -> {
                        val prevItem = rail.menu.getItem(menuIndex - 1)
                        if (prevItem.isVisible) {
                            changeProject(prevItem.itemId, activity)
                        }
                    }
                    menuIndex < rail.menu.size() - 1 -> {
                        val nextItem = rail.menu.getItem(menuIndex + 1)
                        if (nextItem.isVisible) {
                            changeProject(nextItem.itemId, activity)
                        }
                    }
                }
            }
            
            // Clean up data structures
            projects.remove(filePath)
            views.remove(projectId)
            menuItems.remove(menuItemEntry.key)
            
            // Switch to first remaining view if available
            if (views.isNotEmpty()) {
                multiView.switchTo(views.keys.first())
                currentProjectId = multiView.getCurrentViewId()
            } else {
                currentProjectId = -1
            }
            
            // Save state if requested
            if (saveState) {
                saveProjects(activity)
            }
        }
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
