package com.rk.xededitor.MainActivity.file

import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.collection.ArrayMap
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.provider.file
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedList
import java.util.Queue

object ProjectManager {

    val projects = ArrayMap<Int, String>()
    private val fileTreeFragments = ArrayMap<String, FileTreeFrag>()
    private val queue: Queue<File> = LinkedList()

    fun processQueue(activity: MainActivity){
        activity.lifecycleScope.launch(Dispatchers.Default){
            while (queue.isNotEmpty()){
                delay(100)
                withContext(Dispatchers.Main){
                    queue.poll()?.let { addProject(it) }
                }

            }
        }

    }

    fun addProject(file: File) {
        BaseActivity.getActivity(MainActivity::class.java)?.let { activity ->
            if (activity.isPaused){
                queue.add(file)
                return
            }
            val rail = activity.binding.navigationRail
            for (i in 0 until rail.menu.size()) {
                val item = rail.menu.getItem(i)
                val menuItemId = item.itemId
                if (menuItemId != R.id.add_new && !projects.contains(menuItemId)) {
                    item.isVisible = true
                    item.title = file.name
                   item.isChecked = true

                    projects[menuItemId] = file.absolutePath

                    val fileObj = file(file)
                    val fragment = FileTreeFrag.newInstance(fileObj)
                    fileTreeFragments[fileObj.getAbsolutePath()] = fragment

                    val transaction = activity.supportFragmentManager.beginTransaction()

                    // Detach currently attached fragments
                    activity.supportFragmentManager.fragments.forEach { frag ->
                        if (fileTreeFragments.values.contains(frag)){
                            transaction.detach(frag)
                        }
                    }

                    // Add the new fragment and attach it
                    transaction.add(R.id.frame, fragment)
                    transaction.attach(fragment)
                    transaction.commit()

                    //hide + button if 6 projects are added
                    if (activity.binding.navigationRail.menu.getItem(5).isVisible){
                        activity.binding.navigationRail.menu.getItem(6).isVisible = false
                    }

                    break
                }
            }
            saveProjects(activity)
        }


    }

    fun removeProject(file: File) {
        val filePath = file.absolutePath
        BaseActivity.getActivity(MainActivity::class.java)?.let { activity ->
            val rail = activity.binding.navigationRail
            for (i in 0 until rail.menu.size()) {
                val item = rail.menu.getItem(i)
                val menuItemId = item.itemId
                if (projects[menuItemId] == filePath) {
                    item.isChecked = false
                    item.isVisible = false

                    val fileObj = file(file)
                    projects.remove(menuItemId)
                    val fragment = fileTreeFragments.remove(fileObj.getAbsolutePath())
                    activity.supportFragmentManager.beginTransaction().remove(fragment!!).commit()


                    if (!rail.menu.getItem(6).isVisible) {
                        rail.menu.getItem(6).isVisible = true
                    }

                    fun selectItem(itemx:MenuItem){
                        val previousFilePath = projects[itemx.itemId]
                        if (previousFilePath != null) {
                            itemx.isChecked = true
                            val previousFile = File(previousFilePath)
                            changeProject(previousFile, activity)
                        }
                    }

                    if (i > 0) {
                        selectItem(rail.menu.getItem(i - 1))
                    } else if (i < rail.menu.size() - 1) {
                        selectItem(rail.menu.getItem(i + 1))
                    }

                    saveProjects(activity)

                    break
                }
            }
        }
    }


    fun changeProject(file: File, activity: MainActivity) {
        val fileObj = file(file)
        val fragment = fileTreeFragments[fileObj.getAbsolutePath()]
        val transaction = activity.supportFragmentManager.beginTransaction()

        activity.supportFragmentManager.fragments.forEach { frag ->
            if (fileTreeFragments.values.contains(frag)){
                transaction.detach(frag)
            }

        }

        transaction.attach(fragment!!)
        transaction.commit()
    }


    fun getSelectedProjectRootFilePath():String?{
        BaseActivity.getActivity(MainActivity::class.java)?.apply {
            return projects[binding.navigationRail.selectedItemId]
        }
        return null
    }

    private fun getSelectedProjectFragment(): FileTreeFrag? {
        return fileTreeFragments[getSelectedProjectRootFilePath()]
    }

    fun refreshCurrentProject(){
        getSelectedProjectFragment()?.fileTree?.reloadFileTree()
    }

    fun changeCurrentProjectRoot(file: FileObject){
        getSelectedProjectFragment()?.fileTree?.loadFiles(file)
        BaseActivity.getActivity(MainActivity::class.java)?.binding?.navigationRail?.apply {
            menu.findItem(selectedItemId).title = file.getName()
        }
    }


    fun restoreProjects(activity: MainActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val jsonString = SettingsData.getString(Keys.PROJECTS, "")
            if (jsonString.isNotEmpty()) {
                val gson = Gson()
                val projectsList = gson.fromJson(jsonString, Array<String>::class.java).toList() // Convert to a List

                projectsList.forEach {
                    val file = File(it)
                    withContext(Dispatchers.Main) {
                         if (!projects.containsValue(file.absolutePath)) {
                            activity.binding.mainView.visibility = View.VISIBLE
                            activity.binding.maindrawer.visibility = View.VISIBLE

                            addProject(file)
                        }
                    }
                }
            }
        }
    }

    private fun saveProjects(activity: MainActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val gson = Gson()
            val uniqueProjects = projects.values.toSet()
            val jsonString = gson.toJson(uniqueProjects.toList())
            SettingsData.setString(Keys.PROJECTS, jsonString)
        }
    }




}
