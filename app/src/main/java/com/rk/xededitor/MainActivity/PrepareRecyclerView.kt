package com.rk.xededitor.MainActivity

import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.rk.xededitor.MainActivity.treeview2.DiagonalScrollView
import com.rk.xededitor.Settings.SettingsData

class PrepareRecyclerView(val activity: MainActivity) {

  companion object{
    val recyclerViewId = 428699
  }

  init {

    
    with(activity) {
      val holder = holder(this)
      
      val linearLayout = LinearLayout(this).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
          if (!SettingsData.getBoolean(activity,"diagonalScroll",false)){
            setPadding(0, 0, dpToPx(54), dpToPx(5))
          }else{
            setPadding(0, 0, dpToPx(54), dpToPx(60))
          }

        }
      }
      
      val recyclerView = RecyclerView(this).apply {
        id = recyclerViewId
        layoutParams = ViewGroup.MarginLayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
          if (!SettingsData.getBoolean(activity,"diagonalScroll",false)){
            setMargins(0, dpToPx(10), 0, 0)
          }else{
            setMargins(0, dpToPx(10), 0, dpToPx(60))
          }

        }
        visibility = View.GONE
      }
      
      linearLayout.addView(recyclerView)
      holder.addView(linearLayout)
      activity.binding.maindrawer.addView(holder)
    }
  }


  fun holder(activity: MainActivity) : ViewGroup{

    if (!SettingsData.getBoolean(activity,"diagonalScroll",false)){
      val hsv = HorizontalScrollView(activity).apply {
        layoutParams = ViewGroup.MarginLayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
      }
      return hsv
    }


   val dsv = DiagonalScrollView(activity).apply {
      layoutParams = ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
      ).apply {
        setMargins(0, dpToPx(10),0, 0)
      }
    }
    return dsv
  }

  fun dpToPx(dp: Int): Int {
    val density = activity.resources.displayMetrics.density
    return (dp * density).toInt()
  }
}
