package com.rk.xededitor.ui.screens.settings.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import com.rk.DefaultScope
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.file.FileManager
import com.rk.file.UriWrapper
import com.rk.file.child
import com.rk.file.themeDir
import com.rk.libcommons.toast
import com.rk.settings.Settings
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.components.SettingsToggle
import com.rk.xededitor.ui.theme.Theme
import com.rk.xededitor.ui.theme.currentTheme
import com.rk.xededitor.ui.theme.defaultTheme
import com.rk.xededitor.ui.theme.installFromFile
import com.rk.xededitor.ui.theme.updateThemes
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val themes = mutableStateListOf<Theme>()

@Composable
fun ThemeScreen(modifier: Modifier = Modifier) {
    PreferenceLayout(label = "Themes", fab = {
        ExtendedFloatingActionButton(onClick = {
            SettingsActivity.instance?.fileManager?.requestOpenFile(mimeType = "application/json"){
                DefaultScope.launch{
                    installFromFile(UriWrapper(it!!,false))
                    updateThemes()
                }
            }
        }, icon = {
            Icon(imageVector = Icons.Outlined.Add,null)
        }, text = {
            Text("Add Theme")
        })
    }) {
        PreferenceGroup {
            if (themes.isEmpty()){
                SettingsToggle(label = "No themes found", description = null, showSwitch = false, default = false)
            }else{
                themes.forEach{ theme ->
                    SettingsToggle(label = theme.name, description = null, showSwitch = false, default = false, startWidget = {
                        RadioButton(selected = currentTheme.value == theme, onClick = {
                            currentTheme.value = theme
                            Settings.theme = theme.id
                        })
                    }, sideEffect = {
                        currentTheme.value = theme
                        Settings.theme = theme.id
                    }, endWidget = {
                        if (theme != defaultTheme){
                            IconButton(onClick = {
                                themeDir().child(theme.name).delete()
                                currentTheme.value = defaultTheme
                                Settings.theme = defaultTheme.id
                                themes.remove(theme)
                            }) {
                                Icon(imageVector = Icons.Outlined.Delete,null)
                            }
                        }

                    })
                }
            }

        }
    }
}