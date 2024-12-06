package com.rk.xededitor.ui.screens.settings.editor

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.libcommons.SetupEditor
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.components.SettingsToggle
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate
import java.io.File
import java.io.FileOutputStream

@Composable
fun EditorFontScreen(modifier: Modifier = Modifier) {
    PreferenceLayout(label = "Fonts", backArrowVisible = true) {
        PreferenceGroup {
            val selectedFontP = PreferencesData.getString(PreferencesKeys.SELECTED_FONT_PATH, "")
            val selectedFontCompose = remember {
                mutableStateOf(
                    if (selectedFontP.isEmpty()) {
                        EditorFont.fonts.first() // Fallback to the first font if selectedFontP is empty
                    } else {
                        EditorFont.fonts.find { selectedFontP == it.pathOrAsset } ?: EditorFont.fonts.first()
                        // Find the font matching `selectedFontP`, fallback to the first font if not found
                    }
                )
            }

            val context = LocalContext.current
            val filePickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent(), onResult = { uri: Uri? ->
                runCatching {
                    var fileName = "unknown-font-error.ttf"

                    context.contentResolver.query(uri!!, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }
                    }

                    val destinationFile = File(context.filesDir, "fonts/$fileName")
                    destinationFile.parentFile?.mkdirs()
                    if (destinationFile.exists().not()) {
                        destinationFile.createNewFile()
                    }
                    context.contentResolver.openInputStream(uri!!).use { inputStream ->
                        FileOutputStream(destinationFile).use { outputStream ->
                            inputStream?.copyTo(outputStream)
                        }
                    }
                    EditorFont.fonts.add(
                        EditorFont.Font(
                            name = fileName.removeSuffix(".ttf"), isAsset = false, pathOrAsset = destinationFile.absolutePath
                        )
                    )
                    EditorFont.saveFonts()
                    rkUtils.toast("Font Successfully added")
                }.onFailure { if (it.message?.isNotBlank() == true){rkUtils.toast(it.message)} }
            })


            EditorFont.fonts.forEach { font ->
                val interactionSource = remember { MutableInteractionSource() }
                PreferenceTemplate(
                    modifier = modifier.clickable(
                        indication = ripple(),
                        interactionSource = interactionSource,
                    ) {
                        //onCLick
                        PreferencesData.setString(PreferencesKeys.SELECTED_FONT_PATH, font.pathOrAsset)
                        PreferencesData.setBoolean(PreferencesKeys.IS_SELECTED_FONT_ASSEST, font.isAsset)
                        MainActivity.activityRef.get()?.adapter?.tabFragments?.values?.forEach { f ->
                            f.get()?.let { ff ->
                                if (ff.fragment is EditorFragment) {
                                    (ff.fragment as EditorFragment).editor?.let { editor ->
                                        kotlin.runCatching { SetupEditor.applyFont(editor) }.onFailure { rkUtils.toast(it.message) }
                                    }
                                }
                            }
                        }
                        selectedFontCompose.value = font
                    },
                    contentModifier = Modifier
                        .fillMaxHeight(),
                    title = { Text(fontWeight = FontWeight.Bold, text = font.name) },
                    description = { Text(text = font.pathOrAsset)},
                    enabled = true,
                    applyPaddings = true,
                    startWidget = {
                        RadioButton(
                            selected = selectedFontCompose.value.pathOrAsset == font.pathOrAsset,
                            onClick = null
                        )
                    }, endWidget = {
                        if(font.isAsset.not() && selectedFontCompose.value.pathOrAsset != font.pathOrAsset){
                            IconButton(onClick = {
                                EditorFont.fonts.removeIf { it.pathOrAsset == font.pathOrAsset }
                                EditorFont.saveFonts()
                                File(font.pathOrAsset).delete()
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete Font",
                                )
                            }
                        }
                    }
                )
            }

            val interactionSource = remember { MutableInteractionSource() }
            PreferenceTemplate(
                modifier = modifier.clickable(
                    indication = ripple(),
                    interactionSource = interactionSource,
                ) {
                    //onCLick
                    filePickerLauncher.launch("font/ttf");
                },
                contentModifier = Modifier
                    .fillMaxHeight(),
                title = { Text(fontWeight = FontWeight.Bold, text = "Add new Font") },
                description = { Text(text = "Choose a ttf font from storage")},
                enabled = true,
                applyPaddings = true,
                startWidget = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Font",
                    )
                }
            )
        }
    }
}