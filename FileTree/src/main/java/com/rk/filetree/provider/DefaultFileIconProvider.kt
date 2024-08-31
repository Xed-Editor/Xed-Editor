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
    private val java = ContextCompat.getDrawable(context, R.drawable.ic_language_java)
    private val html = ContextCompat.getDrawable(context, R.drawable.ic_language_html)
    private val kotlin = ContextCompat.getDrawable(context, R.drawable.ic_language_kotlin)
    private val python = ContextCompat.getDrawable(context, R.drawable.ic_language_python)
    private val xml = ContextCompat.getDrawable(context, R.drawable.ic_language_xml)
    private val js = ContextCompat.getDrawable(context, R.drawable.ic_language_js)
    private val c = ContextCompat.getDrawable(context, R.drawable.ic_language_c)
    private val cpp = ContextCompat.getDrawable(context, R.drawable.ic_language_cpp)
    private val json = ContextCompat.getDrawable(context, R.drawable.ic_language_json)
    private val css = ContextCompat.getDrawable(context, R.drawable.ic_language_css)
    private val markdown = ContextCompat.getDrawable(context, R.drawable.ic_language_markdown)
    private val csharp = ContextCompat.getDrawable(context, R.drawable.ic_language_csharp)

    override fun getIcon(node: Node<FileObject>): Drawable? {
        return if (node.value.isFile()) {
            when (node.value.getName().substringAfterLast('.', "")) {
                "java", "bsh" -> java
                "html" -> html
                "kt", "kts" -> kotlin
                "py" -> python
                "xml" -> xml
                "js" -> js
                "c" -> c
                "md" -> markdown
                "cpp", "h" -> cpp
                "json" -> json
                "css" -> css
                "cs" -> csharp
                else -> file
            }
        } else {
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
