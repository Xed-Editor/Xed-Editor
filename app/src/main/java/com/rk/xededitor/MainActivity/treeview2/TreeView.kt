package com.rk.xededitor.MainActivity.treeview2

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.libcommons.After
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.StaticData.nodes
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils.runOnUiThread
import java.io.File


class TreeView(val activity: MainActivity, rootFolder: File) {
    init {
        val recyclerView =
            activity.findViewById<RecyclerView>(PrepareRecyclerView.recyclerViewId).apply {
                setItemViewCacheSize(100)
                visibility = View.GONE

            }
        activity.binding!!.progressBar.visibility = View.VISIBLE
        Thread {
            
            SettingsData.setString(SettingsData.Keys.LAST_OPENED_PATH, rootFolder.absolutePath)
            nodes = TreeViewAdapter.merge(rootFolder)
            val adapter = TreeViewAdapter(recyclerView, activity, rootFolder)
            adapter.apply {
                setOnItemClickListener(object : OnItemClickListener {
                    override fun onItemClick(v: View, node: Node<File>) {
                        val loading = LoadingPopup(activity, null).show()

                        After(150) {
                            runOnUiThread {
                                activity.newEditor(node.value)
                                activity.adapter?.onNewEditor()
                                if (!SettingsData.getBoolean(SettingsData.Keys.KEEP_DRAWER_LOCKED, false)) {
                                    After(500) {
                                        activity.binding!!.drawerLayout.close()
                                    }
                                }
                                loading.hide()
                            }
                        }


                    }


                    override fun onItemLongClick(v: View, node: Node<File>) {
                        FileAction(activity, rootFolder, node.value, adapter)
                    }
                })
                submitList(nodes)
            }


            activity.runOnUiThread {
                recyclerView.layoutManager = LinearLayoutManager(activity)
                activity.binding!!.progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerView.adapter = adapter
            }

        }.start()

    }
}
