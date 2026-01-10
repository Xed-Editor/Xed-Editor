package com.rk.extension.github

import com.google.gson.Gson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val BASE_URL = "https://api.github.com/repos/Xed-Editor/Extension-Registry/contents"

object GitHubApi {
    private val client = OkHttpClient()
    private val gson = Gson()

    /**
     * Fetches the contents of a file or directory from the GitHub repository.
     *
     * @param path The path to the file or directory in the repository.
     * @throws GitHubApiException If the GitHub API request fails.
     */
    suspend fun fetchContents(path: String): Array<FileContent> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/$path"

                val request = Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw GitHubApiException(
                            "Failed to fetch contents from GitHub",
                            response.code,
                            response.message,
                        )
                    }

                    val body = response.body?.string() ?: return@withContext emptyArray()

                    return@withContext if (body.trim().startsWith("[")) {
                        gson.fromJson(body, Array<FileContent>::class.java)
                    } else {
                        arrayOf(gson.fromJson(body, FileContent::class.java))
                    }
                }
            } catch (e: Exception) {
                return@withContext arrayOf()
            }
        }

    internal suspend fun downloadDir(path: String, targetDir: File) {
        targetDir.mkdirs()
        val contents = fetchContents(path)

        contents.forEach { item ->
            when (item.type) {
                "file" -> downloadFile(item, targetDir)
                "dir" -> downloadDir(item.path, File(targetDir, item.name))
            }
        }
    }

    internal suspend fun downloadFile(file: FileContent, targetDir: File) {
        targetDir.mkdirs()
        withContext(Dispatchers.IO) {
            val url = file.downloadUrl ?: return@withContext
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val data = response.body.bytes() ?: return@withContext
                val outFile =
                    File(targetDir, file.name).apply {
                        if (!exists()) {
                            createNewFile()
                        }
                    }
                outFile.writeBytes(data)
                println("Downloaded: ${outFile.absolutePath}")
            }
        }
    }
}
