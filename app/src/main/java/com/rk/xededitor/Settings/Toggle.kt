package com.rk.xededitor.Settings

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.rk.xededitor.R


class Toggle(val ctx: SettingsActivity, val enabled: Boolean) {
  private var name = ""
  private var listener: CompoundButton.OnCheckedChangeListener? = null
  private val inflater: LayoutInflater = LayoutInflater.from(ctx)
  var drawable = ContextCompat.getDrawable(ctx, R.drawable.settings)
  
  private val mainBody: LinearLayout = ctx.findViewById(R.id.mainBody)
  val v: View = inflater.inflate(R.layout.setting_toggle_layout, null)
  private val textView: TextView = v.findViewById(R.id.textView)
  val materialSwitch: MaterialSwitch = v.findViewById(R.id.materialSwitch)
  
  fun setName(name: String): Toggle {
    this.name = name
    return this
  }
  
  fun setListener(listener: CompoundButton.OnCheckedChangeListener?): Toggle {
    this.listener = listener
    return this
  }
  
  fun setDrawable(drawable: Drawable): Toggle {
    this.drawable = drawable
    return this
  }
  
  fun setDrawable(resourceId: Int): Toggle {
    this.drawable = ContextCompat.getDrawable(ctx, resourceId)
    return this
  }
  
  fun showToggle() : Toggle {
   
    materialSwitch.setChecked(enabled)
    textView.text = name
    materialSwitch.setOnCheckedChangeListener(listener)
    val imageView = v.findViewById<ImageView>(R.id.imageView)
    imageView.setImageDrawable(drawable)
    mainBody.addView(v)
    return this
  }
  
}