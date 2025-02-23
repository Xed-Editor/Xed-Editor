package com.rk.xededitor.ui.screens.terminal

import android.content.Intent
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rk.libcommons.dpToPx
import com.rk.libcommons.pendingCommand
import com.rk.resources.strings
import com.rk.xededitor.service.SessionService
import com.rk.xededitor.ui.activities.terminal.Terminal
import com.rk.xededitor.ui.screens.terminal.virtualkeys.VirtualKeysConstants
import com.rk.xededitor.ui.screens.terminal.virtualkeys.VirtualKeysInfo
import com.rk.xededitor.ui.screens.terminal.virtualkeys.VirtualKeysListener
import com.rk.xededitor.ui.screens.terminal.virtualkeys.VirtualKeysView
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

var terminalView = WeakReference<TerminalView?>(null)
var virtualKeysView = WeakReference<VirtualKeysView?>(null)
var virtualKeysId = View.generateViewId()


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(modifier: Modifier = Modifier, terminalActivity: Terminal) {
    val context = LocalContext.current
    LaunchedEffect("terminal") {
        context.startService(Intent(context, SessionService::class.java))
    }
    Box(modifier = Modifier.imePadding()) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val drawerWidth = (screenWidthDp * 0.84).dp

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(strings.sessions),
                                style = MaterialTheme.typography.titleLarge
                            )
                            IconButton(onClick = {
                                fun generateUniqueString(existingStrings: List<String>): String {
                                    var index = 1
                                    var newString: String

                                    do {
                                        newString = "main$index"
                                        index++
                                    } while (newString in existingStrings)

                                    return newString
                                }
                                terminalView.get()
                                    ?.let {
                                        val client = TerminalBackEnd(it, terminalActivity)
                                        terminalActivity.sessionBinder!!.createSession(
                                            generateUniqueString(terminalActivity.sessionBinder!!.getService().sessionList),
                                            client,
                                            terminalActivity
                                        )
                                    }

                            }) {
                                Icon(
                                    imageVector = Icons.Default.Add, // Material Design "Add" icon
                                    contentDescription = stringResource(strings.add_session)
                                )
                            }
                        }

                        terminalActivity.sessionBinder?.getService()?.sessionList?.let{
                            LazyColumn {
                                items(it){ session_id ->
                                    SelectableCard(
                                        selected = session_id == terminalActivity.sessionBinder?.getService()?.currentSession?.value,
                                        onSelect = { changeSession(terminalActivity, session_id) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = session_id,
                                                style = MaterialTheme.typography.bodyLarge
                                            )

                                            if (session_id != terminalActivity.sessionBinder?.getService()?.currentSession?.value){
                                                Spacer(modifier = Modifier.weight(1f))

                                                IconButton(
                                                    onClick = {
                                                        println(session_id)
                                                        terminalActivity.sessionBinder?.terminateSession(session_id)
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                        }
                                    }


                                }
                            }
                        }

                    }
                }

            },
            content = {
                Scaffold(topBar = {
                    TopAppBar(
                        title = { Text(text = stringResource(strings.terminal)) },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch { drawerState.open() }
                            }) {
                                Icon(Icons.Default.Menu, null)
                            }
                        })
                }) { paddingValues ->
                    Column(modifier = Modifier.padding(paddingValues)) {
                        AndroidView(
                            factory = { context ->
                                TerminalView(context, null).apply {
                                    terminalView = WeakReference(this)
                                    setTextSize(dpToPx(14f, context))
                                    val client = TerminalBackEnd(this, terminalActivity)

                                    val session = if (pendingCommand != null){
                                        terminalActivity.sessionBinder!!.getService().currentSession.value = pendingCommand!!.id
                                        terminalActivity.sessionBinder!!.getSession(pendingCommand!!.id)
                                            ?: terminalActivity.sessionBinder!!.createSession(
                                                pendingCommand!!.id,
                                                client,
                                                terminalActivity
                                            )
                                    }else{
                                        terminalActivity.sessionBinder!!.getSession(terminalActivity.sessionBinder!!.getService().currentSession.value)
                                            ?: terminalActivity.sessionBinder!!.createSession(
                                                terminalActivity.sessionBinder!!.getService().currentSession.value,
                                                client,
                                                terminalActivity
                                            )
                                    }

                                    session.updateTerminalSessionClient(client)
                                    attachSession(session)
                                    setTerminalViewClient(client)
                                    setTypeface(
                                        Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/Default.ttf"
                                        )
                                    )

                                    post {
                                        val typedValue = TypedValue()

                                        context.theme.resolveAttribute(
                                            com.google.android.material.R.attr.colorOnSurface,
                                            typedValue,
                                            true
                                        )
                                        keepScreenOn = true
                                        requestFocus()
                                        isFocusableInTouchMode = true

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
                            update = { terminalView ->
                                terminalView.onScreenUpdated();

                                val typedValue = TypedValue()

                                context.theme.resolveAttribute(
                                    com.google.android.material.R.attr.colorOnSurface,
                                    typedValue,
                                    true
                                )

                                terminalView.mEmulator?.mColors?.mCurrentColors?.apply {
                                    set(256, typedValue.data)
                                    set(258, typedValue.data)
                                } },
                        )

                        AndroidView(
                            factory = { context ->
                                VirtualKeysView(context, null).apply {
                                    virtualKeysView = WeakReference(this)
                                    id = virtualKeysId
                                    val typedValue = TypedValue()
                                    context.theme.resolveAttribute(
                                        com.google.android.material.R.attr.colorOnSurface,
                                        typedValue,
                                        true
                                    )


                                    virtualKeysViewClient =
                                        terminalView.get()?.mTermSession?.let {
                                            VirtualKeysListener(
                                                it
                                            )
                                        }

                                    buttonTextColor = typedValue.data

                                    reload(
                                        VirtualKeysInfo(
                                            VIRTUAL_KEYS,
                                            "",
                                            VirtualKeysConstants.CONTROL_CHARS_ALIASES
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

@Composable
fun SelectableCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "containerColor"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 8.dp else 2.dp
        ),
        enabled = enabled,
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}


fun changeSession(terminalActivity: Terminal, session_id:String){
    terminalView.get()?.apply {
        val client = TerminalBackEnd(this, terminalActivity)
        val session =
            terminalActivity.sessionBinder!!.getSession(session_id)
                ?: terminalActivity.sessionBinder!!.createSession(
                    session_id,
                    client,
                    terminalActivity
                )
        session.updateTerminalSessionClient(client)
        attachSession(session)
        setTerminalViewClient(client)
        post {
            val typedValue = TypedValue()

            context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface,
                typedValue,
                true
            )
            keepScreenOn = true
            requestFocus()
            setFocusableInTouchMode(true)

            mEmulator?.mColors?.mCurrentColors?.apply {
                set(256, typedValue.data)
                set(258, typedValue.data)
            }
        }
        virtualKeysView.get()?.apply {
            virtualKeysViewClient = terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }
        }

    }
    terminalActivity.sessionBinder!!.getService().currentSession.value = session_id

}



const val VIRTUAL_KEYS =
    ("[" + "\n  [" + "\n    \"ESC\"," + "\n    {" + "\n      \"key\": \"/\"," + "\n      \"popup\": \"\\\\\"" + "\n    }," + "\n    {" + "\n      \"key\": \"-\"," + "\n      \"popup\": \"|\"" + "\n    }," + "\n    \"HOME\"," + "\n    \"UP\"," + "\n    \"END\"," + "\n    \"PGUP\"" + "\n  ]," + "\n  [" + "\n    \"TAB\"," + "\n    \"CTRL\"," + "\n    \"ALT\"," + "\n    \"LEFT\"," + "\n    \"DOWN\"," + "\n    \"RIGHT\"," + "\n    \"PGDN\"" + "\n  ]" + "\n]")