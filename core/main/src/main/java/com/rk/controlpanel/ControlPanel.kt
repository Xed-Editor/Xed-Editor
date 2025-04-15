package com.rk.controlpanel

import android.app.Dialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.extension.Hooks
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.tabs.editor.EditorFragment
import com.rk.xededitor.MainActivity.tabs.editor.getCurrentEditorFragment
import com.rk.xededitor.ui.theme.KarbonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference


private var dialog: WeakReference<Dialog?> = WeakReference(null)
fun MainActivity.showControlPanel() {
    MaterialAlertDialogBuilder(this).apply {
        setView(ComposeView(this@showControlPanel).apply {
            setContent {
                KarbonTheme {
                    Surface {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            tonalElevation = 1.dp,
                        ) {
                            DividerColumn(
                                startIndent = 0.dp,
                                endIndent = 0.dp,
                                dividersToSkip = 0,
                            ) {
                                Hooks.ControlPanel.controlItems.values.forEach {
                                    ControlItem(item = it)
                                }

                                ControlItem(
                                    item = ControlItem(
                                        label = "Save",
                                        keyBind = "CTRL+S",
                                        sideEffect = {
                                            getCurrentEditorFragment()?.save(isAutoSaver = false)
                                        })
                                )

                                ControlItem(
                                    item = ControlItem(
                                        label = "Save All",
                                        sideEffect = {
                                            MainActivity.activityRef.get()?.apply {
                                                lifecycleScope.launch{
                                                    if (tabViewModel.fragmentFiles.isNotEmpty()) {
                                                        withContext(Dispatchers.IO) {
                                                            com.rk.xededitor.MainActivity.tabs.editor.saveAllFiles()
                                                        }
                                                    }
                                                }
                                            }
                                        })
                                )
                            }
                        }


                    }
                }
            }
        })
        dialog = WeakReference(show())
    }
}

data class ControlItem(
    val label: String,
    val description: String? = null,
    val keyBind: String? = null,
    val sideEffect: () -> Unit,
    val hideControlPanelOnClick: Boolean = true
)

@Composable
fun ControlItem(modifier: Modifier = Modifier, item: ControlItem) {
    PreferenceTemplate(
        modifier = Modifier.clickable(enabled = true, onClick = {
            item.sideEffect()
            if (item.hideControlPanelOnClick){
                dialog.get()?.dismiss()
            }

        }),
        description = {
            if (item.description != null) {
                Text(text = item.description)
            }
        },
        title = {
            Text(text = item.label)
        }, endWidget = {
            if (item.keyBind != null){
                Text(text = item.keyBind)
            }
        })
}