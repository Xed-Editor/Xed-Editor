package com.rk.xededitor.MainActivity.file

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.rk.filetree.widget.FileTree
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
            val paramsX =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )


            it.layoutParams = paramsX
            scrollView.addView(fileTree)
        }

        return scrollView
    }
}
