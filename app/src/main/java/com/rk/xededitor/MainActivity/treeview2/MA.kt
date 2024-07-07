package com.rk.xededitor.MainActivity.treeview2

import android.view.View
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.After
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.rkUtils
import java.util.Timer
import java.util.TimerTask

class MA(val ctx: MainActivity, rootFolder: DocumentFile) {
  init {
    val recyclerView: RecyclerView = ctx.findViewById<RecyclerView>(R.id.recycler_view)
    
    
    
    
    with(recyclerView) {
      visibility = View.VISIBLE
      
      val nodes = TreeViewAdapter.merge(rootFolder)
      layoutManager = LinearLayoutManager(ctx)
      setItemViewCacheSize(300)
      itemAnimator = null
      
      
      adapter = TreeViewAdapter(ctx, nodes).apply {
        setOnItemClickListener(object : OnItemClickListener {
          override fun onItemClick(v: View, position: Int) {
            val file = nodes[position].value
            if (file.isFile) {
              ctx.newEditor(file,false)
              ctx.onNewEditor()
              if (!SettingsData.getBoolean(ctx, "keepDrawerLocked", false)) {
                After(500){
                  ctx.binding.drawerLayout.close()
                }
              }
            }
          }
          
          
          override fun onItemLongClick(v: View, position: Int) {
            val file = nodes[position].value
            TreeViewAdapter.nodemap?.get(nodes[position])?.let {
              HandleFileActions(ctx, rootFolder, file, it)
            }
          }
        })
      }
      
      
    }
  }
}