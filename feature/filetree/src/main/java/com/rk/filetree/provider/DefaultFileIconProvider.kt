package com.rk.filetree.provider

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.filetree.interfaces.FileIconProvider
import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node
import com.rk.libcommons.R

class DefaultFileIconProvider(context: Context) : FileIconProvider {
    private val file = ContextCompat.getDrawable(context, R.drawable.file)
    private val folder = ContextCompat.getDrawable(context, R.drawable.folder)
    private val chevronRight = ContextCompat.getDrawable(context, R.drawable.ic_chevron_right)
    private val expandMore = ContextCompat.getDrawable(context, R.drawable.round_expand_more_24)

    // Lazy initialization for drawables to save memory
    private val java by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_java) }
    private val html by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_html) }
    private val kotlin by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_kotlin) }
    private val python by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_python) }
    private val xml by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_xml) }
    private val js by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_js) }
    private val c by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_c) }
    private val cpp by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_cpp) }
    private val json by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_json) }
    private val css by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_css) }
    private val csharp by lazy { ContextCompat.getDrawable(context, R.drawable.ic_language_csharp) }
    private val unknown by lazy { ContextCompat.getDrawable(context, R.drawable.question_mark) }
    private val bash by lazy { ContextCompat.getDrawable(context, R.drawable.bash) }
    private val apk by lazy { ContextCompat.getDrawable(context, R.drawable.apkfile) }
    private val archive by lazy { ContextCompat.getDrawable(context, R.drawable.archive) }
    private val contract by lazy { ContextCompat.getDrawable(context, R.drawable.contract) }
    private val text by lazy { ContextCompat.getDrawable(context, R.drawable.text) }
    private val video by lazy { ContextCompat.getDrawable(context, R.drawable.video) }
    private val audio by lazy { ContextCompat.getDrawable(context, R.drawable.music) }
    private val image by lazy { ContextCompat.getDrawable(context, R.drawable.image) }
    private val react by lazy { ContextCompat.getDrawable(context, R.drawable.react) }
    private val rust by lazy { ContextCompat.getDrawable(context, R.drawable.rust) }
    private val markdown by lazy { ContextCompat.getDrawable(context, R.drawable.markdown) }

    override fun getIcon(node: Node<FileObject>): Drawable? {
        return if (node.value.isFile()) {
            when (node.value.getName()) {
                "contract.sol",
                "LICENSE",
                "NOTICE",
                "NOTICE.txt" -> contract
                "gradlew" -> bash

                else ->
                    when (node.value.getName().substringAfterLast('.', "")) {
                        "java",
                        "bsh" -> java
                        "html" -> html
                        "kt",
                        "kts" -> kotlin
                        "py" -> python
                        "xml" -> xml
                        "js" -> js
                        "c",
                        "h" -> c
                        "cpp",
                        "hpp" -> cpp
                        "json" -> json
                        "css",
                        "sass",
                        "scss" -> css
                        "cs" -> csharp
                        "sh",
                        "bash",
                        "zsh",
                        "bat" -> bash
                        "apk",
                        "xapk",
                        "apks" -> apk
                        "zip",
                        "rar",
                        "7z",
                        "gz",
                        "bz2",
                        "tar",
                        "xz" -> archive
                        "md" -> markdown
                        "txt" -> text
                        "mp3",
                        "wav",
                        "ogg",
                        "flac" -> audio
                        "mp4",
                        "mov",
                        "avi",
                        "mkv" -> video
                        "jpg",
                        "jpeg",
                        "png",
                        "gif",
                        "bmp" -> image
                        "rs" -> rust
                        "jsx" -> react
                        else -> file
                    }
            }
        } else if (node.value.isDirectory()) {
            folder
        } else {
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
