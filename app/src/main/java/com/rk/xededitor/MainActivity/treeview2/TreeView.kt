package com.rk.xededitor.MainActivity.treeview2

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.LoadingPopup
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.StaticData.nodes
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils.runOnUiThread
import java.io.File


class TreeView(val ctx: MainActivity, rootFolder: File) {

  companion object {
    var opened_file_path = ""
  }

  init {
    val recyclerView = ctx.findViewById<RecyclerView>(PrepareRecyclerView.recyclerViewId).apply {
      setItemViewCacheSize(100)
      visibility = View.GONE

    }

    ctx.binding.progressBar.visibility = View.VISIBLE


    Thread {
      opened_file_path = rootFolder.absolutePath
      SettingsData.setSetting(ctx, "lastOpenedPath", rootFolder.absolutePath)

      nodes = TreeViewAdapter.merge(rootFolder)

      val adapter = TreeViewAdapter(recyclerView, ctx, rootFolder)

      adapter.apply {
        setOnItemClickListener(object : OnItemClickListener {
          override fun onItemClick(v: View, node: Node<File>) {
            val loading = LoadingPopup(ctx, null).show()

              com.rk.libcommons.After(150) {
                  runOnUiThread {
                      ctx.newEditor(node.value)
                      ctx.onNewEditor()
                      if (!SettingsData.getBoolean(ctx, "keepDrawerLocked", false)) {
                          com.rk.libcommons.After(500) {
                              ctx.binding.drawerLayout.close()
                          }
                      }
                      loading.hide()
                  }
              }


          }


          override fun onItemLongClick(v: View, node: Node<File>) {
            FileAction(ctx, rootFolder, node.value, adapter)
          }
        })
        submitList(nodes)
      }


      ctx.runOnUiThread {
        recyclerView.layoutManager = LinearLayoutManager(ctx)
        ctx.binding.progressBar.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        recyclerView.adapter = adapter
      }

    }.start()

  }
}
