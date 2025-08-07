package com.rk
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.rk.file_wrapper.FileWrapper
import com.rk.libcommons.sandboxDir
import com.rk.libcommons.child
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.terminal.bridge.Bridge
import com.rk.xededitor.MainActivity.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object ActionHandler {
    val handler:(String)-> String = {
        onAction(it)
    }

    private fun getCorrectPathForLogging(file: File): String{
        return if (file.absolutePath.contains(sandboxDir().absolutePath)){
            file.absolutePath.removePrefix(sandboxDir().absolutePath)
        }else{
            file.absolutePath
        }
    }

    private inline fun onAction(data: String): String{
        runCatching {
            val json = JSONObject(data)
            val action = json.getString("action")
            val args = json.getString("args")
            val pwd = json.getString("pwd")



            when(action){
                "edit" -> {
                    if (args.isEmpty()){
                        return "No file path provided"
                    }
                   val file = sandboxDir().child(args)

                    if (file.exists().not()){
                        return "File not found : ${getCorrectPathForLogging(file)}"
                    }
                    if (file.isDirectory){
                        return "Path is a directory : ${getCorrectPathForLogging(file)}"
                    }
                    MainActivity.withContext {
                        lifecycleScope.launch(Dispatchers.Main){
                            adapter!!.addFragment(FileWrapper(file))
                            toast(strings.tab_opened)
                        }
                    }
                }
                else -> {
                    return "Unknown action : $action"
                }
            }
        }.onFailure {
            return it.message ?: Bridge.RESULT.ERR
        }
       return Bridge.RESULT.OK
    }
}