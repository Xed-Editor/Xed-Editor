package com.rk.xededitor.MainActivity.treeview2

import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.libcommons.After
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils.runOnUiThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class TreeView(val activity: MainActivity, rootFolder: File) {
	init {
		val recyclerView = activity.findViewById<RecyclerView>(PrepareRecyclerView.recyclerViewId).apply {
			setItemViewCacheSize(100)
			visibility = View.GONE
			
		}
		activity.binding.progressBar.visibility = View.VISIBLE

		activity.lifecycleScope.launch(Dispatchers.Default){
			SettingsData.setString(Keys.LAST_OPENED_PATH, rootFolder.absolutePath)
			val nodes = TreeViewAdapter.merge(rootFolder)
			val adapter = TreeViewAdapter(recyclerView, activity, rootFolder)
			adapter.apply {
				setOnItemClickListener(object : OnItemClickListener {
					override fun onItemClick(v: View, node: Node<File>) {
						val loading = LoadingPopup(activity, null).show()

						runOnUiThread {
							activity.newEditor(node.value)
							activity.adapter?.onNewEditor(node.value)
						}

						After(500) {
							if (!SettingsData.getBoolean(Keys.KEEP_DRAWER_LOCKED, false)) {
								runOnUiThread {
									activity.binding.drawerLayout.close()
									loading.hide()
								}
							}
						}


					}


					override fun onItemLongClick(v: View, node: Node<File>) {
						FileAction(activity, rootFolder, node.value, adapter)
					}
				})
				submitList(nodes)
			}

			withContext(Dispatchers.Main){
				recyclerView.layoutManager = LinearLayoutManager(activity)
				activity.binding.progressBar.visibility = View.GONE
				recyclerView.visibility = View.VISIBLE
				recyclerView.adapter = adapter
			}

		}

		
	}
}
