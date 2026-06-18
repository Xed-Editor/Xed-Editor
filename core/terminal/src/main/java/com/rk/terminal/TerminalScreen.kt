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

import com.rk.animations.NavigationAnimationTransitions
import com.rk.editor.FontCache
import com.rk.exec.pendingCommand
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.editor.DEFAULT_TERMINAL_FONT_PATH
import com.rk.settings.editor.TerminalFontScreen
import com.rk.settings.DEFAULT_TERMINAL_EXTRA_KEYS
import com.rk.settings.terminal.SettingsTerminalScreen
import com.rk.settings.terminal.TerminalCheckScreen
import com.rk.settings.terminal.TerminalExtraKeys
import com.rk.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
import com.rk.theme.LocalThemeHolder
import com.rk.theme.ThemeHolder
import com.rk.utils.dpToPx
import com.rk.utils.toast
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Properties



@Composable
fun TerminalScreen(modifier: Modifier = Modifier, controller: TerminalController, onExit: (() -> Unit)? = null) {
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
            TerminalScreenInternal(controller = controller, onExit = onExit, navController = navController)
        }
        composable(SettingsRoutes.TerminalSettings.route) { SettingsTerminalScreen(navController) }
        composable(SettingsRoutes.TerminalFontScreen.route) { TerminalFontScreen() }
        composable(SettingsRoutes.TerminalExtraKeys.route) { TerminalExtraKeys() }
        composable(SettingsRoutes.TerminalCheck.route) { TerminalCheckScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreenInternal(modifier: Modifier = Modifier, controller: TerminalController, onExit: (() -> Unit)? = null, navController: NavController) {
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
            drawerContent = { TerminalDrawer(drawerWidth, controller, onExit, navController) },
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
                        val session =
                            if (pendingCommand != null) {
                                val session = controller.createSession(pendingCommand!!.id)
                                controller.changeSession(pendingCommand!!.id)
                                session
                            } else {
                                val currentId = controller.currentSessionId ?: "main"
                                controller.createSession(currentId)
                            }

                        TerminalView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            session = session,
                            virtualKeysViewRef = controller.virtualKeysViewRef,
                            onEnterKeyOnFinishedSession = { finishedSession ->
                                val currentId = controller.currentSessionId
                                if (currentId != null) {
                                    controller.terminateSession(currentId)
                                    if (controller.sessionIds.isNotEmpty()) {
                                        controller.changeSession(controller.sessionIds.first())
                                    }
                                }
                            },
                            onViewInitialized = { view ->
                                controller.terminalViewRef = WeakReference(view)
                            }
                        )

                        val pagerState = rememberPagerState(pageCount = { 2 })
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(75.dp)) { page ->
                            when (page) {
                                0 -> {
                                    controller.virtualKeysViewRef.get()?.requestFocus()
                                    AndroidView(
                                        factory = { context ->
                                            VirtualKeysView(context, null).apply {
                                                controller.virtualKeysViewRef = WeakReference(this)
                                                virtualKeysViewClient =
                                                    controller.terminalViewRef.get()?.mTermSession?.let { VirtualKeysListener(it) }

                                                buttonTextColor = onSurfaceColor

                                                runCatching {
                                                        reload(
                                                            VirtualKeysInfo(
                                                                Settings.terminal_extra_keys,
                                                                "",
                                                                VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                                                            )
                                                        )
                                                    }
                                                    .onFailure {
                                                        toast(strings.invalid_terminal_extra_keys)
                                                        reload(
                                                            VirtualKeysInfo(
                                                                DEFAULT_TERMINAL_EXTRA_KEYS,
                                                                "",
                                                                VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                                                            )
                                                        )
                                                    }
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
                                                        controller.terminalViewRef.get()?.dispatchKeyEvent(eventDown)
                                                        controller.terminalViewRef.get()?.dispatchKeyEvent(eventUp)
                                                    } else {
                                                        controller.terminalViewRef.get()?.currentSession?.write(text)
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
fun TerminalView(
    modifier: Modifier = Modifier,
    session: TerminalSession,
    virtualKeysViewRef: WeakReference<VirtualKeysView?> = WeakReference(null),
    onEnterKeyOnFinishedSession: ((TerminalSession) -> Unit)? = null,
    onViewInitialized: ((com.termux.view.TerminalView) -> Unit)? = null,
) {
    val isDarkMode = isSystemInDarkTheme()
    val currentTheme = LocalThemeHolder.current
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        factory = { context ->
            com.termux.view.TerminalView(context, null).apply {
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

                onViewInitialized?.invoke(this)
                setTextSize(dpToPx(Settings.terminal_font_size.toFloat(), context))
                val client = TerminalBackEnd(
                    terminalViewRef = WeakReference(this),
                    virtualKeysViewRef = virtualKeysViewRef,
                    onEnterKeyOnFinishedSession = onEnterKeyOnFinishedSession,
                )

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

                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val widthChanged = (right - left) != (oldRight - oldLeft)
                    val heightChanged = (bottom - top) != (oldBottom - oldTop)

                    if (widthChanged || heightChanged) {
                        val colors =
                            if (isDarkMode) {
                                currentTheme.darkTerminalColors
                            } else {
                                currentTheme.lightTerminalColors
                            }
                        this@apply.applyTerminalColors(
                            surfaceColor = surfaceColor,
                            onSurfaceColor = onSurfaceColor,
                            terminalColors = colors,
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
        modifier = modifier,
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

            if (terminalView.mTermSession != session) {
                val client = TerminalBackEnd(
                    terminalViewRef = WeakReference(terminalView),
                    virtualKeysViewRef = virtualKeysViewRef,
                    onEnterKeyOnFinishedSession = onEnterKeyOnFinishedSession,
                )
                session.updateTerminalSessionClient(client)
                terminalView.attachSession(session)
                terminalView.setTerminalViewClient(client)
                
                virtualKeysViewRef.get()?.apply {
                    virtualKeysViewClient = VirtualKeysListener(session)
                }
            }
        },
    )
}

@Composable
private fun TerminalDrawer(drawerWidth: Dp, controller: TerminalController, onExit: (() -> Unit)?, navController: NavController) {
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
                            controller.terminalViewRef.get()?.let {
                                val uniqueId = generateUniqueString(controller.sessionIds)
                                controller.createSession(uniqueId)
                                controller.changeSession(uniqueId)
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(strings.add_session))
                    }

                    IconButton(onClick = { navController.navigate(SettingsRoutes.TerminalSettings.route) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(strings.settings),
                        )
                    }
                }
            }

            val sessionIds = controller.sessionIds
            LazyColumn {
                items(sessionIds) { sessionId ->
                    val isSelected = sessionId == controller.currentSessionId
                    NavigationDrawerItem(
                        label = { Text(text = sessionId) },
                        selected = isSelected,
                        onClick = { controller.changeSession(sessionId) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        badge = {
                            IconButton(
                                onClick = {
                                    if (isSelected) {
                                        val index = sessionIds.indexOf(sessionId)
                                        val sessionBefore = sessionIds.getOrNull(index - 1)
                                        val sessionAfter = sessionIds.getOrNull(index + 1)
                                        val neighborSession = sessionBefore ?: sessionAfter
                                        neighborSession?.let { controller.changeSession(it) }
                                    }

                                    controller.terminateSession(sessionId)

                                    if (controller.sessionIds.isEmpty()) {
                                        onExit?.invoke()
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
