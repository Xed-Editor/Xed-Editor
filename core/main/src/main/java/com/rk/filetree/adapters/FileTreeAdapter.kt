package com.rk.filetree.adapters

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rk.filetree.interfaces.FileClickListener
import com.rk.filetree.interfaces.FileIconProvider
import com.rk.filetree.interfaces.FileLongClickListener
import com.rk.file_wrapper.FileObject
import com.rk.filetree.model.Node
import com.rk.filetree.model.TreeViewModel
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.rk.filetree.util.sort
import com.rk.filetree.widget.FileTree
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
    val expandView: ImageView = v.findViewById(R.id.expand)
    val fileView: ImageView = v.findViewById(R.id.file_view)
    val textView: TextView = v.findViewById(R.id.text_view)
}

class NodeDiffCallback : DiffUtil.ItemCallback<Node<FileObject>>() {
    override fun areItemsTheSame(oldItem: Node<FileObject>, newItem: Node<FileObject>): Boolean {
        return oldItem.value.getAbsolutePath() == newItem.value.getAbsolutePath()
    }

    override fun areContentsTheSame(oldItem: Node<FileObject>, newItem: Node<FileObject>): Boolean {
        return areItemsTheSame(oldItem, newItem) && oldItem.isExpand == newItem.isExpand
    }
}

class FileTreeAdapter(private val context: Context, val fileTree: FileTree) :
    ListAdapter<Node<FileObject>, ViewHolder>(NodeDiffCallback()) {

    var onClickListener: FileClickListener? = null
    var onLongClickListener: FileLongClickListener? = null
    var iconProvider: FileIconProvider? = null
    private var isBusy = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View =
            LayoutInflater.from(context).inflate(R.layout.recycler_view_item, parent, false)
        val holder = ViewHolder(view)

        val clickListener =
            View.OnClickListener {
                val adapterPosition = holder.adapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val clickedNode = getItem(adapterPosition)

                    if (clickedNode.value.isDirectory() && isBusy.not()) {
                        if (!clickedNode.isExpand) {
                            it.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                                expandNode(clickedNode)
                                isBusy = false
                            }
                        } else {
                            collapseNode(clickedNode)
                            isBusy = false
                        }
                        notifyItemChanged(adapterPosition)
                    }

                    onClickListener?.onClick(clickedNode)
                }
            }

        holder.itemView.setOnClickListener(clickListener)

        holder.itemView.setOnLongClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                val clickedNode = getItem(adapterPosition)
                onLongClickListener?.onLongClick(clickedNode)
            }
            true
        }

        holder.expandView.setOnClickListener(clickListener)
        holder.fileView.setPadding(0, 0, 0, 0)
        return holder
    }

    //parent file
    suspend fun newFile(file: FileObject) {
        isBusy = true
        val tempData = currentList.toMutableList()

        var xnode: Node<FileObject>? = null
        for (node in tempData) {
            if (node.value == file) {
                xnode = node
                break
            }
        }

        val cache = sort(file)
        val children1 = TreeViewModel.getChildren(xnode!!)

        tempData.removeAll(children1.toSet())
        TreeViewModel.remove(xnode, xnode.child)
        xnode.isExpand = false

        val index = tempData.indexOf(xnode)

        tempData.addAll(index + 1, cache)
        TreeViewModel.add(xnode, cache)
        xnode.isExpand = true

        submitList(tempData)
        isBusy = false
    }

    fun removeFile(file: FileObject) {
        val tempData = currentList.toMutableList()
        var nodetoremove: Node<FileObject>? = null

        for (node in tempData) {
            if (node.value == file) {
                nodetoremove = node
                break
            }
        }

        if (nodetoremove != null) {
            isBusy = true
            if (file.isFile()) {
                val index = tempData.indexOf(nodetoremove)
                if (index != -1) {
                    tempData.removeAt(index)
                }
            } else {
                val children = TreeViewModel.getChildren(nodetoremove)
                tempData.removeAll(children.toSet())
                TreeViewModel.remove(nodetoremove, nodetoremove.child)
                nodetoremove.isExpand = false

                val index = tempData.indexOf(nodetoremove)
                if (index != -1) {
                    tempData.removeAt(index)
                }
            }

            submitList(tempData)
            isBusy = false
        }
    }

    fun renameFile(child: FileObject, newFile: FileObject) {
        isBusy = true
        val tempData = currentList.toMutableList()
        for (node in tempData) {
            if (node.value == child) {
                node.value = newFile
                submitList(tempData)

                val position = tempData.indexOf(node)
                if (position != -1) {
                    notifyItemChanged(position)
                }
                break
            }
        }
        isBusy = false
    }

    private fun dpToPx(dpValue: Float): Int {
        val scale: Float = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }


    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = getItem(position)
        val isDir = node.value.isDirectory()
        val expandView = holder.expandView
        val fileView = holder.fileView

        // Reset padding and margins to avoid accumulation
        holder.itemView.setPadding(0, dpToPx(5f), 0, 0)

        // Set padding based on node level
        //holder.itemView.setPadding(node.level * dpToPx(17f), dpToPx(5f), 0, 0)
        fileView.setImageDrawable(iconProvider?.getIcon(node))

        val icChevronRight = iconProvider?.getChevronRight()


        val targetRotation = when {
            node.isExpand -> 90f
            else -> 0f
        }

        val currentRotation = expandView.rotation
        if (currentRotation != targetRotation) {
            val rotationAnimator = ObjectAnimator.ofFloat(expandView, "rotation", currentRotation, targetRotation).apply {
                duration = 300
                interpolator = LinearInterpolator()
            }
            expandView.rotation = targetRotation
            rotationAnimator.setDuration(300)
            rotationAnimator.start()
        }


        if (isDir) {
            expandView.visibility = View.VISIBLE
            val layoutParams = holder.itemView.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins(node.level * dpToPx(17f), 0, 0, 0)
            holder.itemView.layoutParams = layoutParams
        } else {
            val layoutParams = holder.itemView.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins((node.level * dpToPx(17f))+icChevronRight!!.intrinsicWidth+dpToPx(10f), 0, 0, 0)
            holder.itemView.layoutParams = layoutParams
            expandView.visibility = View.GONE
        }

        //todo print its width and set max widht to the filetree
        holder.textView.text = (node.value.getName().ifBlank {
            "invalid"
        })+"                                                    "
        


    }

    suspend fun expandNode(clickedNode: Node<FileObject>){
        val tempData = currentList.toMutableList()
        val index = tempData.indexOf(clickedNode)
        val children = withContext(Dispatchers.IO){
            sort(clickedNode.value)
        }
        tempData.addAll(index + 1, children)
        TreeViewModel.add(clickedNode, children)
        clickedNode.isExpand = true
        submitList(tempData)
        notifyItemChanged(index)
    }


    private fun collapseNode(clickedNode: Node<FileObject>) {
        val tempData = currentList.toMutableList()
        val index = tempData.indexOf(clickedNode)
        val children = TreeViewModel.getChildren(clickedNode)
        tempData.removeAll(children.toSet())
        TreeViewModel.remove(clickedNode, clickedNode.child)
        clickedNode.isExpand = false
        submitList(tempData)
        notifyItemChanged(index)
    }
}
