package com.rk.exec

import org.json.JSONObject

object PipxUtils {
    suspend fun getInstalledVersion(venvName: String): String? {
        val result =
            ShellUtils.runUbuntu(
                command = arrayOf("pipx", "runpip", venvName, "index", "versions", venvName, "--json"),
                timeoutSeconds = 20L,
            )
        if (result.timedOut || result.exitCode != 0) return null

        return runCatching {
                val obj = JSONObject(result.output)
                obj.getString("installed_version")
            }
            .getOrNull()
    }

    suspend fun getLatestVersion(venvName: String): String? {
        val result =
            ShellUtils.runUbuntu(
                command = arrayOf("pipx", "runpip", venvName, "index", "versions", venvName, "--json"),
                timeoutSeconds = 20L,
            )
        if (result.timedOut || result.exitCode != 0) return null

        return runCatching {
                val obj = JSONObject(result.output)
                obj.getString("latest")
            }
            .getOrNull()
    }

    suspend fun hasUpdate(venvName: String): Boolean {
        val result =
            ShellUtils.runUbuntu(
                command = arrayOf("pipx", "runpip", venvName, "index", "versions", venvName, "--json"),
                timeoutSeconds = 20L,
            )
        if (result.timedOut || result.exitCode != 0) return false

        return runCatching {
                val obj = JSONObject(result.output)
                val latest = obj.getString("latest")
                val installed = obj.getString("installed_version")
                installed != latest
            }
            .getOrDefault(false)
    }
}
