package com.rk.file

object FileValidation {
    /**
     * Regex for invalid characters in a single file or folder name. This does NOT allow forward slashes. Includes
     * control characters and characters like `/ \ < > : " | ? *`
     */
    val INVALID_NAME_CHARS = Regex("""[\p{Cntrl}/\\<>:"|?*]""")

    /**
     * Regex for invalid characters in a folder path. This ALLOWS forward slashes (except at the start) to support
     * creating nested directories.
     */
    val INVALID_FOLDER_PATH_CHARS = Regex("""[\p{Cntrl}\\<>:"|?*]|^/""")
}
