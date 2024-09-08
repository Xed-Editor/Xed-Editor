package com.rk.xededitor.PluginClient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.rk.libPlugin.server.Plugin
import com.rk.libPlugin.server.PluginUtils.isPluginActive
import com.rk.libPlugin.server.PluginUtils.setPluginActive
import com.rk.xededitor.R
import java.io.File
import java.io.IOException

class InstalledPluginListAdapter(private var context: Context, items: List<Plugin?>?) :
    ArrayAdapter<Plugin?>(
        context, 0, items!!
    ) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val plugin = getItem(position)

        if (convertView == null) {
            convertView =
                LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false)
        }


        val title = convertView!!.findViewById<TextView>(R.id.title)
        checkNotNull(plugin)
        title.text = plugin.info.name

        val description = convertView.findViewById<TextView>(R.id.description)
        description.text = plugin.info.description

        val imagePath = plugin.info.icon
        val file = File(plugin.pluginHome, imagePath)
        val bitmap: Bitmap?
        try {
            bitmap = BitmapFactory.decodeFile(file.canonicalPath)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        if (bitmap != null) {
            val imageView = convertView.findViewById<ImageView>(R.id.icon)
            imageView.setImageBitmap(bitmap)
        }

        val materialSwitch = convertView.findViewById<MaterialSwitch>(R.id.toggle)

        materialSwitch.isChecked = isPluginActive(context, plugin.info.packageName, false)

        materialSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            setPluginActive(
                context, plugin.info.packageName, isChecked
            )
        }
        return convertView
    }
}
