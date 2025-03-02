package com.rk.xededitor.MainActivity.file

import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import java.io.File


fun com.rk.file_wrapper.FileObject.getFragmentType(): FragmentType {
    return when (getName().substringAfterLast('.', "").lowercase()) {
        // Video file extensions
        "mp4", "mkv", "mov", "avi", "flv", "wmv", "webm" -> FragmentType.VIDEO

        // Audio file extensions
        "mp3", "m4a", "wav", "flac", "aac", "ogg", "wma","opus" -> FragmentType.AUDIO

        // Image file extensions
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "tiff", "svg" -> FragmentType.IMAGE

        //fallback to text editor
        else -> FragmentType.EDITOR
    }
}

