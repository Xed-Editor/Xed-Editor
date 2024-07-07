/*
 * Copyright © 2022 Github Lzhiyong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rk.xededitor.MainActivity.treeview2

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.R
import java.util.LinkedList
import java.util.Queue
import java.util.Stack
import java.util.concurrent.locks.ReentrantLock

interface OnItemClickListener {
  fun onItemClick(v: View, position: Int)
  
  fun onItemLongClick(v: View, position: Int)
}


class TreeViewAdapter(
  val context: Context, var data: MutableList<Node<DocumentFile>>
) : RecyclerView.Adapter<TreeViewAdapter.ViewHolder>() {
  
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
    nodemap = HashMap()
    
    thread = Thread {
      val lock = ReentrantLock()
      val localViews = Stack<View>()
      
      
      
      if (!Thread.currentThread().isInterrupted) {
        lock.lock()
        cachedViews = localViews
        lock.unlock()
      }
      
      
      val mData: MutableList<Node<DocumentFile>> = data.toMutableList()
      val queue: Queue<List<Node<DocumentFile>>> = LinkedList()
      queue.add(mData)
      while (!Thread.currentThread().isInterrupted && queue.isNotEmpty()) {
        queue.poll()?.forEach { node ->
          if (!Thread.currentThread().isInterrupted) {
            val file = node.value
            if (file.isDirectory) {
              // Merge and process child directories
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
        if (Thread.currentThread().isInterrupted){
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
      // child files
      val list = root.listFiles().toMutableList()
      // dir with sorted
      val dirs = list.filter { it.isDirectory }.sortedBy { it.name }
      // file with sorted
      val files = (list - dirs.toSet()).sortedBy { it.name }
      // file to node
    
      return (dirs + files).map { Node(it) }.toMutableList()
    }
    
    
    @JvmStatic
    var thread: Thread? = null
    
    @JvmStatic
    fun stopThread() {
      if (thread != null) {
        thread!!.interrupt()
      }
      
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
  
  
  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    
    val node = data[position]
    val isDir = node.value.isDirectory
    val expandView = holder.expandView
    val fileView = holder.fileView
    //if (!nodemap?.containsKey(node)!!) {
      nodemap!![node] = holder.textView
    //}
    
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
      // non-directory not show the expand icon
      expandView.setImageDrawable(null)
      // padding
      fileView.setPadding(icChevronRight!!.intrinsicWidth, 0, 0, 0)
      fileView.setImageDrawable(icFile)
    }
    
    holder.textView.text = " "+node.value.name+"          "
    holder.itemView.setOnClickListener {
      if (isDir) {
        var parent = node
        var child: List<Node<DocumentFile>>
        // expand and collapsed
        if (!node.isExpand) {
          var index = position
          var count = 0
          
          do {
            val key = parent.value
            val cachedChild = cacheList.get(key)
            
            if (cachedChild != null) {
              child = cachedChild
            } else {
              // Cache miss: Calculate child list and store in cache
              child = merge(parent.value)
              cacheList.put(key, child)
            }
            
            
            data.addAll(index + 1, child)
            TreeView.add(parent, child)
            
            if (child.isNotEmpty()) {
              parent = child[0]
              count += child.size
              index++
            }
          } while (child.size == 1 && child[0].value.isDirectory)
          
          
          // refresh data
          notifyItemRangeInserted(position + 1, count)
        } else {
          child = TreeView.getChildren(parent)
          data.removeAll(child.toSet())
          TreeView.remove(parent, parent.child)
          // refresh data
          notifyItemRangeRemoved(position + 1, child.size)
        }
        
        // refresh data at position
        notifyItemChanged(position)
      }
      
      // callback
      listener?.onItemClick(it, position)
    }
    holder.itemView.setOnLongClickListener {
      // callback
      listener?.onItemLongClick(it, position)
      return@setOnLongClickListener true
    }
    
    
  }
  
  override fun getItemViewType(position: Int) = position
  
  override fun getItemCount() = data.size
  
  class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
    val expandView: ImageView = v.findViewById(R.id.expand)
    val fileView: ImageView = v.findViewById(R.id.file_view)
    val textView: TextView = v.findViewById(R.id.text_view)
  }
}

