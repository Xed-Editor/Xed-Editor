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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.file.FileWrapper
import com.rk.libcommons.DefaultScope
import com.rk.resources.strings
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.components.InfoBlock
import com.rk.xededitor.ui.components.InputDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageMutators(modifier: Modifier = Modifier, navController: NavController) {

    val mutators = Mutators.getMutators()
    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    val context = LocalContext.current

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
            text = "Mutators are small scripts that are used to modify the editor text. Create a mutator and click on it to open it in the editor.",
        )

        PreferenceGroup {
            if (mutators.isEmpty()) {
                Text(
                    text = "No mutators", modifier = Modifier.padding(16.dp)
                )
            } else {
                mutators.toList().forEach { mut ->
                    PreferenceTemplate(modifier = modifier.clickable {
                        DefaultScope.launch {
                            val file = File(context.getTempDir(), mut.name + "&mut.js")
                            withContext(Dispatchers.IO) {
                                file.writeText(mut.script)
                            }
                            withContext(Dispatchers.Main) {
                                MainActivity.activityRef.get()?.adapter?.addFragment(
                                    FileWrapper(
                                        file
                                    ), FragmentType.EDITOR
                                )
                                rkUtils.toast("Opened in Editor")
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
            title = "Name",
            inputLabel = "Mutator Name",
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
        rkUtils.toast("Name cant be empty")
        return false
    } else {
        if (Mutators.getMutators().map { it.name }.contains(name)) {
            rkUtils.toast("Name already used")
            return false
        }
        Mutators.createMutator(
            Mutators.Mutator(
                name = name, script = """

            //get text
            //let text = getEditorText()
            
            //show toast
            //showToast(text)


            //network
            //let response = http(url, jsonString)
            //dialog
            //showDialog(title,msg)

            //set text
            //setEditorText("this text will be written in the editor")
            
            

        """.trimIndent()
            )
        )
        return true
    }

    //return true to hide the dialog
}


