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
        var root = ctx.getExternalFilesDir(null)!!


        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // sdcard root directory
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                root = Environment.getExternalStorageDirectory()
            }
        }
        val nodes = TreeViewAdapter.merge(rootFolder)
        layoutManager = LinearLayoutManager(ctx)

        adapter = TreeViewAdapter(ctx, nodes).apply {
            setOnItemClickListener(object : OnItemClickListener {
                override fun onItemClick(v: View, position: Int) {
                    // TODO
                    Toast.makeText(ctx, "onItemClick", Toast.LENGTH_SHORT).show()
                }

                override fun onItemLongClick(v: View, position: Int) {
                    // TODO
                    Toast.makeText(ctx, "onItemLongClick", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}}