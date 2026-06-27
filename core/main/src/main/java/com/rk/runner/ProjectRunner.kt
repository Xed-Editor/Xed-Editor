package com.rk.runner

import android.app.Activity
import com.rk.exec.TerminalCommand
import com.rk.exec.launchTerminal
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.projects.DetectedProjectType
import com.rk.projects.ProjectTypeDetector
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.terminal.setupAssetFile
import java.io.File

/**
 * Project-aware "Run" entry point used by the editor's play button.
 *
 * Instead of running a single file blindly, this inspects the project the file belongs to
 * ([ProjectTypeDetector]) and:
 *  - **Python / Node / Rust / Go** – runs the project from its own folder in the terminal sandbox.
 *  - **Fabric / Forge / generic Gradle** – checks the JDK and runs `./gradlew build`, surfacing any
 *    build errors in the terminal.
 *  - **Static web** – opens the in-app HTML preview (the existing, working web runner).
 *  - **Android / Unknown** – not runnable; the button is hidden (Android needs the full SDK and a
 *    different flow, and unknown projects have no meaningful run command).
 *
 * All terminal commands run with the project root as the working directory, so relative paths,
 * `requirements.txt`, `package.json`, `gradlew`, etc. resolve correctly.
 */
object ProjectRunner {

    /** Detection is cheap but touches the filesystem; cache briefly so toolbar recomposition is fast. */
    private const val CACHE_TTL_MS = 4_000L
    private val cache = HashMap<String, Pair<Long, DetectedProjectType>>()

    /** Project types the run button supports. Android and Unknown are intentionally hidden. */
    fun isRunnable(type: DetectedProjectType): Boolean =
        when (type) {
            DetectedProjectType.ANDROID,
            DetectedProjectType.UNKNOWN -> false
            else -> true
        }

    @Synchronized
    fun detect(projectRootPath: String): DetectedProjectType {
        val now = System.currentTimeMillis()
        cache[projectRootPath]?.let { (ts, type) -> if (now - ts < CACHE_TTL_MS) return type }
        val type = ProjectTypeDetector.detect(File(projectRootPath))
        cache[projectRootPath] = now to type
        return type
    }

    /** Whether the run button should be shown for the given project root. */
    fun canRun(projectRootPath: String?): Boolean {
        if (projectRootPath.isNullOrBlank()) return false
        if (!File(projectRootPath).isDirectory) return false
        return isRunnable(detect(projectRootPath))
    }

    /**
     * Resolve the project root for an editor tab: the explicit project root if present, otherwise
     * the directory containing the open file. Returns null for non-filesystem files (SAF/network),
     * which can't be run.
     */
    fun resolveProjectRootPath(projectRoot: FileObject?, file: FileObject): String? {
        if (projectRoot is FileWrapper) return projectRoot.getAbsolutePath()
        if (file is FileWrapper) return File(file.getAbsolutePath()).parent
        return null
    }

    suspend fun run(activity: Activity, projectRoot: FileObject?, file: FileObject) {
        val rootPath = resolveProjectRootPath(projectRoot, file) ?: return
        val rootFile = File(rootPath)
        val type = detect(rootPath)
        if (!isRunnable(type)) return

        // Static web is previewed in-app via the existing HTML runner.
        if (type == DetectedProjectType.WEB) {
            HtmlRunner.run(activity, webEntry(rootFile, file))
            return
        }

        // Everything else runs/builds in the terminal sandbox, rooted at the project folder.
        setupAssetFile("project_runner")
        launchTerminal(
            activity = activity,
            terminalCommand =
                TerminalCommand(
                    sandbox = true,
                    exe = "/bin/bash",
                    args =
                        arrayOf(
                            localBinDir().child("project_runner").absolutePath,
                            type.name,
                            rootPath,
                            file.getAbsolutePath(),
                        ),
                    id = "Run · ${rootFile.name}",
                    terminatePreviousSession = true,
                    workingDir = rootPath,
                ),
        )
    }

    /** Pick the HTML file to preview: the open file if it's HTML, else index.html, else the first HTML. */
    private fun webEntry(root: File, file: FileObject): FileObject {
        if (file is FileWrapper && file.getName().endsWith(".html", ignoreCase = true)) return file
        File(root, "index.html").takeIf { it.exists() }?.let { return FileWrapper(it) }
        root.listFiles()?.firstOrNull { it.isFile && it.extension.equals("html", ignoreCase = true) }?.let {
            return FileWrapper(it)
        }
        return file
    }
}
