package com.rk.xededitor.update

import android.content.Intent
import android.net.Uri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.MainActivity.MainActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import android.content.Context
import android.widget.Toast
import java.io.IOException

object UpdateManager {
    
    @OptIn(DelicateCoroutinesApi::class)
    fun fetch(context: Context, branch: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (PreferencesData.getBoolean(PreferencesKeys.CHECK_UPDATE, false).not()) {
                    return@launch
                }
                
                val lastUpdate = PreferencesData.getString(PreferencesKeys.LAST_UPDATE_CHECK, "0").toLong()
                val timeDifferenceInMillis = if (lastUpdate > 0) {
                    (lastUpdate - System.currentTimeMillis()) * 1000
                } else {
                    Long.MAX_VALUE
                }
                
                val fifteenHoursInMillis = 15 * 60 * 60 * 1000
                val has15HoursPassed = timeDifferenceInMillis >= fifteenHoursInMillis
                
                if (has15HoursPassed.not()) {
                    return@launch
                }
                
                val url = "https://api.github.com/repos/Xed-Editor/Xed-Editor/commits?sha=$branch"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val jsonResponse = response.body?.string()
                            if (jsonResponse != null) {
                                parseJson(jsonResponse)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to check for updates", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show()
                    }
                }
                
                PreferencesData.setString(PreferencesKeys.LAST_UPDATE_CHECK, System.currentTimeMillis().toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun parseJson(jsonStr: String) {
        val updates = mutableListOf<String>()
        
        val jsonArray = JSONArray(jsonStr)
        for (i in 0 until jsonArray.length()) {
            val latestCommit = jsonArray.getJSONObject(i)
            val author = latestCommit.getJSONObject("commit").getJSONObject("author").getString("name")
            
            if (author == "renovate[bot]") {
                continue
            }
            
            val commitMessage = latestCommit.getJSONObject("commit").getString("message")
            val date = latestCommit.getJSONObject("commit").getJSONObject("author").getString("date")
            
            if (convertToUnixTimestamp(BuildConfig.GIT_COMMIT_DATE) < convertToUnixTimestamp(date)) {
                if (commitMessage == "." || updates.contains(commitMessage)) {
                    continue
                }
                updates.add(commitMessage.replace("fix:", "Fixed").replace("fix :", "Fixed").replace("feat:", "Added").replace("feat.", "Added")
                    .replace("refactor:", "Improved").replace("refactor :", "Improved").replace("refactor.", "Improved").replace("feat :", "Added")
                    .replace(Regex("\\b(\\w+)\\b\\s+\\b\\1\\b", RegexOption.IGNORE_CASE), "$1").replaceFirstChar { it.uppercaseChar() })
            }
        }
        
        withContext(Dispatchers.Main) {
            if (updates.isNotEmpty()) {
                MainActivity.activityRef.get()?.let {
                    MaterialAlertDialogBuilder(it).apply {
                        setTitle("New Updates Available")
                        setMessage(updates.joinToString("\n"))
                        setPositiveButton("Update") { _, _ ->
                            val url = "https://github.com/Xed-Editor/Xed-Editor"
                            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                            it.startActivity(intent)
                        }
                        setNegativeButton("Ignore", null)
                        show()
                    }
                }
            }
        }
    }
    
    private fun convertToUnixTimestamp(dateString: String): Long {
        val zonedDateTime = ZonedDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME)
        return zonedDateTime.toEpochSecond()
    }
}
