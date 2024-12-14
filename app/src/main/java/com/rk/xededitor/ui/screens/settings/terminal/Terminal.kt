package com.rk.xededitor.ui.screens.settings.terminal

import androidx.compose.material3.Text
import android.app.Activity
import android.util.TypedValue
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rk.xededitor.rkUtils
import com.rk.xededitor.ui.screens.settings.terminal.virtualkeys.VirtualKeysConstants
import com.rk.xededitor.ui.screens.settings.terminal.virtualkeys.VirtualKeysInfo
import com.rk.xededitor.ui.screens.settings.terminal.virtualkeys.VirtualKeysListener
import com.rk.xededitor.ui.screens.settings.terminal.virtualkeys.VirtualKeysView
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private var terminalView = WeakReference<TerminalView?>(null)
var virtualKeysId = View.generateViewId()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Terminal(modifier: Modifier = Modifier) {
    Box(modifier = Modifier.imePadding()) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

        val scope = rememberCoroutineScope()

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet {
            //drawer content
        }
    }, content = {
        Scaffold(topBar = {
            TopAppBar(title = { Text(text = "Terminal")}, navigationIcon = {
                IconButton(onClick = {
                    scope.launch {
                        drawerState.open()

                        }
                    }) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                })
            }) { paddingValues ->

                Column(modifier = Modifier.padding(paddingValues)) {
                    // TerminalView takes available space
                    val activity = LocalContext.current as? Activity
                    AndroidView(
                        factory = { context ->
                            TerminalView(context, null).apply {
                                terminalView = WeakReference(this)
                                setTextSize(rkUtils.dpToPx(14f, context))
                                val client = TerminalBackEnd(this, activity!!)
                                setTerminalViewClient(client)
                                val session = MkSession.createSession(activity, client)
                                attachSession(session)

                                post {
                                    val typedValue = TypedValue()
                                    context.theme.resolveAttribute(
                                        com.google.android.material.R.attr.colorSurface,
                                        typedValue,
                                        true
                                    )
                                    val surfaceColor = typedValue.data

                                    context.theme.resolveAttribute(
                                        com.google.android.material.R.attr.colorOnSurface,
                                        typedValue,
                                        true
                                    )

                                    setBackgroundColor(surfaceColor)
                                    keepScreenOn = true
                                    requestFocus()
                                    setFocusableInTouchMode(true)

                                    mEmulator?.mColors?.mCurrentColors?.apply {
                                        set(256, typedValue.data)
                                        set(258, typedValue.data)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        update = { terminalView -> terminalView.onScreenUpdated() },
                    )

                    AndroidView(
                        factory = { context ->
                            VirtualKeysView(context, null).apply {

                                id = virtualKeysId
                                val typedValue = TypedValue()
                                context.theme.resolveAttribute(
                                    com.google.android.material.R.attr.colorSurface,
                                    typedValue,
                                    true
                                )
                                val surfaceColor = typedValue.data

                                context.theme.resolveAttribute(
                                    com.google.android.material.R.attr.colorOnSurface,
                                    typedValue,
                                    true
                                )


                                virtualKeysViewClient =
                                    terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }

                                buttonTextColor = typedValue.data
                                setBackgroundColor(surfaceColor)

                                reload(
                                    VirtualKeysInfo(
                                        VIRTUAL_KEYS, "", VirtualKeysConstants.CONTROL_CHARS_ALIASES
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(75.dp)
                    )
                }

            }
        })
    }
}

const val VIRTUAL_KEYS =
    ("[" + "\n  [" + "\n    \"ESC\"," + "\n    {" + "\n      \"key\": \"/\"," + "\n      \"popup\": \"\\\\\"" + "\n    }," + "\n    {" + "\n      \"key\": \"-\"," + "\n      \"popup\": \"|\"" + "\n    }," + "\n    \"HOME\"," + "\n    \"UP\"," + "\n    \"END\"," + "\n    \"PGUP\"" + "\n  ]," + "\n  [" + "\n    \"TAB\"," + "\n    \"CTRL\"," + "\n    \"ALT\"," + "\n    \"LEFT\"," + "\n    \"DOWN\"," + "\n    \"RIGHT\"," + "\n    \"PGDN\"" + "\n  ]" + "\n]")
