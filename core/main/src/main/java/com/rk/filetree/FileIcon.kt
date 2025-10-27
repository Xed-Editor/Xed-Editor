package com.rk.filetree

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rk.file.FileObject
import com.rk.resources.drawables
import com.rk.theme.folderSurface

private val ic_file = drawables.file
private val folder = drawables.folder
private val unknown = drawables.unknown_document
private val fileSymlink = drawables.file_symlink
private val java = drawables.java
private val html = drawables.ic_language_html
private val kotlin = drawables.ic_language_kotlin
private val python = drawables.ic_language_python
private val xml = drawables.ic_language_xml
private val js = drawables.ic_language_js
private val ts = drawables.typescript
private val lua = drawables.lua
private val plugin = drawables.extension
private val prop = drawables.settings
private val c = drawables.ic_language_c
private val cpp = drawables.ic_language_cpp
private val json = drawables.ic_language_json
private val css = drawables.ic_language_css
private val csharp = drawables.ic_language_csharp
private val bash = drawables.bash
private val apk = drawables.apk_document
private val archive = drawables.archive
private val text = drawables.text
private val video = drawables.video
private val audio = drawables.music
private val image = drawables.image
private val react = drawables.react
private val rust = drawables.rust
private val markdown = drawables.markdown
private val php = drawables.php
private val go = drawables.golang
private val lisp = drawables.lisp
private val sql = drawables.sql

@Composable
fun FileIcon(file: FileObject) {
    val icon = if (file.isFile()) {
        when (file.getName()) {
            "contract.sol",
            "LICENSE",
            "NOTICE",
                -> text

            "gradlew" -> bash

            else ->
                when (file.getName().substringAfterLast('.', "")) {
                    "java",
                    "bsh", "gradle" -> java

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
                    "ogg", "m4a", "aac", "wma", "opus",
                    "flac" -> audio

                    "mp4",
                    "mov",
                    "avi",
                    "mkv" -> video

                    "jpg",
                    "jpeg",
                    "png",
                    "gif",
                    "bmp","svg" -> image

                    "rs" -> rust
                    "lisp","clisp" -> lisp
                    "sql" -> sql
                    "jsx", "tsx" -> react
                    "php" -> php
                    "plugin" -> plugin
                    "properties", "pro", "package.json" -> prop
                    "go" -> go
                    else -> ic_file
                }
        }
    } else if (file.isDirectory()) {
        folder
    } else if (file.isSymlink()) {
        fileSymlink
    } else {
        unknown
    }

    val tint = if (icon == folder || icon == archive) {
        MaterialTheme.colorScheme.folderSurface
    } else MaterialTheme.colorScheme.secondary

    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp)
    )

}
