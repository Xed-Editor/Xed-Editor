package com.rk.xededitor.plugin.ManagePluginActivity

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.rk.xededitor.R
import com.rk.xededitor.plugin.PluginManager
import com.rk.xededitor.rkUtils

class listAdapter(
  private val context: Activity,
  private val title: ArrayList<String>,
  private val description: ArrayList<String>,
  private val imgd: ArrayList<Drawable>
) : ArrayAdapter<String>(context, R.layout.custom_list, title) {
  
  override fun getView(position: Int, view: View?, parent: ViewGroup): View {
    val inflater = context.layoutInflater
    val rowView = inflater.inflate(R.layout.custom_list, null, true)
    
    val titleText: TextView = rowView.findViewById(R.id.title)
    val imageView: ImageView = rowView.findViewById(R.id.icon)
    val subtitleText: TextView = rowView.findViewById(R.id.description)
    val switch: MaterialSwitch = rowView.findViewById(R.id.material_switch)
    
    
    titleText.text = title[position]
    imageView.setImageDrawable(imgd[position])
    subtitleText.text = description[position]
    switch.isChecked = PluginManager.isPluginActive(context, description[position])
    
    switch.setOnCheckedChangeListener { _, isChecked ->
      PluginManager.activatePlugin(context, description[position], isChecked)
      rkUtils.toast(context, "Plugin will start on next restart")
    }
    
    return rowView
  }
}