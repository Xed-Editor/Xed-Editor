package com.rk.xededitor.MainActivity.treeview2

import android.view.View
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import com.rk.xededitor.After
import com.rk.xededitor.MainActivity.StaticData

import com.rk.xededitor.MainActivity.StaticData.nodes
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.Settings.SettingsData

class TreeView(val ctx: MainActivity, rootFolder: DocumentFile) {
  init {
    
    Thread {
    ctx.runOnUiThread {
        ctx.binding.recyclerView.visibility = View.GONE
        ctx.binding.progressBar.visibility = View.VISIBLE
      }
      
      
      nodes = TreeViewAdapter.merge(StaticData.rootFolder)
      
      
      ctx.runOnUiThread {
        
        with(ctx.binding.recyclerView) {
          
          layoutManager = LinearLayoutManager(ctx)
          setItemViewCacheSize(100)
          
          
          //itemAnimator = null
          
          
          adapter = TreeViewAdapter(this, ctx).apply {
            submitList(nodes)
            setOnItemClickListener(object : OnItemClickListener {
              override fun onItemClick(v: View, node: Node<DocumentFile>) {
                // if (node.value.isFile) {
                ctx.newEditor(node.value, false)
                ctx.onNewEditor()
                if (!SettingsData.getBoolean(ctx, "keepDrawerLocked", false)) {
                  After(500) {
                    ctx.binding.drawerLayout.close()
                  }
                }
                // }
              }
              
              
              override fun onItemLongClick(v: View, node: Node<DocumentFile>) {
                
                TreeViewAdapter.nodemap?.get(node)?.let {
                  HandleFileActions(ctx, rootFolder, node.value, it)
                }
              }
            })
          }
        }
        ctx.binding.progressBar.visibility = View.GONE
        ctx.binding.recyclerView.visibility = View.VISIBLE
        
      }
    }.start()
    
    
  }
}
