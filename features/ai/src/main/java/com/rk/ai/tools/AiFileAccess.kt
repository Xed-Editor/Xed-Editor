package com.rk.ai.tools

import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.NetWrapper
import com.rk.file.UriWrapper
import com.rk.file.ZipFileObject

enum class AiFileKind {
    Local,

    Document,

    Remote,

    Archive,

    Unknown,
}

fun FileObject.aiKind(): AiFileKind =
    when (this) {
        is FileWrapper -> AiFileKind.Local
        is UriWrapper -> AiFileKind.Document
        is NetWrapper -> AiFileKind.Remote
        is ZipFileObject -> AiFileKind.Archive
        else -> AiFileKind.Unknown
    }

fun FileObject.nativePathOrNull(): String? = if (this is FileWrapper) getAbsolutePath() else null

internal val AI_IGNORED_DIRS = setOf(".git", ".gradle", ".idea", "build", "node_modules", ".cxx")
