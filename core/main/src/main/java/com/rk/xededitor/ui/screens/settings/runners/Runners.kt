package com.rk.xededitor.ui.screens.settings.runners

import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.google.gson.Gson
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.file.localDir
import com.rk.resources.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.reflect.TypeToken
import com.rk.DefaultScope
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.file.FileWrapper
import com.rk.file.createFileIfNot
import com.rk.file.runnerDir
import com.rk.libcommons.dialog
import com.rk.libcommons.toast
import com.rk.resources.getString
import com.rk.runner.ShellBasedRunner
import com.rk.runner.ShellBasedRunners
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.launch


@Composable
fun Runners(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current

    PreferenceLayout(label = stringResource(strings.runners), fab = {
        FloatingActionButton(onClick = {
            //popup
            DefaultScope.launch{
                val created = ShellBasedRunners.newRunner(ShellBasedRunner(name = "test", regex = ".*\\.test$"))
                if (created.not()){
                    dialog(context = activity!!,title = strings.err.getString(), msg = "A runner with this name already exists")
                }
            }
        }) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
    }) {
        var isLoading = remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            isLoading.value = true
            ShellBasedRunners.indexRunners()
            isLoading.value = false
        }

        PreferenceGroup {
            val scope = rememberCoroutineScope()
            if (isLoading.value){
                SettingsToggle(
                    modifier = Modifier,
                    label = stringResource(strings.loading),
                    default = false,
                    sideEffect = {},
                    showSwitch = false,
                    startWidget = {}
                )
            }else{
                if (ShellBasedRunners.runners.isEmpty()){
                    SettingsToggle(
                        modifier = Modifier,
                        label = stringResource(strings.no_runners),
                        default = false,
                        sideEffect = {},
                        showSwitch = false,
                        startWidget = {}
                    )
                }else{
                    ShellBasedRunners.runners.forEach{
                        SettingsToggle(
                            modifier = Modifier,
                            label = it.getName(),
                            description = null,
                            default = false,
                            sideEffect = { _ ->
                                MainActivity.instance?.adapter?.addFragment(FileWrapper(runnerDir().child("${it.getName()}.sh").createFileIfNot()))
                                toast(strings.tab_opened)
                            },
                            showSwitch = false,
                            endWidget = {
                                IconButton(onClick = {
                                    scope.launch{
                                        ShellBasedRunners.runners.remove(it)
                                        ShellBasedRunners.saveRunners()
                                    }
                                }) {
                                    Icon(imageVector = Icons.Outlined.Delete,contentDescription = null)
                                }
                            }
                        )
                    }
                }

            }
        }
    }
}