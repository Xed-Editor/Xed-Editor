package com.rk.filetree.provider

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.rk.filetree.interfaces.FileIconProvider
import com.rk.filetree.model.Node
import com.rk.resources.drawables

class DefaultFileIconProvider(context: Context) : FileIconProvider {
    private val file = ContextCompat.getDrawable(context, drawables.file)
    private val folder = ContextCompat.getDrawable(context, drawables.folder)
    private val unknown by lazy { ContextCompat.getDrawable(context, drawables.unknown_document) }
    private val fileSymlink by lazy { ContextCompat.getDrawable(context, drawables.file_symlink) }

    private val chevronRight = ContextCompat.getDrawable(context, drawables.chevron_right)

    // Lazy initialization for drawables to save memory
    private val java by lazy { ContextCompat.getDrawable(context, drawables.java) }
    private val html by lazy { ContextCompat.getDrawable(context, drawables.ic_language_html) }
    private val kotlin by lazy { ContextCompat.getDrawable(context, drawables.ic_language_kotlin) }
    private val python by lazy { ContextCompat.getDrawable(context, drawables.ic_language_python) }
    private val xml by lazy { ContextCompat.getDrawable(context, drawables.ic_language_xml) }
    private val js by lazy { ContextCompat.getDrawable(context, drawables.ic_language_js) }
    private val ts by lazy { ContextCompat.getDrawable(context, drawables.typescript) }
    private val lua by lazy { ContextCompat.getDrawable(context, drawables.lua) }
    private val plugin by lazy { ContextCompat.getDrawable(context, drawables.extension) }
    private val prop by lazy { ContextCompat.getDrawable(context, drawables.settings) }
    private val c by lazy { ContextCompat.getDrawable(context, drawables.ic_language_c) }
    private val cpp by lazy { ContextCompat.getDrawable(context, drawables.ic_language_cpp) }
    private val json by lazy { ContextCompat.getDrawable(context, drawables.ic_language_json) }
    private val css by lazy { ContextCompat.getDrawable(context, drawables.ic_language_css) }
    private val csharp by lazy { ContextCompat.getDrawable(context, drawables.ic_language_csharp) }
    private val bash by lazy { ContextCompat.getDrawable(context, drawables.bash) }
    private val apk by lazy { ContextCompat.getDrawable(context, drawables.apk_document) }
    private val archive by lazy { ContextCompat.getDrawable(context, drawables.archive) }
    private val text by lazy { ContextCompat.getDrawable(context, drawables.text) }
    private val video by lazy { ContextCompat.getDrawable(context, drawables.video) }
    private val audio by lazy { ContextCompat.getDrawable(context, drawables.music) }
    private val image by lazy { ContextCompat.getDrawable(context, drawables.image) }
    private val react by lazy { ContextCompat.getDrawable(context, drawables.react) }
    private val rust by lazy { ContextCompat.getDrawable(context, drawables.rust) }
    private val markdown by lazy { ContextCompat.getDrawable(context, drawables.markdown) }
    private val php by lazy { ContextCompat.getDrawable(context, drawables.php) }

    override fun getIcon(node: Node<com.rk.file_wrapper.FileObject>): Drawable? {
        return if (node.value.isFile()) {
            when (node.value.getName()) {
                "contract.sol",
                "LICENSE",
                "NOTICE",
                    -> text

                "gradlew" -> bash

                else ->
                    when (node.value.getName().substringAfterLast('.', "")) {
                        "java",
                        "bsh","gradle" -> java

                        "html", "htm", "htmx" -> html
                        "kt",
                        "kts" -> kotlin

                        "py" -> python
                        "xml" -> xml
                        "js" -> js
                        "ts" -> ts
                        "lua" -> lua
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
                        "bat",
                        "fish",
                        "ksh" -> bash

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
                        "ogg", "m4a",
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
                        "jsx", "tsx" -> react
                        "php" -> php
                        "plugin" -> plugin
                        "properties","pro","package.json" -> prop
                        else -> file
                    }
            }
        } else if (node.value.isDirectory()) {
            folder
        } else if (node.value.isSymlink()) {
            fileSymlink
        } else {
            unknown
        }

    }

    override fun getChevronRight(): Drawable? {
        return chevronRight
    }
}
