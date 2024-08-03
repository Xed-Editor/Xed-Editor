package com.rk.xededitor.theme

import android.content.Context
import android.content.res.Resources
import com.rk.xededitor.R
import com.rk.xededitor.Settings.SettingsData


object ThemeManager {


  fun getSelectedTheme(context: Context) : Int{
    return SettingsData.getSetting(context,"selected_theme",R.style.selectable_Berry.toString()).toInt()
  }

  fun setSelectedTheme(context: Context,themeId: Int){
    SettingsData.setSetting(context,"selected_theme",themeId.toString())
  }

  fun applyTheme(context: Context){
    setTheme(context, getSelectedTheme(context))
  }

  fun setTheme(context: Context, themeId: Int) {
    context.setTheme(themeId)
    setSelectedTheme(context,themeId)
  }

  private const val theme_prefix = "selectable_"

  val idMap = HashMap<String,Int>()


  fun getThemes(context: Context): List<Pair<String, Int>> {
    val stylesClass = R.style::class.java
    val fields = stylesClass.declaredFields
    val themes = mutableListOf<Pair<String, Int>>()

    for (field in fields) {
      try {
        val resourceId = field.getInt(null)
        val resourceName = context.resources.getResourceEntryName(resourceId)
        if (!resourceName.startsWith(theme_prefix)) {
          continue
        }
        val finalname = resourceName.removePrefix(theme_prefix)

        idMap[finalname] = resourceId

        themes.add(Pair(finalname, resourceId))
      } catch (e: IllegalAccessException) {
        e.printStackTrace()
      }
    }

    return themes
  }

  fun getCurrentTheme(context: Context): Resources.Theme? {
    return context.theme
  }

  fun getCurrentThemeId(context: Context): Int {
    val attrs = intArrayOf(android.R.attr.theme)
    val typedArray = getCurrentTheme(context)!!.obtainStyledAttributes(attrs)
    val themeId = typedArray.getResourceId(0, 0)
    typedArray.recycle()
    return themeId
  }
}