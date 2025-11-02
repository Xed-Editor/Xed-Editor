package com.rk.editor

import com.rk.file.FileType

val textmateSources = FileType.entries.toTypedArray()
    .flatMap { fileType ->
        fileType.extensions.mapNotNull { ext ->
            fileType.textmateScope?.let { scope ->
                ext to scope
            }
        }
    }
    .toMap()
