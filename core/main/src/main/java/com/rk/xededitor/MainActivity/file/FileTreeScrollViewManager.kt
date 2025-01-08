package com.rk.xededitor.MainActivity.file

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.rk.filetree.widget.DiagonalScrollView
import com.rk.filetree.widget.FileTree
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.rkUtils
import kotlin.properties.Delegates

@Suppress("NOTHING_TO_INLINE")
object FileTreeScrollViewManager {
    
    private inline fun dpToPx(dp: Int, density: Float): Int {
        return (dp * density).toInt()
    }

    private var fileTreeViewId by Delegates.notNull<Int>()

    fun getFileTreeParentScrollView(context: Context, fileTree: FileTree?): ViewGroup {
        fileTree?.let { fileTreeViewId = it.id }
        val density = context.resources.displayMetrics.density
        val linearLayout = LinearLayout(context)

        val params =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

        val scrollView = HorizontalScrollView(context).apply {
                layoutParams = params
                isHorizontalScrollBarEnabled = false
            }

        fileTree?.let {
            it.layoutParams = params
            linearLayout.addView(fileTree)
        }

        linearLayout.apply {
            layoutParams = params
            setPadding(0, 0, dpToPx(54, density), dpToPx(5, density))
            scrollView.addView(this)
        }

        scrollView.layoutParams = params
        return scrollView
    }
}
