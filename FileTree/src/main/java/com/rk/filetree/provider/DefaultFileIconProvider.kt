package com.rk.filetree.provider

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.filetree.model.Node
import com.rk.libcommons.R
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
    private val csharp = ContextCompat.getDrawable(context, R.drawable.ic_language_csharp)
    private val unknown = ContextCompat.getDrawable(context,R.drawable.question_mark)
    private val bash = ContextCompat.getDrawable(context, R.drawable.bash)
    private val apk = ContextCompat.getDrawable(context, R.drawable.apkfile)
    private val archive = ContextCompat.getDrawable(context, R.drawable.archive)
    private val contract = ContextCompat.getDrawable(context, R.drawable.contract)
    private val text = ContextCompat.getDrawable(context, R.drawable.text)
    private val video = ContextCompat.getDrawable(context, R.drawable.video)
    private val audio = ContextCompat.getDrawable(context, R.drawable.music)
    private val image = ContextCompat.getDrawable(context, R.drawable.image)
    private val react = ContextCompat.getDrawable(context,R.drawable.react)
    private val rust = ContextCompat.getDrawable(context,R.drawable.rust)
    private val markdown = ContextCompat.getDrawable(context,R.drawable.markdown)

    override fun getIcon(node: Node<FileObject>): Drawable? {
        return if (node.value.isFile()) {
            when(node.value.getName()){
                "contract.sol", "LICENSE","NOTICE","NOTICE.txt" -> contract
                "gradlew" -> bash

                else -> when (node.value.getName().substringAfterLast('.', "")) {
                    "java", "bsh" -> java
                    "html" -> html
                    "kt", "kts" -> kotlin
                    "py" -> python
                    "xml" -> xml
                    "js" -> js
                    "c","h" -> c
                    "cpp","hpp" -> cpp
                    "json" -> json
                    "css" -> css
                    "cs" -> csharp
                    "sh","bash","zsh","bat" -> bash
                    "apk","xapk","apks" -> apk
                    "zip","rar","7z","tar.gz","tar.bz2","tar" -> archive
                    "md" -> markdown
                    "txt" -> text
                    "mp3","wav","ogg","flac" -> audio
                    "mp4","mov","avi","mkv" -> video
                    "jpg","jpeg","png","gif","bmp" -> image
                    "rs" -> rust
                    "jsx" -> react
                    else -> file
                }
            }

        } else if (node.value.isDirectory()) {
           folder
        }else{
           unknown
        }
    }

    override fun getChevronRight(): Drawable? {
        return chevronRight
    }

    override fun getExpandMore(): Drawable? {
        return expandMore
    }
}
