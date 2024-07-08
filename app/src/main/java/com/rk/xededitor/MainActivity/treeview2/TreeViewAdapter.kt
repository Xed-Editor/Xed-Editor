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
  private val cacheList = CacheList()
  
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
    return ViewHolder(view)
  }
  
  @SuppressLint("SetTextI18n")
  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val node = getItem(position)
    val isDir = node.value.isDirectory
    val expandView = holder.expandView
    val fileView = holder.fileView
    nodemap!![node] = holder.textView
    
    fileView.setPadding(0, 0, 0, 0)
    holder.itemView.setPaddingRelative(node.level * 35, 0, 0, 0)
    
    if (isDir) {
      if (!node.isExpand) {
        expandView.setImageDrawable(icChevronRight)
      } else {
        expandView.setImageDrawable(icExpandMore)
      }
      fileView.setImageDrawable(icFolder)
    } else {
      expandView.setImageDrawable(null)
      fileView.setPadding(icChevronRight!!.intrinsicWidth, 0, 0, 0)
      fileView.setImageDrawable(icFile)
    }
    
    holder.textView.text = " " + node.value.name + "          "
    holder.itemView.setOnClickListener {
      if (isDir) {
        var parent = node
        var child: List<Node<DocumentFile>>
        if (!node.isExpand) {
          val tempData = currentList.toMutableList()
          var index = position
          do {
            val key = parent.value
            val cachedChild = cacheList.get(key)
            child = cachedChild ?: merge(parent.value).also {
              cacheList.put(key, it)
            }
            tempData.addAll(index + 1, child)
            TreeView.add(parent, child)
            if (child.isNotEmpty()) {
              parent = child[0]
              index++
            }
          } while (child.size == 1 && child[0].value.isDirectory)
          submitList(tempData)
        } else {
          child = TreeView.getChildren(parent)
          val tempData = currentList.toMutableList()
          tempData.removeAll(child.toSet())
          TreeView.remove(parent, parent.child)
          submitList(tempData)
        }
      }
      listener?.onItemClick(it, currentList[position])
    }
    holder.itemView.setOnLongClickListener {
      listener?.onItemLongClick(it, currentList[position])
      true
    }
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
