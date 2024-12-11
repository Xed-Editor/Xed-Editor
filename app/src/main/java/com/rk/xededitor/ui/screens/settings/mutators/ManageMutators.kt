package com.rk.xededitor.ui.screens.settings.mutators

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.rk.libcommons.DefaultScope
import com.rk.resources.strings
import com.rk.xededitor.App.Companion.getTempDir
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.core.FragmentType
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import java.io.File

@Composable
fun ManageMutators(modifier: Modifier = Modifier, navController: NavController) {

    val mutators = Mutators.getMutators()
    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    val context = LocalContext.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                showDialog = true
            }) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = "Add Mutator")
            }
        },
        content = { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                PreferenceLayout(
                    label = stringResource(id = strings.mutators),
                    backArrowVisible = true
                ) {
                    PreferenceGroup {
                        if (mutators.isEmpty()) {
                            Text(
                                text = "No mutators",
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            mutators.toList().forEach { mut ->
                                PreferenceTemplate(
                                    modifier = modifier.clickable {
                                        DefaultScope.launch {
                                            val file = File(context.getTempDir(),mut.name+"&mut.js")
                                            withContext(Dispatchers.IO){
                                                file.writeText(mut.script)
                                            }
                                            withContext(Dispatchers.Main){
                                                MainActivity.activityRef.get()?.adapter?.addFragment(file,FragmentType.EDITOR)
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
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )

    if (showDialog) {
        inputText = ""
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .wrapContentSize()
                ) {
                    Text(
                        text = "Enter name:",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Enter mutator name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (onDone(inputText)){
                                    showDialog = false
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )


                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            if (onDone(inputText)){
                                showDialog = false
                            }
                        }) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }
}

private fun onDone(name:String):Boolean{
    if (name.isBlank()){
        rkUtils.toast("Name cant be empty")
        return false
    }else{
        if (Mutators.getMutators().map { it.name }.contains(name)){
            rkUtils.toast("Name already used")
            return false
        }
        Mutators.createMutator(Mutators.Mutator(name = name, script = """
            let text = api.getEditorText()
            
            //do cool stuff here
            api.showToast(text)
            
            api.setEditorText("this text will be written in the editor")
        """.trimIndent()))
        return true
    }

    //return true to hide the dialog
}
