package com.rk.terminal

import android.graphics.Typeface
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doOnTextChanged
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rk.activities.terminal.Terminal
import com.rk.animations.NavigationAnimationTransitions
import com.rk.exec.pendingCommand
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.terminal.SettingsTerminalScreen
import com.rk.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
import com.rk.theme.LocalThemeHolder
import com.rk.theme.currentTheme
import com.rk.utils.VolumeKeyHandler
import com.rk.utils.VolumeScrollTarget
import com.rk.utils.dpToPx
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import java.lang.ref.WeakReference
import java.util.Properties
import kotlinx.coroutines.launch

var terminalView = WeakReference<TerminalView?>(null)
var virtualKeysView = WeakReference<VirtualKeysView?>(null)

@Composable
fun TerminalScreen(modifier: Modifier = Modifier, terminalActivity: Terminal) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "terminal",
        enterTransition = { NavigationAnimationTransitions.enterTransition },
        exitTransition = { NavigationAnimationTransitions.exitTransition },
        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
    ) {
        composable("terminal") {
            TerminalScreenInternal(terminalActivity = terminalActivity, navController = navController)
        }
        composable("terminal_settings") { SettingsTerminalScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreenInternal(modifier: Modifier = Modifier, terminalActivity: Terminal, navController: NavController) {
    val context = LocalContext.current

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val isDarkMode = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val currentTheme = LocalThemeHolder.current

    Box(modifier = Modifier.imePadding()) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val drawerWidth = (screenWidthDp * 0.84).dp

        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = stringResource(strings.sessions), style = MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement = Arrangement.End) {
                                IconButton(
                                    onClick = {
                                        fun generateUniqueString(existingStrings: List<String>): String {
                                            var index = 1
                                            var newString: String

                                            do {
                                                newString = "main #$index"
                                                index++
                                            } while (newString in existingStrings)

                                            return newString
                                        }
                                        terminalView.get()?.let {
                                            val client = TerminalBackEnd(it, terminalActivity)
                                            terminalActivity.sessionBinder
                                                ?.get()!!
                                                .createSession(
                                                    generateUniqueString(
                                                        terminalActivity.sessionBinder?.get()!!.getService().sessionList
                                                    ),
                                                    client,
                                                    terminalActivity,
                                                )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add, // Material Design "Add" icon
                                        contentDescription = stringResource(strings.add_session),
                                    )
                                }

                                IconButton(onClick = { navController.navigate("terminal_settings") }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = stringResource(strings.settings),
                                    )
                                }
                            }
                        }

                        terminalActivity.sessionBinder?.get()?.getService()?.sessionList?.let {
                            LazyColumn {
                                items(it) { sessionId ->
                                    SelectableCard(
                                        selected =
                                            sessionId ==
                                                terminalActivity.sessionBinder
                                                    ?.get()
                                                    ?.getService()
                                                    ?.currentSession
                                                    ?.value,
                                        onSelect = { terminalActivity.changeSession(sessionId) },
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(text = sessionId, style = MaterialTheme.typography.bodyLarge)

                                            if (
                                                sessionId !=
                                                    terminalActivity.sessionBinder
                                                        ?.get()
                                                        ?.getService()
                                                        ?.currentSession
                                                        ?.value
                                            ) {
                                                Spacer(modifier = Modifier.weight(1f))

                                                IconButton(
                                                    onClick = {
                                                        terminalActivity.sessionBinder
                                                            ?.get()
                                                            ?.terminateSession(sessionId)
                                                    },
                                                    modifier = Modifier.size(24.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
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
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = stringResource(strings.terminal)) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, null)
                                }
                            },
                        )
                    }
                ) { paddingValues ->
                    Column(modifier = Modifier.padding(paddingValues)) {
                        AndroidView(
                            factory = { context ->
                                TerminalView(context, null).apply {
                                    val terminalColors =
                                        if (isDarkMode) {
                                            currentTheme.darkTerminalColors
                                        } else {
                                            currentTheme.lightTerminalColors
                                        }
                                    applyTerminalColors(
                                        surfaceColor = surfaceColor,
                                        onSurfaceColor = onSurfaceColor,
                                        terminalColors = terminalColors,
                                    )

                                    terminalView = WeakReference(this)
                                    setTextSize(dpToPx(Settings.terminal_font_size.toFloat(), context))
                                    val client = TerminalBackEnd(this, terminalActivity)

                                    val session =
                                        if (pendingCommand != null) {
                                            terminalActivity.sessionBinder?.get()!!.getService().currentSession.value =
                                                pendingCommand!!.id
                                            terminalActivity.sessionBinder?.get()!!.getSession(pendingCommand!!.id)
                                                ?: terminalActivity.sessionBinder
                                                    ?.get()!!
                                                    .createSession(pendingCommand!!.id, client, terminalActivity)
                                                    .session
                                        } else {
                                            terminalActivity.sessionBinder
                                                ?.get()!!
                                                .getSession(
                                                    terminalActivity.sessionBinder
                                                        ?.get()!!
                                                        .getService()
                                                        .currentSession
                                                        .value
                                                )
                                                ?: terminalActivity.sessionBinder
                                                    ?.get()!!
                                                    .createSession(
                                                        terminalActivity.sessionBinder
                                                            ?.get()!!
                                                            .getService()
                                                            .currentSession
                                                            .value,
                                                        client,
                                                        terminalActivity,
                                                    )
                                                    .session
                                        }

                                    session.updateTerminalSessionClient(client)
                                    attachSession(session)
                                    setTerminalViewClient(client)

                                    val fontFile = sandboxDir().child("etc/font.ttf")
                                    if (fontFile.exists()) {
                                        setTypeface(Typeface.createFromFile(fontFile))
                                    } else {
                                        setTypeface(Typeface.createFromAsset(context.assets, "fonts/Default.ttf"))
                                    }

                                    addOnLayoutChangeListener {
                                        v,
                                        left,
                                        top,
                                        right,
                                        bottom,
                                        oldLeft,
                                        oldTop,
                                        oldRight,
                                        oldBottom ->
                                        val widthChanged = (right - left) != (oldRight - oldLeft)
                                        val heightChanged = (bottom - top) != (oldBottom - oldTop)

                                        if (widthChanged || heightChanged) {
                                            val terminalColors =
                                                if (isDarkMode) {
                                                    currentTheme.darkTerminalColors
                                                } else {
                                                    currentTheme.lightTerminalColors
                                                }
                                            terminalView
                                                .get()
                                                ?.applyTerminalColors(
                                                    surfaceColor = surfaceColor,
                                                    onSurfaceColor = onSurfaceColor,
                                                    terminalColors = terminalColors,
                                                )
                                        }
                                    }

                                    post {
                                        keepScreenOn = true
                                        requestFocus()
                                        isFocusableInTouchMode = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            update = { terminalView ->
                                val terminalColors =
                                    if (isDarkMode) {
                                        currentTheme.darkTerminalColors
                                    } else {
                                        currentTheme.lightTerminalColors
                                    }

                                terminalView.applyTerminalColors(
                                    surfaceColor = surfaceColor,
                                    onSurfaceColor = onSurfaceColor,
                                    terminalColors = terminalColors,
                                )
                            },
                        )

                        val pagerState = rememberPagerState(pageCount = { 2 })
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(75.dp)) { page ->
                            when (page) {
                                0 -> {
                                    terminalView.get()?.requestFocus()
                                    AndroidView(
                                        factory = { context ->
                                            VirtualKeysView(context, null).apply {
                                                virtualKeysView = WeakReference(this)
                                                virtualKeysViewClient =
                                                    terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }

                                                buttonTextColor = onSurfaceColor!!

                                                reload(
                                                    VirtualKeysInfo(
                                                        VIRTUAL_KEYS,
                                                        "",
                                                        VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(75.dp),
                                    )
                                }

                                1 -> {
                                    var text by rememberSaveable { mutableStateOf("") }

                                    AndroidView(
                                        modifier = Modifier.fillMaxWidth().height(75.dp),
                                        factory = { ctx ->
                                            EditText(ctx).apply {
                                                maxLines = 1
                                                isSingleLine = true
                                                imeOptions = EditorInfo.IME_ACTION_DONE

                                                // Listen for text changes to update Compose state
                                                doOnTextChanged { textInput, _, _, _ -> text = textInput.toString() }

                                                setOnEditorActionListener { v, actionId, event ->
                                                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                                                        if (text.isEmpty()) {
                                                            // Dispatch enter key events if text is empty
                                                            val eventDown =
                                                                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                                                            val eventUp =
                                                                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
                                                            terminalView.get()?.dispatchKeyEvent(eventDown)
                                                            terminalView.get()?.dispatchKeyEvent(eventUp)
                                                        } else {
                                                            terminalView.get()?.currentSession?.write(text)
                                                            setText("")
                                                        }
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
                                            }
                                        },
                                        update = { editText ->
                                            // Keep EditText's text in sync with Compose state, avoid infinite loop by
                                            // only updating if
                                            // different
                                            if (editText.text.toString() != text) {
                                                editText.setText(text)
                                                editText.setSelection(text.length)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
fun SelectableCard(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val containerColor by
        animateColorAsState(
            targetValue =
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
            label = "containerColor",
        )

    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 2.dp),
        enabled = enabled,
        onClick = onSelect,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

fun Terminal.changeSession(sessionId: String) {
    val terminalView = terminalView.get() ?: return
    val binder = sessionBinder!!.get()!!

    val client = TerminalBackEnd(terminalView, this)
    val session = binder.getSession(sessionId) ?: binder.createSession(sessionId, client, this).session

    session.updateTerminalSessionClient(client)
    terminalView.attachSession(session)
    terminalView.setTerminalViewClient(client)

    terminalView.apply {
        post {
            keepScreenOn = true
            requestFocus()
            setFocusableInTouchMode(true)
        }
    }
    virtualKeysView.get()?.apply { virtualKeysViewClient = VirtualKeysListener(terminalView.mTermSession) }

    binder.getService().currentSession.value = sessionId
}

private fun TerminalView.applyTerminalColors(onSurfaceColor: Int, surfaceColor: Int, terminalColors: Properties) {
    this.onScreenUpdated()

    mEmulator?.mColors?.reset()
    TerminalColors.COLOR_SCHEME.updateWith(terminalColors)

    mEmulator?.mColors?.mCurrentColors?.apply {
        set(TextStyle.COLOR_INDEX_FOREGROUND, onSurfaceColor)
        set(TextStyle.COLOR_INDEX_BACKGROUND, surfaceColor)
        set(TextStyle.COLOR_INDEX_CURSOR, onSurfaceColor)
    }

    invalidate()
}

/**
 * Extension function to create a VolumeScrollTarget from a TerminalView. This allows the terminal to be scrolled using
 * volume keys.
 */
fun TerminalView.asVolumeScrollTarget(): VolumeScrollTarget =
    object : VolumeScrollTarget {
        override fun scrollByVolume(up: Boolean): Boolean {
            if (mEmulator == null) return false
            val rows = if (up) -VolumeKeyHandler.TERMINAL_SCROLL_ROWS else VolumeKeyHandler.TERMINAL_SCROLL_ROWS
            doScroll(null, rows)
            return true
        }
    }

const val VIRTUAL_KEYS =
    ("[" +
        "\n  [" +
        "\n    \"ESC\"," +
        "\n    {" +
        "\n      \"key\": \"/\"," +
        "\n      \"popup\": \"\\\\\"" +
        "\n    }," +
        "\n    {" +
        "\n      \"key\": \"-\"," +
        "\n      \"popup\": \"|\"" +
        "\n    }," +
        "\n    \"HOME\"," +
        "\n    \"UP\"," +
        "\n    \"END\"," +
        "\n    \"PGUP\"" +
        "\n  ]," +
        "\n  [" +
        "\n    \"TAB\"," +
        "\n    \"CTRL\"," +
        "\n    \"ALT\"," +
        "\n    \"LEFT\"," +
        "\n    \"DOWN\"," +
        "\n    \"RIGHT\"," +
        "\n    \"PGDN\"" +
        "\n  ]" +
        "\n]")
