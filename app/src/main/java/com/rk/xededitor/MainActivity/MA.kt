package com.rk.xededitor.MainActivity

import android.app.Activity
import android.os.Build
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils

class MA(val ctx: Activity,rootFolder: DocumentFile) {
init {
    val recyclerView: RecyclerView by lazy {
        ctx.findViewById<RecyclerView>(R.id.recycler_view)
    }

    with(recyclerView) {
        visibility = View.VISIBLE
        setItemAnimator(null)
        val nodes = TreeViewAdapter.merge(rootFolder)
        layoutManager = LinearLayoutManager(ctx)

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