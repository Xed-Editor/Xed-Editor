package com.rk.xededitor.PluginClient

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.R

class PluginItem(val icon: Bitmap?, val title: String, val packageName: String,val description:String,val versionCode:Int)

class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
    val icon: ImageView = v.findViewById(R.id.icon)
    val title: TextView = v.findViewById(R.id.title)
    val description: TextView = v.findViewById(R.id.description)
}

class RepoPluginAdapter(private val itemClick:(PluginItem) -> Unit) : ListAdapter<PluginItem, ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.plugin_repo_item, parent, false)
        val holder = ViewHolder(view)
        view.setOnClickListener{
            itemClick.invoke(currentList[holder.adapterPosition])
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        item.icon?.let { holder.icon.setImageBitmap(item.icon) }
        holder.title.text = item.title
        holder.description.text = item.description

    }

    class DiffCallback : DiffUtil.ItemCallback<PluginItem>() {
        override fun areItemsTheSame(oldItem: PluginItem, newItem: PluginItem): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: PluginItem, newItem: PluginItem): Boolean {
            return oldItem.versionCode == newItem.versionCode && oldItem.description == newItem.description && oldItem.title == newItem.title && oldItem.packageName == newItem.packageName
        }
    }
}
