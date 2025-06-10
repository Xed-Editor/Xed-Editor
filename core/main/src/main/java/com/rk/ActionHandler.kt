package com.rk
import androidx.lifecycle.lifecycleScope
import com.rk.file_wrapper.FileWrapper
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

    private inline fun onAction(data: String): String{
        runCatching {

            val json = JSONObject(data)
            val action = json.getString("action")
            val args = json.getString("args")
            val pwd = json.getString("pwd")



            when(action){
                "edit" -> {
                   val file = if (args.startsWith("/")){
                        File(args)
                    }else{
                        File(pwd,args)
                   }

                    if (file.exists().not()){
                        return "File not found : $file"
                    }
                    if (file.isDirectory){
                        return "Path is a directory : $file"
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