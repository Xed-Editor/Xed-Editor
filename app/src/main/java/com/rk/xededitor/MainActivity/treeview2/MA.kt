package com.rk.xededitor.MainActivity.treeview2

import android.app.Activity
import android.view.View
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils

class MA(val ctx: Activity,rootFolder: DocumentFile) {
    init {
        val recyclerView: RecyclerView by lazy {
            ctx.findViewById<RecyclerView>(R.id.recycler_view)
        }

        with(recyclerView) {
            visibility = View.VISIBLE
            val nodes = TreeViewAdapter.merge(rootFolder)
            layoutManager = LinearLayoutManager(ctx)
            setItemViewCacheSize(200)


            adapter = TreeViewAdapter(ctx, nodes).apply {
                setOnItemClickListener(object : OnItemClickListener {
                    override fun onItemClick(v: View, position: Int) {
                        val file = nodes[position].value
                        if(file.isFile){
                            (ctx as MainActivity).newEditor(file)
                            (ctx as MainActivity).onNewEditor()
                        }

                    }

                    override fun onItemLongClick(v: View, position: Int) {
                        rkUtils.ni(ctx)
                    }
                })
            }

        }
    }}