package com.rk.xededitor.ui.screens.settings.mutators

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.file.FileWrapper
import com.rk.DefaultScope
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.ui.components.InfoBlock
import com.rk.xededitor.ui.components.InputDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.xededitor.ui.activities.main.MainActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageMutators(modifier: Modifier = Modifier, navController: NavController) {

    val mutators = Mutators.mutators
    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }


    PreferenceLayout(label = stringResource(id = strings.mutators), backArrowVisible = true, fab = {
        FloatingActionButton(onClick = {
            showDialog = true
        }) {
            Icon(imageVector = Icons.Outlined.Add, contentDescription = "Add Mutator")
        }
    }) {

        InfoBlock(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info, contentDescription = null
                )
            },
            text = stringResource(strings.info_mutators),
        )

        PreferenceGroup {
            if (mutators.isEmpty()) {
                Text(
                    text = stringResource(strings.no_mutators), modifier = Modifier.padding(16.dp)
                )
            } else {
                mutators.toList().forEach { mut ->
                    PreferenceTemplate(modifier = modifier.clickable {
                        DefaultScope.launch {
                            withContext(Dispatchers.Main) {
                                MainActivity.instance?.viewModel?.newTab(fileObject = FileWrapper(mut.file))
                                toast(strings.tab_opened.getString())
                            }

                        }

                    },
                        contentModifier = Modifier.fillMaxHeight(),
                        title = { Text(text = mut.name) },
                        enabled = true,
                        applyPaddings = true,
                        endWidget = {
                            IconButton(onClick = { Mutators.deleteMutator(mut) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete, contentDescription = null
                                )
                            }
                        })
                }
            }
        }
    }

    if (showDialog) {
        InputDialog(
            title = stringResource(strings.create),
            inputLabel = stringResource(strings.mutator_name),
            inputValue = inputText,
            onInputValueChange = { text ->
                inputText = text
            },
            onConfirm = {
                if (onDone(inputText)) {
                    showDialog = false
                }
            },
            onDismiss = {
                showDialog = false
                inputText = ""
            },
        )

    }
}

private fun onDone(name: String): Boolean {
    if (name.isBlank()) {
        toast(strings.name_empty_err.getString())
        return false
    } else {
        if (Mutators.mutators.map { it.name }.contains(name)) {
            toast(strings.name_used.getString())
            return false
        }
        Mutators.createMutator(

                name = name, script = """

            //${strings.script_get_text.getString()}
            //let text = getEditorText()
            
            //${strings.script_show_toast.getString()}
            //showToast(text)


            //${strings.script_network.getString()}
            //let response = http(url, jsonString)
            
            //${strings.script_showing_dialog.getString()}
            //showDialog(title,msg)

            //show a input dialog 
            //showInput(title, hint, prefill)

            //${strings.script_set_text.getString()}
            //setEditorText("${strings.script_text.getString()}")
            
        """.trimIndent()
        )
        return true
    }

    //return true to hide the dialog
}


