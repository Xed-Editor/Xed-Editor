package com.rk.xededitor.MainActivity.treeview2

import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.Settings.SettingsData


object PrepareRecyclerView {
  
  //this object class is used to setup recycler view of treeview to use scrollview or diagonal scrollview
  const val recyclerViewId = 428699
  fun init(activity: MainActivity){
    val density = activity.resources.displayMetrics.density
    fun holder(activity: MainActivity): ViewGroup {
      if (!SettingsData.getBoolean(SettingsData.Keys.DIAGONAL_SCROLL, false)) {
        val hsv = HorizontalScrollView(activity).apply {
          layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
          )
          isHorizontalScrollBarEnabled = false
        }
        return hsv
      }
      
      
      val dsv = DiagonalScrollView(activity).apply {
        layoutParams = ViewGroup.MarginLayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
          setMargins(0, dpToPx(10, density), 0, 0)
        }
      }
      return dsv
    }
    
    with(activity) {
      
      val holder = holder(this)
      
      val linearLayout = LinearLayout(this).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
          if (!SettingsData.getBoolean(SettingsData.Keys.DIAGONAL_SCROLL, false)) {
            setPadding(0, 0, dpToPx(54,density), dpToPx(5,density))
          } else {
            setPadding(0, 0, dpToPx(54,density), dpToPx(60,density))
          }
          
        }
      }
      
      val recyclerView = RecyclerView(this).apply {
        id = recyclerViewId
        layoutParams = ViewGroup.MarginLayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
          if (!SettingsData.getBoolean(SettingsData.Keys.DIAGONAL_SCROLL, false)) {
            setMargins(0, dpToPx(10,density), 0, 0)
          } else {
            setMargins(0, dpToPx(10,density), 0, dpToPx(60,density))
          }
          
        }
        visibility = View.GONE
      }
      
      linearLayout.addView(recyclerView)
      holder.addView(linearLayout)
      activity.binding.maindrawer.addView(holder)
    }
  }
  
  private fun dpToPx(dp: Int,density:Float): Int {
    return (dp * density).toInt()
  }
}
