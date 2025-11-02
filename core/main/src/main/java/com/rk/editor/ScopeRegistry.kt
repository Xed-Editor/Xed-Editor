package com.rk.editor

import com.rk.file.FileType

// TODO: Could remove this entirely, need to migrate existing usages to `FileType.kt`
val textmateSources = FileType.entries.toTypedArray()
    .flatMap { fileType ->
        fileType.extensions.mapNotNull { ext ->
            fileType.textmateScope?.let { scope ->
                ext to scope
            }
        }
    }
    .toMap()
