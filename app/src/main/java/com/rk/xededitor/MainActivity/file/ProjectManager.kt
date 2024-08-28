package com.rk.xededitor.MainActivity.file

import androidx.collection.ArrayMap
import com.rk.filetree.provider.file
import com.rk.xededitor.BaseActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import java.io.File


object ProjectManager {

    val projects = ArrayMap<Int,String>()

    fun addProject(file: File){
        BaseActivity.getActivity(MainActivity::class.java)?.let {
           val rail = it.binding.navigationRail
            for (i in 0 until rail.menu.size()){
                val item = rail.menu.getItem(i)
                val menuItemId = item.itemId
                if (menuItemId != R.id.add_new && !projects.contains(menuItemId)){
                    item.isVisible = true
                    item.title = file.name
                    item.isChecked = true
                    projects[menuItemId] = file.absolutePath
                    break
                }
            }
        }
    }
    fun removeProject(file: File){
        val filePath = file.absolutePath
        BaseActivity.getActivity(MainActivity::class.java)?.let {
            val rail = it.binding.navigationRail
            for (i in 0 until rail.menu.size()) {
                val item = rail.menu.getItem(i)
                val menuItemId = item.itemId
                if (projects[menuItemId] == filePath){
                    projects.remove(menuItemId)
                    item.isChecked = false
                    item.isVisible = false
                    if (!rail.menu.getItem(6).isVisible){
                        rail.menu.getItem(6).isVisible = true
                    }
                    break
                }
            }
        }

    }
    fun changeProject(file: File,activity: MainActivity){
        activity.fileTree.loadFiles(file(file))

    }

}