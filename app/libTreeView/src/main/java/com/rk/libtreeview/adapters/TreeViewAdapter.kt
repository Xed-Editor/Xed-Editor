package com.rk.libtreeview.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rk.libtreeview.R
import com.rk.libtreeview.models.Node
import com.rk.libtreeview.callback.NodeDiffCallBack
import com.rk.libtreeview.holder.ViewHolder
import com.rk.libtreeview.interfaces.FileIconProvider
import com.rk.libtreeview.interfaces.FileObject
import com.rk.libtreeview.interfaces.onClickListener
import com.rk.libtreeview.models.TreeViewModel
import com.rk.libtreeview.providers.DefaultIconPovider





class TreeViewAdapter(val context: Context,val root: FileObject,val iconPovider: FileIconProvider = DefaultIconPovider(context)) : ListAdapter<Node<FileObject>, ViewHolder>(NodeDiffCallBack()) {
    private var listener: onClickListener? = null

    fun setListener(listener: onClickListener){
        this.listener = listener
    }




    private val icChevronRight = ResourcesCompat.getDrawable(context.resources, R.drawable.round_chevron_right_24, context.theme)
    private val icExpandMore = ResourcesCompat.getDrawable(context.resources, R.drawable.round_expand_more_24, context.theme)



    init {
        var files = root.listFiles()
        if (files == null){
            files = emptyArray()
        }
        submitList(files.map { fileObject -> Node(fileObject!!) })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =  LayoutInflater.from(context).inflate(R.layout.node_view, parent, false)
        val holder = ViewHolder(view)


        val clickListener = View.OnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                val clickedNode = getItem(adapterPosition)
                if (clickedNode.value.isDirectory) {
                    if (!clickedNode.isExpand) {


                        val tempData = currentList.toMutableList()
                        val index = tempData.indexOf(clickedNode)
                        var files = clickedNode.value.listFiles()
                        if (files == null) {
                            files = emptyArray()
                        }
                        val children = files.map { fileObject -> Node(fileObject!!) }
                        //val children = merge(clickedNode.value)
                        tempData.addAll(index + 1, children)
                        TreeViewModel.add(clickedNode, children)
                        clickedNode.isExpand = true
                        submitList(tempData)
                    } else {
                        val tempData = currentList.toMutableList()
                        val children = TreeViewModel.getChildren(clickedNode)
                        tempData.removeAll(children.toSet())
                        TreeViewModel.remove(clickedNode, clickedNode.child)
                        clickedNode.isExpand = false
                        submitList(tempData)
                    }
                    notifyItemChanged(adapterPosition) // Update the expand/collapse icon
                } else {
                    // It's a file, call the listener
                    listener?.onFileClick(clickedNode.value)
                }
            }
        }
        holder.itemView.setOnClickListener(clickListener)
        holder.expandView.setOnClickListener(clickListener)


        holder.itemView.setOnLongClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                //item long click
                println("long click")
            }
            true
        }

        holder.fileView.setPadding(0, 0, 0, 0)


        return holder
    }

    private fun dpToPx(dpValue: Float): Int {
        val scale: Float = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = getItem(position)
        val isDir = node.value.isDirectory
        val expandView = holder.expandView
        val fileView = holder.fileView


        //nodemap!![node] = holder.textView
        // Reset padding and margins to avoid accumulation
        holder.itemView.setPadding(0, 0, 0, 0)
        val layoutParams = fileView.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.setMargins(0, 0, 0, 0)
        fileView.layoutParams = layoutParams

        // Set padding based on node level
        holder.itemView.setPadding(node.level * dpToPx(17f), dpToPx(5f), 0, 0)

        if (isDir) {
            expandView.visibility = View.VISIBLE
            if (!node.isExpand) {
                expandView.setImageDrawable(icChevronRight)
            } else {
                expandView.setImageDrawable(icExpandMore)
            }
            fileView.setImageDrawable(iconPovider.getIconForFolder(node.value))
        } else {
            // Set margins for files
            layoutParams.setMargins(icChevronRight!!.intrinsicWidth + dpToPx(10f), 0, 0, 0)
            fileView.layoutParams = layoutParams
            expandView.visibility =
                View.GONE/*fileView.setPadding(icChevronRight!!.intrinsicWidth, 0, 0, 0)*/
            fileView.setImageDrawable(iconPovider.getIconForFile(node.value))

        }

        // holder.textView.text = " ${node.value.name}          "
        holder.textView.text = "  ${node.value.name}  "


    }
}