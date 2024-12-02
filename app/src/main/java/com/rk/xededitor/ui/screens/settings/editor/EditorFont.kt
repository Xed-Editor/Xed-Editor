package com.rk.xededitor.ui.screens.settings.editor

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.components.BottomSheetContent
import kotlinx.coroutines.launch
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import java.io.File
import java.io.FileOutputStream


object EDITOR_FONT {
    const val DEFAULT = 0
    const val CUSTOM = -1
}

fun setFont(font: Int) {
    PreferencesData.setString(
        PreferencesKeys.NEW_EDITOR_FONT, font.toString()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorFontSheet(modifier: Modifier = Modifier, filePickerLauncher: ManagedActivityResultLauncher<String, Uri?>, onReaction: (Boolean) -> Unit) {
    val bottomSheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    var selectedFont by remember {
        mutableIntStateOf(
            PreferencesData.getString(PreferencesKeys.NEW_EDITOR_FONT, EDITOR_FONT.DEFAULT.toString()).toInt()
        )
    }
    
    val fontLabels = listOf("Default", "Custom")
    val fonts = listOf(EDITOR_FONT.DEFAULT, EDITOR_FONT.CUSTOM)
    
    ModalBottomSheet(onDismissRequest = { onReaction(false) }, sheetState = bottomSheetState) {
        BottomSheetContent(title = { Text(text = stringResource(id = strings.select_theme_mode)) }, buttons = {
            OutlinedButton(onClick = { coroutineScope.launch { bottomSheetState.hide(); onReaction(false) } }) {
                Text(text = stringResource(id = strings.cancel))
            }
        }) {
            LazyColumn {
                itemsIndexed(fonts) { index, font ->
                    PreferenceTemplate(title = { Text(text = fontLabels[index]) }, modifier = Modifier.clickable {
                        selectedFont = font
                        coroutineScope.launch {
                            
                            when (font) {
                                EDITOR_FONT.DEFAULT -> {
                                    setFont(font)
                                }
                                
                                EDITOR_FONT.CUSTOM -> {
                                    filePickerLauncher.launch("font/ttf")
                                }
                            }
                            bottomSheetState.hide(); onReaction(false)
                        }
                    }, startWidget = { RadioButton(selected = selectedFont == font, onClick = null) })
                }
            }
        }
    }
    
    
}