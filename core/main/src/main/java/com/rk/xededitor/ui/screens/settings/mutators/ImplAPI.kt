package com.rk.xededitor.ui.screens.settings.mutators

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.errorDialog
import com.rk.libcommons.toast
import com.rk.mutator_engine.Engine
import com.rk.mutator_engine.EngineAPI
import com.rk.xededitor.MainActivity.Kee
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.editor.getCurrentEditorFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File


/**
 * all function in class must block the caller
 * */

class ImplAPI(val engine: Engine) : EngineAPI {
    /**
     * Displays a toast message with the specified text.
     *
     * @param text The message to display in the toast.
     */
    override fun showToast(text: String?) {
        toast(text)
    }

    /**
     * Retrieves the text content from the current editor if it is a text file.
     *
     * If the current editor does not contain a text file or the editor is unavailable,
     * the method will return the string `"__invalid__"`.
     *
     * @return The text content of the current editor, or `"__invalid__"` if the editor
     * is unavailable or does not contain a text file.
     */
    override fun getEditorText(): String {
        return (getCurrentEditorFragment()?.editor?.text ?: "__invalid__").toString()
    }

    /**
     * Sets the specified text in the current editor if it is a text file.
     *
     * **Note:** While the script is running, the user may switch to a different editor,
     * which could result in the text being set in the wrong editor.
     * @param text The text to set in the current editor. If null, the editor's content will be cleared.
     */
    override fun setEditorText(text: String?) {
        runBlocking {
            withContext(Dispatchers.Main) {
                runCatching {
                    getCurrentEditorFragment()?.editor?.setText(text)
                }.onFailure { errorDialog(it);it.printStackTrace() }

            }
        }

    }

    override fun getEditorTextFromPath(path: String?): String {
        var result = "__invalid__"  // Default value
        runBlocking {
            withContext(Dispatchers.Main) {
                MainActivity.withContext {
                    val fragment =
                        adapter?.tabFragments?.get(path?.let { File(it) }?.let { Kee(
                            com.rk.file_wrapper.FileWrapper(
                                it
                            )
                        ) })
                            ?.get()?.fragment
                    if (fragment is EditorFragment) {
                        result = fragment.editor?.text.toString()
                    }
                }
            }
        }
        return result
    }

    override fun http(url: String?, options: String?): String? {
        var loadingPopup:LoadingPopup? = null
        runBlocking {
            withContext(Dispatchers.Main){
                MainActivity.withContext {
                    loadingPopup = LoadingPopup(this,null).show()
                }
            }
        }

        val result = xhttp(url, options)


        runBlocking {
            withContext(Dispatchers.Main){
                loadingPopup?.hide()
            }
        }

        return result
    }

    private fun xhttp(url: String?, options: String?): String? {
        if (url == null) {
            return """{"ok": false, "status": 0, "statusText": "URL is null", "body": null}"""
        }

        val client = OkHttpClient()
        val optionsMap: Map<String, Any?> = try {
            if (options.isNullOrEmpty()) emptyMap()
            else Gson().fromJson(options, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            return """{"ok": false, "status": 0, "statusText": "Invalid options JSON", "body": null}"""
        }

        val builder = Request.Builder().url(url)

        (optionsMap["headers"] as? Map<*, *>)?.forEach { (key, value) ->
            if (key is String && value is String) {
                builder.addHeader(key, value)
            }
        }

        val method = (optionsMap["method"] as? String)?.uppercase() ?: "GET"
        val body = optionsMap["body"] as? String

        if (method == "POST" || method == "PUT") {
            builder.method(method, body?.toRequestBody("application/json".toMediaTypeOrNull()) ?: RequestBody.create(null, ByteArray(0)))
        } else {
            builder.method(method, null)
        }

        val request = builder.build()

        return try {
            client.newCall(request).execute().use { response ->
                Gson().toJson(
                    mapOf(
                        "ok" to response.isSuccessful,
                        "status" to response.code,
                        "statusText" to response.message,
                        "body" to response.body?.string()
                    )
                )
            }
        } catch (e: Exception) {
            Gson().toJson(
                mapOf(
                    "ok" to false, "status" to 0, "statusText" to e.message, "body" to null
                )
            )
        }
    }

    override fun showDialog(title: String?, content: String?) {
        runBlocking {
            withContext(Dispatchers.Main) {
                MainActivity.withContext {
                    MaterialAlertDialogBuilder(this).setTitle(title).setMessage(content)
                        .setPositiveButton("OK", null).show()

                }
            }
        }

    }

    override fun exit() {
        engine.quickJS.close()
    }

    override fun sleep(millis: Double) {
        Thread.sleep(millis.toLong())
    }


}
