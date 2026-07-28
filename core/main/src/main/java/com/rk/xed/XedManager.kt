package com.rk.xed

import com.rk.file.FileObject

object XedManager {
    /** Returns the `.xed` directory within the project root. */
    suspend fun getXedDir(projectRoot: FileObject): FileObject? {
        return projectRoot.getChild(".xed")
    }

    /** Returns the `runner.sh` file if it exists in `.xed`. */
    suspend fun getRunScript(projectRoot: FileObject): FileObject? {
        val xedDir = getXedDir(projectRoot)
        if (xedDir != null && xedDir.exists() && xedDir.isDirectory()) {
            val runScript = xedDir.getChild("runner.sh")
            if (runScript != null && runScript.exists() && runScript.isFile()) {
                return runScript
            }
        }
        return null
    }
}
