package com.rk.terminal

import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun TerminalScreen(modifier: Modifier = Modifier, terminalViewModel: TerminalViewModel) {
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
            TerminalScreenInternal(terminalViewModel = terminalViewModel, navController = navController)
        }
        composable(SettingsRoutes.TerminalSettings.route) { SettingsTerminalScreen(navController) }
        composable(SettingsRoutes.TerminalFontScreen.route) { TerminalFontScreen() }
        composable(SettingsRoutes.TerminalExtraKeys.route) { TerminalExtraKeys() }
    }
}

@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    terminalViewModel: TerminalViewModel,
    showKeys: Boolean = true,
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val isDarkMode = isSystemInDarkTheme()
    val currentTheme = LocalThemeHolder.current

    Column(modifier = modifier.fillMaxSize()) {
        if (terminalViewModel.sessionBinder?.getService() != null) {
            TerminalView(isDarkMode, currentTheme, surfaceColor, onSurfaceColor, terminalViewModel)
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Connecting to terminal service...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (showKeys) {
            AndroidView(
                factory = { context ->
                    VirtualKeysView(context, null).apply {
                        terminalViewModel.virtualKeysView = this
                        virtualKeysViewClient =
                            terminalViewModel.terminalView?.mTermSession?.let { VirtualKeysListener(it) }

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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreenInternal(modifier: Modifier = Modifier, terminalViewModel: TerminalViewModel, navController: NavController) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

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
            drawerContent = { TerminalDrawer(drawerWidth, terminalViewModel, navController) },
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
                    TerminalPanel(
                        modifier = Modifier.padding(paddingValues),
                        terminalViewModel = terminalViewModel
                    )
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
    terminalViewModel: TerminalViewModel,
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

                terminalViewModel.terminalView = this
                settingsTerminalView = WeakReference(this)
                setTextSize(dpToPx(Settings.terminal_font_size.toFloat(), context))
                val client = TerminalBackEnd(terminalViewModel)

                val activity = context as? android.app.Activity
                val binder = terminalViewModel.sessionBinder
                val service = binder?.getService()

                if (activity != null && binder != null && service != null) {
                    val session = if (pendingCommand != null) {
                        service.currentSession.value = pendingCommand!!.id
                        binder.getSession(pendingCommand!!.id)
                            ?: binder.createSession(pendingCommand!!.id, client, activity)?.session
                    } else {
                        val currentId = service.currentSession.value
                        binder.getSession(currentId)
                            ?: binder.createSession(currentId, client, activity)?.session
                    }

                    if (session != null) {
                        session.updateTerminalSessionClient(client)
                        attachSession(session)
                        setTerminalViewClient(client)
                    }
                }

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
                        val colors =
                            if (isDarkMode) {
                                currentTheme.darkTerminalColors
                            } else {
                                currentTheme.lightTerminalColors
                            }
                        applyTerminalColors(
                            surfaceColor = surfaceColor,
                            onSurfaceColor = onSurfaceColor,
                            terminalColors = colors,
                        )
                    }
                }

                post {
                    keepScreenOn = true
                    isFocusableInTouchMode = true
                    focusAndShowKeyboard(terminalViewModel)
                }
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInteropFilter { event ->
                    if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                        terminalViewModel.terminalView?.focusAndShowKeyboard(terminalViewModel)
                    }
                    false
                },
        update = { view ->
            val colors =
                if (isDarkMode) {
                    currentTheme.darkTerminalColors
                } else {
                    currentTheme.lightTerminalColors
                }

            view.applyTerminalColors(
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                terminalColors = colors,
            )
        },
    )
}

@Composable
private fun TerminalDrawer(drawerWidth: Dp, terminalViewModel: TerminalViewModel, navController: NavController) {
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
                            terminalViewModel.terminalView?.let {
                                val client = TerminalBackEnd(terminalViewModel)
                                val activity = it.context as? android.app.Activity ?: return@let
                                terminalViewModel.sessionBinder?.createSession(
                                    generateUniqueString(terminalViewModel.sessionBinder?.getService()?.sessionList ?: emptyList()),
                                    client,
                                    activity,
                                )
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

            val service = terminalViewModel.sessionBinder?.getService()
            service?.sessionList?.let {
                LazyColumn {
                    items(it) { sessionId ->
                        val isSelected = sessionId == service.currentSession.value
                        NavigationDrawerItem(
                            label = { Text(text = sessionId) },
                            selected = isSelected,
                            onClick = { 
                                val activity = terminalViewModel.terminalView?.context as? Terminal
                                activity?.changeSession(sessionId, terminalViewModel) 
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            badge = {
                                IconButton(
                                    onClick = {
                                        if (isSelected) {
                                            val index = service.sessionList.indexOf(sessionId)
                                            val sessionBefore = service.sessionList.getOrNull(index - 1)
                                            val sessionAfter = service.sessionList.getOrNull(index + 1)
                                            val neighborSession = sessionBefore ?: sessionAfter
                                            neighborSession?.let { 
                                                val activity = terminalViewModel.terminalView?.context as? Terminal
                                                activity?.changeSession(it, terminalViewModel) 
                                            }
                                        }

                                        terminalViewModel.sessionBinder?.terminateSession(sessionId)

                                        if (service.sessionList.isEmpty()) {
                                            val activity = terminalViewModel.terminalView?.context as? Terminal
                                            activity?.finish()
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

fun Terminal.changeSession(sessionId: String, terminalViewModel: TerminalViewModel) {
    val termView = terminalViewModel.terminalView ?: return
    val binder = terminalViewModel.sessionBinder ?: return

    val client = TerminalBackEnd(terminalViewModel)
    val session = binder.getSession(sessionId) ?: binder.createSession(sessionId, client, this)?.session ?: return

    session.updateTerminalSessionClient(client)
    termView.attachSession(session)
    termView.setTerminalViewClient(client)

    termView.apply {
        post {
            keepScreenOn = true
            setFocusableInTouchMode(true)
            focusAndShowKeyboard(terminalViewModel)
        }
    }
    terminalViewModel.virtualKeysView?.apply { virtualKeysViewClient = VirtualKeysListener(termView.mTermSession) }

    binder.getService()?.currentSession?.value = sessionId
}

fun TerminalView.focusAndShowKeyboard(terminalViewModel: TerminalViewModel) {
    post {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.restartInput(this)
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
}

fun TerminalView.applyTerminalColors(onSurfaceColor: Int, surfaceColor: Int, terminalColors: Properties) {
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
