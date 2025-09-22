package com.rk.xededitor.ui.activities.main

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.DefaultScope
//import com.rk.extension.Hooks
import com.rk.libcommons.errorDialog
import com.rk.mutator_engine.Engine
import com.rk.tabs.EditorTab
import com.rk.xededitor.ui.components.XedDialog
import com.rk.xededitor.ui.screens.settings.mutators.MutatorAPI
import com.rk.xededitor.ui.screens.settings.mutators.Mutators
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun ControlPanel(onDismissRequest:()-> Unit,viewModel: MainViewModel){
    XedDialog(onDismissRequest = onDismissRequest) {
        DividerColumn {

            ControlItem(
                item = ControlItem(
                    label = "Save",
                    sideEffect = {
                        MainActivity.instance?.viewModel?.currentTab?.let {
                            if (it is EditorTab){
                                it.editorState.showControlPanel = true
                            }
                        }
                        viewModel.currentTab?.let {
                            if (it is EditorTab){
                                GlobalScope.launch{
                                    it.save()
                                }
                            }
                        }
                    })
            )

            ControlItem(
                item = ControlItem(
                    label = "Save All",
                    sideEffect = {
                        MainActivity.instance?.viewModel?.currentTab?.let {
                            if (it is EditorTab){
                                it.editorState.showControlPanel = true
                            }
                        }
                        viewModel.tabs.forEach{
                            if (it is EditorTab){
                                GlobalScope.launch{
                                    it.save()
                                }
                            }
                        }
                    })
            )


//            Hooks.ControlItems.items.values.forEach{ item ->
//                ControlItem(item = item)
//            }


            Mutators.mutators.forEach { mut ->
                ControlItem(
                    item = ControlItem(
                        label = mut.name,
                        sideEffect = {
                            MainActivity.instance?.viewModel?.currentTab?.let {
                                if (it is EditorTab){
                                    it.editorState.showControlPanel = true
                                }
                            }
                            DefaultScope.launch {
                                Engine(
                                    mut.script,
                                    DefaultScope
                                ).start(onResult = { engine, result ->
                                    println(result)
                                }, onError = { t ->
                                    t.printStackTrace()
                                    errorDialog(t)
                                }, api = MutatorAPI::class.java)
                            }
                        })
                )
            }
        }
    }
}

data class ControlItem(
    val label: String,
    val sideEffect: () -> Unit,
)

@Composable
fun ControlItem(modifier: Modifier = Modifier, item: ControlItem) {
    PreferenceTemplate(
        modifier = Modifier.clickable(enabled = true, onClick = {
            item.sideEffect()
        }),
        title = {
            Text(text = item.label)
        })
}