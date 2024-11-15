package com.rk.xededitor.MainActivity.file.filetree

import com.rk.libcommons.R
import java.io.File

private typealias icon = R.drawable

inline fun getIcon(fileForIcon: File): Int {
    return when (fileForIcon.name.substringAfterLast('.', "")
        ) {
            "java", "bsh" -> icon.java
            
            "html" -> icon.ic_language_html
            "kt", "kts" -> icon.ic_language_kotlin
            
            "py" -> icon.ic_language_python
            "xml" -> icon.ic_language_xml
            "js" -> icon.ic_language_js
            "c", "h" -> icon.ic_language_c
            
            "cpp", "hpp" -> icon.ic_language_cpp
            
            "json" -> icon.ic_language_json
            "css", "sass", "scss" -> icon.ic_language_css
            
            "cs" -> icon.ic_language_csharp
            "sh", "bash", "zsh", "bat" -> icon.bash
            
            "apk", "xapk", "apks" -> icon.apkfile
            
            "zip", "rar", "7z", "gz", "bz2", "tar", "xz" -> icon.archive
            
            "md" -> icon.markdown
            "txt" -> icon.text
            "mp3", "wav", "ogg", "m4a", "flac" -> icon.music
            
            "mp4", "mov", "avi", "mkv" -> icon.video
            
            "jpg", "jpeg", "png", "gif", "bmp" -> icon.image
            
            "rs" -> icon.rust
            "jsx" -> icon.react
            "lua" -> icon.languagelua
            
            else -> com.rk.xededitor.R.drawable.outline_insert_drive_file_24
        }
}