package com.rk.xededitor.ui.screens.debugger

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.libcommons.application
import com.rk.libcommons.errorDialog
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture


data class Result(
    val error: Exception?,
    val result: String? = null,
)

private fun runCode(code: String): CompletableFuture<Result> {
    if (BuildConfig.DEBUG.not()) {
        throw IllegalStateException("Cannot execute in release mode")
    }

    return CompletableFuture.supplyAsync {
        try {
            val interpreterClazz = Class.forName("bsh.Interpreter")
            val interpreterInstance = interpreterClazz.getDeclaredConstructor().newInstance()

            interpreterClazz.getDeclaredMethod("setClassLoader",ClassLoader::class.java)(interpreterInstance,application!!.classLoader)
            val result = interpreterClazz.getDeclaredMethod("eval",String::class.java)(interpreterInstance,code)

            Result(
                error = null,
                result = result?.toString(),
            )
        } catch (e: Exception) {
            Result(
                error = e,
                result = null,
            )
        }
    }
}



@Composable
fun Result(modifier: Modifier = Modifier,result: Result) {
    SettingsToggle(
        label = "Output : ${result.result ?: "null"}",
        default = false,
        description = "Error : ${result.error.toString() ?: "null"}",
        showSwitch = false
    )
}

@Composable
fun Debugger(modifier: Modifier = Modifier.fillMaxSize()) {
    PreferenceLayout(label = "Debugger") {

        val code = remember { mutableStateOf("1+1") }
        val output = remember { mutableStateListOf<Result?>() }
        val scope = rememberCoroutineScope()

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)) {
            // Code Input Field
            OutlinedTextField(
                value = code.value,
                onValueChange = { code.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )


            Button(
                onClick = {
                    if (code.value == "clear"){
                        output.clear()
                        code.value = ""
                    }else{
                        if (BuildConfig.DEBUG){
                            scope.launch(Dispatchers.IO) {
                                val result = runCode(code.value).get()
                                output.add(result)

                                if (output.size > 1){
                                    output.removeAt(0)
                                }
                            }

                        }else{
                            errorDialog("Debugger is not allowed on release builds for user safety reasons")
                        }

                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text("Run Code")
            }
        }


        PreferenceGroup {
            SelectionContainer {
                Column(Modifier.weight(1f)) {
                    output.reversed().forEach {
                        if (it != null) {
                            Result(result = it)
                        }
                    }
                }
            }
        }
    }
}