package com.rk.xededitor.ui.screens.settings.editor

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.ui.components.BottomSheetContent
import kotlinx.coroutines.launch
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate



object EditorFont {
    private val defaultFont = Font(name = "Default", isAsset = true, pathOrAsset = "font.ttf")
    val fonts = mutableStateListOf(defaultFont)
    
    data class Font(val name: String, val isAsset: Boolean, val pathOrAsset: String)
    init {
        restoreFonts()
    }
    fun restoreFonts() {
        val f = PreferencesData.getString(PreferencesKeys.FONT_GSON, "")
        val gson = GsonBuilder().create()
        
        try {
            val restoredFonts: List<Font>? = gson.fromJson(f, Array<Font>::class.java)?.toList()
            if (!restoredFonts.isNullOrEmpty()) {
                fonts.clear()
                fonts.addAll(restoredFonts)
            }
        } catch (e: Exception) {
            fonts.clear()
            fonts.add(defaultFont)
        }
    }
    
    fun saveFonts() {
        val gson = GsonBuilder().create()
        val json = gson.toJson(fonts)
        PreferencesData.setString(PreferencesKeys.FONT_GSON, json)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorFontSheet(modifier: Modifier = Modifier, filePickerLauncher: ManagedActivityResultLauncher<String, Uri?>, onReaction: (Boolean) -> Unit,getCurrentFont:()->EditorFont.Font,setCurrentFont:(EditorFont.Font)->Unit) {
    val bottomSheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    
    
    
    ModalBottomSheet(onDismissRequest = { onReaction(false) }, sheetState = bottomSheetState) {
        BottomSheetContent(title = { Text(text = "Select Font") }, buttons = {
            OutlinedButton(onClick = { coroutineScope.launch { bottomSheetState.hide(); onReaction(false) } }) {
                Text(text = stringResource(id = strings.cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { filePickerLauncher.launch("font/ttf");coroutineScope.launch { bottomSheetState.hide(); onReaction(false) } }) {
                Text(text = "Add Font")
            }
        }) {
            LazyColumn {
                itemsIndexed(EditorFont.fonts) { index, font ->
                    PreferenceTemplate(title = { Text(text = font.name) }, modifier = Modifier.clickable {
                        coroutineScope.launch {
                            setCurrentFont(font)
                            bottomSheetState.hide(); onReaction(false)
                        }
                    }, startWidget = { RadioButton(selected = getCurrentFont().pathOrAsset == font.pathOrAsset, onClick = null) })
                }
            }
        }
    }
    
    
}