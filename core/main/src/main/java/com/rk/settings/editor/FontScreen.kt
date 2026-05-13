package com.rk.settings.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.rk.DefaultScope
import com.rk.activities.main.MainActivity
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.editor.FontCache
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import com.rk.theme.LegacyOutfitFontFamily
import com.rk.theme.generateTypography
import com.rk.utils.errorDialog
import com.rk.utils.toast
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val DEFAULT_EDITOR_FONT_PATH = "fonts/SourceCodePro-Medium.ttf"
const val DEFAULT_TERMINAL_FONT_PATH = "fonts/SourceCodePro-Medium.ttf"
const val DEFAULT_APP_FONT_PATH = "fonts/Outfit-Regular.ttf"

private var appFontRefreshKey by mutableIntStateOf(0)

@Composable
fun EditorFontScreen(modifier: Modifier = Modifier) {
    val selectedFontPath = Settings.editor_font_path
    FontScreen(modifier, selectedFontPath, DEFAULT_EDITOR_FONT_PATH) { font ->
        Settings.editor_font_path = font.pathOrAsset
        Settings.is_editor_font_asset = font.isAsset

        DefaultScope.launch(Dispatchers.Main) {
            MainActivity.instance?.apply {
                viewModel.tabs.forEach {
                    if (it is EditorTab) {
                        it.editorState.editor.get()?.applyFont()
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalFontScreen(modifier: Modifier = Modifier) {
    val selectedFontPath = Settings.terminal_font_path
    val etcFontExists = sandboxDir().child("etc/font.ttf").exists()
    val warningMessage = if (etcFontExists) stringResource(strings.terminal_font_warning) else null

    FontScreen(modifier, selectedFontPath, DEFAULT_TERMINAL_FONT_PATH, warningMessage) { font ->
        Settings.terminal_font_path = font.pathOrAsset
        Settings.is_terminal_font_asset = font.isAsset
    }
}

@Composable
fun AppFontScreen(modifier: Modifier = Modifier) {
    val selectedFontPath = Settings.app_font_path
    FontScreen(modifier, selectedFontPath, DEFAULT_APP_FONT_PATH) { font ->
        Settings.app_font_path = font.pathOrAsset
        Settings.is_app_font_asset = font.isAsset
        appFontRefreshKey++
    }
}

@Composable
fun rememberAppTypography(context: Context): Typography {
    val fontPath = key(appFontRefreshKey) { Settings.app_font_path }
    val font =
        if (fontPath.isNotEmpty()) {
            FontCache.getFont(context, fontPath, Settings.is_app_font_asset)
                ?: FontCache.getFont(context, DEFAULT_APP_FONT_PATH, true)
        } else {
            FontCache.getFont(context, DEFAULT_APP_FONT_PATH, true)
        }
    val family = font?.let { FontFamily(it) } ?: LegacyOutfitFontFamily
    return generateTypography(family)
}

@Composable
fun FontScreen(
    modifier: Modifier = Modifier,
    selectedFontPath: String,
    defaultFontPath: String,
    warningMessage: String? = null,
    onSelectFont: (FontRegistry.Font) -> Unit,
) {
    PreferenceLayout(label = stringResource(strings.fonts), backArrowVisible = true) {
        warningMessage?.let {
            InfoBlock(
                icon = { Icon(imageVector = Icons.Outlined.Warning, contentDescription = null) },
                text = it,
                warning = true,
            )
        }

        PreferenceGroup {
            var selectedFontCompose by remember {
                mutableStateOf(
                    if (selectedFontPath.isEmpty()) {
                        FontRegistry.fonts.find { it.pathOrAsset == defaultFontPath }!!
                    } else {
                        FontRegistry.fonts.find { selectedFontPath == it.pathOrAsset }
                            ?: FontRegistry.fonts.find { it.pathOrAsset == defaultFontPath }!!
                    }
                )
            }

            val context = LocalContext.current
            val filePickerLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                    onResult = { uri: Uri? ->
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
                                context.contentResolver.openInputStream(uri).use { inputStream ->
                                    FileOutputStream(destinationFile).use { outputStream ->
                                        inputStream?.copyTo(outputStream)
                                    }
                                }
                                FontRegistry.fonts.add(
                                    FontRegistry.Font(
                                        name = fileName.removeSuffix(".ttf"),
                                        isAsset = false,
                                        pathOrAsset = destinationFile.absolutePath,
                                    )
                                )
                                FontRegistry.saveFonts()
                                toast(strings.font_added.getString())
                            }
                            .onFailure {
                                if (it.message?.isNotBlank() == true) {
                                    errorDialog(it)
                                }
                            }
                    },
                )

            FontRegistry.fonts.forEach { font ->
                val interactionSource = remember { MutableInteractionSource() }
                val isDefault = font.pathOrAsset == defaultFontPath
                val defaultSuffix = if (isDefault) " (${strings.default_option.getString()})" else ""
                PreferenceTemplate(
                    modifier =
                        modifier.clickable(indication = ripple(), interactionSource = interactionSource) {
                            selectedFontCompose = font
                            onSelectFont(font)
                        },
                    contentModifier = Modifier.fillMaxHeight(),
                    title = { Text(fontWeight = FontWeight.Bold, text = font.name + defaultSuffix) },
                    description = { Text(text = font.pathOrAsset) },
                    enabled = true,
                    applyPaddings = true,
                    startWidget = {
                        RadioButton(selected = selectedFontCompose.pathOrAsset == font.pathOrAsset, onClick = null)
                    },
                    endWidget = {
                        if (font.isAsset.not() && selectedFontCompose.pathOrAsset != font.pathOrAsset) {
                            IconButton(
                                onClick = {
                                    FontRegistry.fonts.removeIf { it.pathOrAsset == font.pathOrAsset }
                                    FontRegistry.saveFonts()
                                    File(font.pathOrAsset).delete()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(strings.delete_font),
                                )
                            }
                        }
                    },
                )
            }

            val interactionSource = remember { MutableInteractionSource() }
            PreferenceTemplate(
                modifier =
                    modifier.clickable(indication = ripple(), interactionSource = interactionSource) {
                        filePickerLauncher.launch("font/ttf")
                    },
                contentModifier = Modifier.fillMaxHeight(),
                title = { Text(fontWeight = FontWeight.Bold, text = stringResource(strings.add_font)) },
                description = { Text(text = stringResource(strings.add_font_desc)) },
                enabled = true,
                applyPaddings = true,
                startWidget = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
            )
        }
    }
}
