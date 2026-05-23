package com.rk.ai.bridge.tools

import com.rk.ai.bridge.tools.ToolError
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object Security {
    private const val MAX_OUTPUT_SIZE = 50_000_000
    private const val MAX_URL_LENGTH = 8192
    private const val MAX_CONTENT_LENGTH = 10_000_000

    fun canonicalize(file: File): File {
        return runCatching { file.canonicalFile }.getOrDefault(file.absoluteFile)
    }

    fun isInsideWorkspace(file: File, workspaceRoots: List<String>): Boolean {
        if (workspaceRoots.isEmpty()) return true
        val canonical = canonicalize(file).absolutePath
        return workspaceRoots.any { root ->
            val canonicalRoot = canonicalize(File(root)).absolutePath
            canonical.startsWith(canonicalRoot + File.separator) || canonical == canonicalRoot
        }
    }

    fun safeResolve(ideService: com.rk.ai.service.IdeService, path: String): File? {
        val resolved = ideService.resolvePath(path) ?: return null
        val canonical = canonicalize(resolved)
        val workspace = ideService.getPrimaryWorkspacePath()
        if (workspace.isNotBlank()) {
            val workspaceRoot = canonicalize(File(workspace)).absolutePath
            if (!canonical.absolutePath.startsWith(workspaceRoot + File.separator) &&
                canonical.absolutePath != workspaceRoot
            ) {
                return null
            }
        }
        return canonical
    }

    fun validateUrl(urlString: String): Result<URL> = runCatching {
        require(urlString.length <= MAX_URL_LENGTH) { "URL too long (max $MAX_URL_LENGTH chars)" }
        val scheme = urlString.substringBefore("://").lowercase()
        require(scheme in allowedSchemes) { "URL scheme '$scheme' not allowed, must be one of: $allowedSchemes" }
        val url = URI(urlString).toURL()
        val host = url.host?.lowercase() ?: throw IllegalArgumentException("URL has no host")
        require(host in allowedHosts || host.endsWith(".${allowedSuffixes.joinToString(", .")}")) {
            "Host '$host' not allowed"
        }
        url
    }

    fun enforceOutputLimit(text: String): String {
        if (text.length > MAX_OUTPUT_SIZE) {
            return text.take(MAX_OUTPUT_SIZE) + "\n\n... output truncated at ${MAX_OUTPUT_SIZE / 1_000_000}MB"
        }
        return text
    }

    fun enforceContentLength(connection: HttpURLConnection) {
        val contentLength = connection.contentLengthLong
        if (contentLength > MAX_CONTENT_LENGTH) {
            throw ToolError.InvalidParam("content", "Response too large: ${contentLength / 1_000_000}MB (max ${MAX_CONTENT_LENGTH / 1_000_000}MB)")
        }
    }

    fun enforceTimeout(connection: HttpURLConnection, timeoutMs: Int = 15_000) {
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
    }

    private val allowedSchemes = setOf("http", "https")
    private val allowedHosts = setOf(
        "localhost", "127.0.0.1", "[::1]",
        "api.github.com", "raw.githubusercontent.com",
        "registry.npmjs.org", "pypi.org", "pypi.python.org",
        "search.maven.org", "repo1.maven.org",
        "google.com", "www.google.com",
    )
    private val allowedSuffixes = setOf(
        "github.com", "gitlab.com", "bitbucket.org",
        "stackoverflow.com", "stackexchange.com",
        "medium.com", "dev.to", "wikipedia.org",
        "docs.oracle.com", "developer.android.com",
        "developer.mozilla.org", "kotlinlang.org",
        "gradle.org", "spring.io",
    )
}
