package com.rk.terminal

import android.graphics.Typeface
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.terminal.Terminal
import com.rk.animations.NavigationAnimationTransitions
import com.rk.editor.FontCache
import com.rk.exec.pendingCommand
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.editor.DEFAULT_TERMINAL_FONT_PATH
import com.rk.settings.editor.TerminalFontScreen
import com.rk.settings.terminal.SettingsTerminalScreen
import com.rk.settings.terminal.TerminalExtraKeys
import com.rk.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
import com.rk.theme.LocalThemeHolder
import com.rk.theme.ThemeHolder
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
        composable(SettingsRoutes.TerminalSettings.route) { SettingsTerminalScreen(navController) }
        composable(SettingsRoutes.TerminalFontScreen.route) { TerminalFontScreen() }
        composable(SettingsRoutes.TerminalExtraKeys.route) { TerminalExtraKeys() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreenInternal(modifier: Modifier = Modifier, terminalActivity: Terminal, navController: NavController) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val isDarkMode = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentTheme = LocalThemeHolder.current

    DisposableEffect(Unit) { onDispose { keyboardController?.hide() } }

    Box(modifier = Modifier.imePadding()) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val drawerWidth = (screenWidthDp * 0.84).dp

        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = { TerminalDrawer(drawerWidth, terminalActivity, navController) },
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
                        TerminalView(isDarkMode, currentTheme, surfaceColor, onSurfaceColor, terminalActivity)

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

                                                buttonTextColor = onSurfaceColor

                                                reload(
                                                    VirtualKeysInfo(
                                                        Settings.terminal_extra_keys,
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
                                    val focusRequester = remember { FocusRequester() }

                                    TextField(
                                        value = text,
                                        onValueChange = { text = it },
                                        maxLines = 1,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions =
                                            KeyboardActions(
                                                onDone = {
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
                                                        text = ""
                                                    }
                                                }
                                            ),
                                        modifier = Modifier.fillMaxWidth().height(75.dp).focusRequester(focusRequester),
                                    )

                                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
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
private fun ColumnScope.TerminalView(
    isDarkMode: Boolean,
    currentTheme: ThemeHolder,
    surfaceColor: Int,
    onSurfaceColor: Int,
    terminalActivity: Terminal,
) {
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
                val client = TerminalBackEnd()

                val session =
                    if (pendingCommand != null) {
                        terminalActivity.sessionBinder?.get()!!.getService().currentSession.value = pendingCommand!!.id
                        terminalActivity.sessionBinder?.get()!!.getSession(pendingCommand!!.id)
                            ?: terminalActivity.sessionBinder
                                ?.get()!!
                                .createSession(pendingCommand!!.id, client, terminalActivity)
                                .session
                    } else {
                        terminalActivity.sessionBinder
                            ?.get()!!
                            .getSession(terminalActivity.sessionBinder?.get()!!.getService().currentSession.value)
                            ?: terminalActivity.sessionBinder
                                ?.get()!!
                                .createSession(
                                    terminalActivity.sessionBinder?.get()!!.getService().currentSession.value,
                                    client,
                                    terminalActivity,
                                )
                                .session
                    }

                session.updateTerminalSessionClient(client)
                attachSession(session)
                setTerminalViewClient(client)

                // Legacy behavior
                val fontFile = sandboxDir().child("etc/font.ttf")
                if (fontFile.exists()) {
                    setTypeface(Typeface.createFromFile(fontFile))
                } else {
                    val fontPath = Settings.terminal_font_path
                    val font =
                        if (fontPath.isNotEmpty()) {
                            FontCache.getTypeface(context, fontPath, Settings.is_terminal_font_asset)
                                ?: FontCache.getTypeface(context, DEFAULT_TERMINAL_FONT_PATH, true)
                        } else {
                            FontCache.getTypeface(context, DEFAULT_TERMINAL_FONT_PATH, true)
                        }

                    setTypeface(font)
                }

                addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
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
                    isFocusableInTouchMode = true
                    requestFocus()
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
}

@Composable
private fun TerminalDrawer(drawerWidth: Dp, terminalActivity: Terminal, navController: NavController) {
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
                                val client = TerminalBackEnd()
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
                        Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(strings.add_session))
                    }

                    IconButton(onClick = { navController.navigate("terminal_settings") }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(strings.settings),
                        )
                    }
                }
            }

            val service = terminalActivity.sessionBinder?.get()?.getService()
            service?.sessionList?.let {
                LazyColumn {
                    items(it) { sessionId ->
                        val isSelected = sessionId == service.currentSession.value
                        NavigationDrawerItem(
                            label = { Text(text = sessionId) },
                            selected = isSelected,
                            onClick = { terminalActivity.changeSession(sessionId) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            badge = {
                                IconButton(
                                    onClick = {
                                        if (isSelected) {
                                            val index = service.sessionList.indexOf(sessionId)
                                            val sessionBefore = service.sessionList.getOrNull(index - 1)
                                            val sessionAfter = service.sessionList.getOrNull(index + 1)
                                            val neighborSession = sessionBefore ?: sessionAfter
                                            neighborSession?.let { terminalActivity.changeSession(it) }
                                        }

                                        terminalActivity.sessionBinder?.get()?.terminateSession(sessionId)

                                        if (service.sessionList.isEmpty()) {
                                            terminalActivity.finish()
                                            service.actionExit()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(strings.delete),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

fun Terminal.changeSession(sessionId: String) {
    val terminalView = terminalView.get() ?: return
    val binder = sessionBinder!!.get()!!

    val client = TerminalBackEnd()
    val session = binder.getSession(sessionId) ?: binder.createSession(sessionId, client, this).session

    session.updateTerminalSessionClient(client)
    terminalView.attachSession(session)
    terminalView.setTerminalViewClient(client)

    terminalView.apply {
        post {
            keepScreenOn = true
            setFocusableInTouchMode(true)
            requestFocus()
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
