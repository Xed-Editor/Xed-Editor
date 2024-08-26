package com.rk.xededitor.MainActivity.treeview2

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.R
import java.io.File

interface OnItemClickListener {
    fun onItemClick(v: View, node: Node<File>)
    fun onItemLongClick(v: View, node: Node<File>)
}

class TreeViewAdapter(
    private val recyclerView: RecyclerView, val context: Context, val root: File
) : ListAdapter<Node<File>, TreeViewAdapter.ViewHolder>(NodeDiffCallback()) {

    private val icFile = ResourcesCompat.getDrawable(
        context.resources, R.drawable.outline_insert_drive_file_24, context.theme
    )
    private val icFolder = ResourcesCompat.getDrawable(
        context.resources, R.drawable.outline_folder_24, context.theme
    )
    private val icChevronRight = ResourcesCompat.getDrawable(
        context.resources, R.drawable.round_chevron_right_24, context.theme
    )
    private val icExpandMore = ResourcesCompat.getDrawable(
        context.resources, R.drawable.round_expand_more_24, context.theme
    )

    private var listener: OnItemClickListener? = null
   
    
    fun removeFile(file: File) {
        val tempData = currentList.toMutableList()
        var nodetoremove: Node<File>? = null

        for (node in tempData) {
            if (node.value == file) {
                nodetoremove = node
                break
            }
        }
        if (file.isFile) {
            tempData.removeAt(tempData.indexOf(nodetoremove))
        } else {
            val children = TreeViewModel.getChildren(nodetoremove!!)
            tempData.removeAll(children.toSet())
            TreeViewModel.remove(nodetoremove, nodetoremove.child)
            nodetoremove.isExpand = false
            tempData.removeAt(tempData.indexOf(nodetoremove))
        }

        submitList(tempData)
    }

    fun renameFile(oldFile: File, newFile: File) {
        val tempData = currentList.toMutableList()
        var nodeToRename: Node<File>? = null

        for (node in tempData) {
            if (node.value == oldFile) {
                nodeToRename = node
                break
            }
        }

        if (nodeToRename == null) {
            return
        }


        val index = tempData.indexOf(nodeToRename)
        if (index == -1) {
            return
        }
        tempData.removeAt(index)

        if (nodeToRename.isExpand) {
            val children = TreeViewModel.getChildren(nodeToRename)
            tempData.removeAll(children.toSet())
            TreeViewModel.remove(nodeToRename, nodeToRename.child)
        }

        val newNode = Node(newFile)

        tempData.add(index, newNode)

        if (nodeToRename.isExpand) {
            val newChildren = merge(newFile)
            tempData.addAll(index + 1, newChildren)
            TreeViewModel.add(newNode, newChildren)
            newNode.isExpand = true
        }

        submitList(tempData)
    }


    fun newFile(file: File) {
        //List<Node<File>>
        val tempData = currentList.toMutableList()
        var xnode: Node<File>? = null
        for (node in tempData) {
            if (node.value == file) {
                xnode = node
                break
            }
        }

        val cache = merge(file)
        

        val children1 = TreeViewModel.getChildren(xnode!!)
        tempData.removeAll(children1.toSet())
        TreeViewModel.remove(xnode, xnode.child)
        xnode.isExpand = false


        val index = tempData.indexOf(xnode)

        //val children = merge(clickedNode.value)
        tempData.addAll(index + 1, cache)
        TreeViewModel.add(xnode, cache)
        xnode.isExpand = true


        submitList(tempData)
    }

    companion object {
        @JvmStatic
        fun merge(root: File): MutableList<Node<File>> {
            val xlist = root.listFiles() ?: return emptyList<Node<File>>().toMutableList()
            val list = xlist.toMutableList()
            val dirs = list.filter { it.isDirectory }.sortedBy { it.name }
            val files = (list - dirs.toSet()).sortedBy { it.name }
            return (dirs + files).map { Node(it) }.toMutableList()
        }

        @JvmStatic
        var thread: Thread? = null

        @JvmStatic
        fun stopThread() {
            thread?.interrupt()
        }

    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.listener = listener
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(context).inflate(R.layout.recycler_view_item, parent, false)
        val holder = ViewHolder(view)


        val clickListener = View.OnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                val clickedNode = getItem(adapterPosition)
                if (clickedNode.value.isDirectory) {
                    if (!clickedNode.isExpand) {
                        // Expand the directory
                        if (animator != null) {
                            recyclerView.itemAnimator = animator
                        }
                        val tempData = currentList.toMutableList()
                        val index = tempData.indexOf(clickedNode)
                        
                        val children = merge(clickedNode.value)
                        //val children = merge(clickedNode.value)
                        tempData.addAll(index + 1, children)
                        TreeViewModel.add(clickedNode, children)
                        clickedNode.isExpand = true
                        submitList(tempData)
                    } else {
                        // Collapse the directory
                        recyclerView.itemAnimator = null
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
                    listener?.onItemClick(it, clickedNode)
                }
            }
        }


        holder.itemView.setOnClickListener(clickListener)
        holder.expandView.setOnClickListener(clickListener)


        holder.itemView.setOnLongClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener?.onItemLongClick(it, getItem(adapterPosition))
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

    private val animator = recyclerView.itemAnimator

    @SuppressLint("SetTextI18n")
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
            fileView.setImageDrawable(icFolder)
        } else {
            // Set margins for files
            layoutParams.setMargins(icChevronRight!!.intrinsicWidth + dpToPx(10f), 0, 0, 0)
            fileView.layoutParams = layoutParams
            expandView.visibility =
                View.GONE/*fileView.setPadding(icChevronRight!!.intrinsicWidth, 0, 0, 0)*/
            fileView.setImageDrawable(icFile)

        }

        // holder.textView.text = " ${node.value.name}          "
        holder.textView.text = "  ${node.value.name}  "

    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val expandView: ImageView = v.findViewById(R.id.expand)
        val fileView: ImageView = v.findViewById(R.id.file_view)
        val textView: TextView = v.findViewById(R.id.text_view)
    }

    class NodeDiffCallback : DiffUtil.ItemCallback<Node<File>>() {
        override fun areItemsTheSame(
            oldItem: Node<File>, newItem: Node<File>
        ): Boolean {
            return oldItem.value.path == newItem.value.path
        }

        override fun areContentsTheSame(
            oldItem: Node<File>, newItem: Node<File>
        ): Boolean {
            return oldItem == newItem
        }
    }
}
