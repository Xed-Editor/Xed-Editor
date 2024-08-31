package com.rk.filetree.provider

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.filetree.model.Node
import com.rk.filetree.R
import com.rk.filetree.interfaces.FileIconProvider
import com.rk.filetree.interfaces.FileObject

class DefaultFileIconProvider(context: Context) : FileIconProvider {
    private val file = ContextCompat.getDrawable(context, R.drawable.file)
    private val folder = ContextCompat.getDrawable(context, R.drawable.folder)
    private val chevronRight = ContextCompat.getDrawable(context, R.drawable.ic_chevron_right)
    private val expandMore = ContextCompat.getDrawable(context, R.drawable.round_expand_more_24)

    override fun getIcon(node: Node<FileObject>): Drawable? {
        return if (node.value.isFile()){
            when (node.value.getName().substringAfterLast('.', "")) {
                "java", "bsh" -> ContextCompat.getDrawable(context, R.drawable.ic_language_java)
                "html" -> ContextCompat.getDrawable(context, R.drawable.ic_language_html)
                "kt", "kts" -> ContextCompat.getDrawable(context, R.drawable.ic_language_kotlin)
                "py" -> ContextCompat.getDrawable(context, R.drawable.ic_language_python)
                "xml" -> ContextCompat.getDrawable(context, R.drawable.ic_language_xml)
                "js" -> ContextCompat.getDrawable(context, R.drawable.ic_language_js)
                "c" -> ContextCompat.getDrawable(context, R.drawable.ic_language_c)
                "cpp", "h" -> ContextCompat.getDrawable(context, R.drawable.ic_language_cpp)
                "json" -> ContextCompat.getDrawable(context, R.drawable.ic_language_json)
                "css" -> ContextCompat.getDrawable(context, R.drawable.ic_language_css)
                "cs" -> ContextCompat.getDrawable(context, R.drawable.ic_language_csharp)
                else -> file
            }
        }else{
            folder
        }
    }

    override fun getChevronRight(): Drawable? {
        return chevronRight
    }

    override fun getExpandMore(): Drawable? {
        return expandMore
    }
}