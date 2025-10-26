package com.rk.activities.main

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.DefaultScope
import com.rk.utils.errorDialog
import com.rk.mutation.Engine
import com.rk.resources.strings
import com.rk.tabs.EditorTab
import com.rk.components.XedDialog
import com.rk.settings.app.InbuiltFeatures
import com.rk.settings.mutators.Mutators
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.rk.mutation.*
import com.rk.settings.mutators.*

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun ControlPanel(onDismissRequest:()-> Unit,viewModel: MainViewModel){
    XedDialog(onDismissRequest = onDismissRequest) {
        DividerColumn {

            ControlItem(
                item = ControlItem(
                    label = stringResource(strings.save),
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
                    label = stringResource(strings.save_all),
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


            if (InbuiltFeatures.mutators.state.value){
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