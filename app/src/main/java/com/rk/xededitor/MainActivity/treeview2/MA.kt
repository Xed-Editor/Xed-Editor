package com.rk.xededitor.MainActivity.treeview2

import android.app.Activity
import android.view.View
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
                            ctx.newEditor(file)
                            ctx.onNewEditor()
                            if(!SettingsData.getBoolean(ctx,"keepDrawerLocked",false)){
                                val timer = Timer()
                                val timerTask: TimerTask = object : TimerTask() {
                                    override fun run() {
                                        ctx.runOnUiThread{
                                            ctx.binding.drawerLayout.close()
                                        }

                                        timer.cancel()
                                    }

                                }
                                timer.schedule(timerTask,0,500)
                            }
                        }


                    }



                    override fun onItemLongClick(v: View, position: Int) {
                        val file = nodes[position].value
                        TreeViewAdapter.nodemap?.get(nodes[position])?.let {
                            HandleFileActions(ctx,rootFolder,file, it)
                        }
                    }
                })
            }


        }
    }
}