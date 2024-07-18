package com.rk.xededitor.MainActivity.treeview2

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.R
import java.util.LinkedList
import java.util.Queue
import java.util.Stack
import java.util.concurrent.locks.ReentrantLock

interface OnItemClickListener {
  fun onItemClick(v: View, node: Node<DocumentFile>)
  fun onItemLongClick(v: View, node: Node<DocumentFile>)
}

class TreeViewAdapter(
  val recyclerView: RecyclerView,
  val context: Context
) : ListAdapter<Node<DocumentFile>, TreeViewAdapter.ViewHolder>(NodeDiffCallback()) {
  
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
  private var cachedViews = Stack<View>()
  private val cacheList = FileCacheMap<DocumentFile,List<Node<DocumentFile>>>()
  
  init {
    thread = Thread {
      nodemap = HashMap()
      val lock = ReentrantLock()
      val localViews = Stack<View>()
      
      if (!Thread.currentThread().isInterrupted) {
        lock.lock()
        cachedViews = localViews
        lock.unlock()
      }
      
      val queue: Queue<List<Node<DocumentFile>>> = LinkedList()
      queue.add(currentList)
      while (!Thread.currentThread().isInterrupted && queue.isNotEmpty()) {
        queue.poll()?.forEach { node ->
          if (!Thread.currentThread().isInterrupted) {
            val file = node.value
            if (file.isDirectory) {
              val childList = merge(file)
              lock.lock()
              cacheList.put(file, childList)
              lock.unlock()
              queue.add(childList)
            }
          }
        }
      }
      val inflater = LayoutInflater.from(context)
      for (i in 0 until 100) {
        if (Thread.currentThread().isInterrupted) {
          break
        }
        val view = inflater.inflate(R.layout.recycler_view_item, null)
        localViews.push(view)
      }
    }.also { it.start() }
  }
  
  companion object {
    @JvmStatic
    fun merge(root: DocumentFile): MutableList<Node<DocumentFile>> {
      val list = root.listFiles().toMutableList()
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
    
    @JvmStatic
    var nodemap: HashMap<Node<DocumentFile>, View>? = null
  }
  
  fun setOnItemClickListener(listener: OnItemClickListener?) {
    this.listener = listener
  }
  
  
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view: View = if (cachedViews.isEmpty()) {
      LayoutInflater.from(context)
        .inflate(R.layout.recycler_view_item, parent, false)
    } else {
      cachedViews.pop()
    }
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
            val cachedChild = cacheList.get(clickedNode.value)
            val children = cachedChild ?: merge(clickedNode.value).also {
              cacheList.put(clickedNode.value, it)
            }
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
  
  fun dpToPx(dpValue: Float): Int {
    val scale: Float = context.resources.displayMetrics.density
    return (dpValue * scale + 0.5f).toInt()
  }
  
  val animator = recyclerView.itemAnimator
  
  @SuppressLint("SetTextI18n")
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val node = getItem(position)
    val isDir = node.value.isDirectory
    val expandView = holder.expandView
    val fileView = holder.fileView
    nodemap!![node] = holder.textView
    // Reset padding and margins to avoid accumulation
    holder.itemView.setPadding(0, 0, 0, 0)
    val layoutParams = fileView.layoutParams as ViewGroup.MarginLayoutParams
    layoutParams.setMargins(0, 0, 0, 0)
    fileView.layoutParams = layoutParams

    // Set padding based on node level
    holder.itemView.setPadding(node.level * 35, 0, 0, 0)

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
        layoutParams.setMargins(icChevronRight!!.intrinsicWidth+dpToPx(10f), 0, 0, 0)
        fileView.layoutParams = layoutParams
        expandView.visibility = View.GONE
        /*fileView.setPadding(icChevronRight!!.intrinsicWidth, 0, 0, 0)*/
        fileView.setImageDrawable(icFile)
        
    }

    holder.textView.text = " ${node.value.name}          "
}
  
  class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
    val expandView: ImageView = v.findViewById(R.id.expand)
    val fileView: ImageView = v.findViewById(R.id.file_view)
    val textView: TextView = v.findViewById(R.id.text_view)
  }
  
  class NodeDiffCallback : DiffUtil.ItemCallback<Node<DocumentFile>>() {
    override fun areItemsTheSame(
      oldItem: Node<DocumentFile>,
      newItem: Node<DocumentFile>
    ): Boolean {
      return oldItem.value.uri == newItem.value.uri
    }
    
    override fun areContentsTheSame(
      oldItem: Node<DocumentFile>,
      newItem: Node<DocumentFile>
    ): Boolean {
      return oldItem == newItem
    }
  }
}
