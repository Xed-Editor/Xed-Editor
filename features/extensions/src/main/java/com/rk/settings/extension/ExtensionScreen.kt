package com.rk.settings.extension

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.gson.GsonBuilder
import com.rk.App
import com.rk.App.Companion.iconPackManager
import com.rk.DefaultScope
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayoutLazyColumn
import com.rk.extension.Extension
import com.rk.extension.ExtensionId
import com.rk.extension.ExtensionStats
import com.rk.extension.UpdatableExtension
import com.rk.extension.extensionManager
import com.rk.extension.manager.ExtensionRegistry
import com.rk.extension.manager.IconPackStoreEntry
import com.rk.extension.manager.ThemeStoreEntry
import com.rk.file.child
import com.rk.file.copyToTempDir
import com.rk.file.themeDir
import com.rk.file.toFileObject
import com.rk.icons.Download
import com.rk.icons.XedIcons
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.theme.themes
import com.rk.theme.ThemeConfig
import com.rk.theme.Typography
import com.rk.theme.installFromFile
import com.rk.theme.installTheme
import com.rk.utils.LoadingPopup
import com.rk.utils.errorDialog
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ExtensionSortOptions(val stringRes: Int) {
    NAME(strings.name),
    RATING(strings.rating),
    DOWNLOAD_COUNT(strings.download_count),
    // TODO: Implement: PUBLISH_DATE(strings.publish_date),
}

private enum class ExtensionFilterOptions(val stringRes: Int) {
    ALL(strings.all),
    SUPPORTED(strings.supported),
    CRASHED(strings.crashed),
}

private enum class StoreCategory(val stringRes: Int, val drawableRes: Int) {
    EXTENSIONS(strings.ext, drawables.extension),
    THEMES(strings.themes, drawables.palette),
    ICON_PACKS(strings.icon_packs, drawables.widgets),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var selectedCategory by remember { mutableStateOf(StoreCategory.EXTENSIONS) }

    var currentSortOption by remember { mutableStateOf(ExtensionSortOptions.DOWNLOAD_COUNT) }
    var currentFilterOption by remember { mutableStateOf(ExtensionFilterOptions.ALL) }
    val searchQuery = rememberTextFieldState("")

    var isIndexing by remember { mutableStateOf(false) }
    var isFetching by remember { mutableStateOf(false) }

    val statsMap = remember { mutableStateMapOf<ExtensionId, ExtensionStats>() }

    var storeThemes by remember { mutableStateOf<List<ThemeStoreEntry>>(emptyList()) }
    var storeIconPacks by remember { mutableStateOf<List<IconPackStoreEntry>>(emptyList()) }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            statsMap.clear()
        }
        val shouldLoad =
            refreshKey > 0 ||
                extensionManager.localExtensions.isEmpty() ||
                extensionManager.storeExtension.isEmpty() ||
                storeThemes.isEmpty() ||
                storeIconPacks.isEmpty()

        if (shouldLoad) {
            isIndexing = true
            isFetching = true

            val localJob =
                launch(Dispatchers.IO) {
                    runCatching { extensionManager.indexLocalExtensions() }
                    isIndexing = false
                }
            val storeJob =
                launch(Dispatchers.IO) {
                    runCatching { extensionManager.indexStoreExtensions() }
                    isFetching = false
                }
            val themesJob =
                launch(Dispatchers.IO) {
                    runCatching {
                        val list = ExtensionRegistry.fetchThemes()
                        withContext(Dispatchers.Main) {
                            storeThemes = list
                        }
                    }
                }
            val iconPacksJob =
                launch(Dispatchers.IO) {
                    runCatching {
                        val list = ExtensionRegistry.fetchIconPacks()
                        withContext(Dispatchers.Main) {
                            storeIconPacks = list
                        }
                    }
                }

            localJob.join()
            storeJob.join()
            themesJob.join()
            iconPacksJob.join()
            isRefreshing = false
        }
    }

    LaunchedEffect(refreshKey, isFetching) {
        if (!isFetching) {
            val rawList = extensionManager.getSyncedExtensions()
            launch(Dispatchers.IO) {
                val deferred =
                    rawList
                        .filter { !statsMap.containsKey(it.id) }
                        .map { ext ->
                            async {
                                ext.id to runCatching { ext.getStats() }.getOrNull()
                            }
                        }
                val results = deferred.awaitAll()
                withContext(Dispatchers.Main) {
                    results.forEach { (id, stats) ->
                        if (stats != null) {
                            statsMap[id] = stats
                        }
                    }
                }
            }
        }
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            installExtensionFromUri(scope, uri, activity)
        }

    val themeFilePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val loading = LoadingPopup(activity, null)
                    loading.show()
                    runCatching {
                        installFromFile(uri.toFileObject(expectedIsFile = true))
                    }
                    loading.hide()
                }
            }
        }

    val iconPackFilePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val loading = LoadingPopup(activity, null)
                    loading.show()
                    runCatching {
                        val file = uri.toFileObject(expectedIsFile = true).copyToTempDir()
                        iconPackManager.installIconPack(file)
                    }
                    loading.hide()
                }
            }
        }

    val localExtensions by remember {
        derivedStateOf {
            val allLocal = extensionManager.getLocalExtensions()
            val filtered = applyFilter(searchQuery, allLocal, currentFilterOption)
            applySort(currentSortOption, filtered, statsMap)
        }
    }
    val hasLocalExtensions by remember {
        derivedStateOf {
            extensionManager.getLocalExtensions().isNotEmpty()
        }
    }

    val storeExtensions by remember {
        derivedStateOf {
            val allStore = extensionManager.getStoreExtensions()
            val filtered = applyFilter(searchQuery, allStore, currentFilterOption)
            applySort(currentSortOption, filtered, statsMap)
        }
    }

    val sortedThemes by remember {
        derivedStateOf {
            val query = searchQuery.text
            val filtered =
                if (query.isEmpty()) {
                    storeThemes
                } else {
                    storeThemes.filter { theme ->
                        theme.manifest.name.contains(query, ignoreCase = true)
                    }
                }
            filtered.sortedBy { theme ->
                theme.manifest.name
            }
        }
    }

    val sortedIconPacks by remember {
        derivedStateOf {
            val query = searchQuery.text
            val filtered =
                if (query.isEmpty()) {
                    storeIconPacks
                } else {
                    storeIconPacks.filter { pack ->
                        pack.manifest.name.contains(query, ignoreCase = true)
                    }
                }
            filtered.sortedBy { it.manifest.name }
        }
    }

    RefreshablePreferenceLayoutLazyColumn(
        label = stringResource(strings.store),
        isExpandedScreen = false,
        backArrowVisible = true,
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshKey++
        },
        fab = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (selectedCategory) {
                        StoreCategory.EXTENSIONS -> {
                            checkExtensionWarningAndRun(activity) {
                                filePickerLauncher.launch(arrayOf("application/zip"))
                            }
                        }
                        StoreCategory.THEMES -> themeFilePickerLauncher.launch(arrayOf("application/json"))
                        StoreCategory.ICON_PACKS -> iconPackFilePickerLauncher.launch(arrayOf("application/zip"))
                    }
                },
                icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(strings.install_from_storage)) },
            )
        },
    ) {
        when (selectedCategory) {
            StoreCategory.EXTENSIONS -> {
                item {
                    ExtensionSearchBar(
                        searchQuery = searchQuery,
                        currentSortOption = currentSortOption,
                        currentFilterOption = currentFilterOption,
                        onSortOptionChange = { currentSortOption = it },
                        onFilterOptionChange = { currentFilterOption = it },
                    )
                }
            }
            StoreCategory.THEMES -> {
                item {
                    StoreSearchBar(
                        searchQuery = searchQuery,
                        placeholderText = stringResource(strings.search_themes),
                    )
                }
            }
            StoreCategory.ICON_PACKS -> {
                item {
                    StoreSearchBar(
                        searchQuery = searchQuery,
                        placeholderText = stringResource(strings.search_icon_packs),
                    )
                }
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                StoreCategory.entries.forEach { category ->
                    SegmentedButton(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(stringResource(category.stringRes)) },
                        icon = { Icon(painterResource(category.drawableRes), null) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = category.ordinal,
                                count = StoreCategory.entries.size,
                            ),
                    )
                }
            }
        }

        when (selectedCategory) {
            StoreCategory.EXTENSIONS -> {
                if (storeExtensions.isNotEmpty() || localExtensions.isNotEmpty() || isIndexing || isFetching) {
                    if (localExtensions.isNotEmpty()) {
                        item {
                            PreferenceGroup(heading = stringResource(strings.local)) {
                                localExtensions.forEach { extension ->
                                    key(extension.id) {
                                        val installState =
                                            remember(
                                                extension,
                                                ExtensionRegistry.activeInstalls[extension.id],
                                            ) {
                                                val active = ExtensionRegistry.activeInstalls[extension.id]
                                                active
                                                    ?: if (extensionManager.isInstalled(extension.id)) {
                                                        if (extension is UpdatableExtension && extension.hasUpdate()) {
                                                            InstallState.Updatable
                                                        } else {
                                                            InstallState.Installed
                                                        }
                                                    } else {
                                                        InstallState.Idle
                                                    }
                                            }

                                        ExtensionCard(
                                            extension = extension,
                                            installState = installState,
                                            onInstallClick = {
                                                checkExtensionWarningAndRun(activity) {
                                                    runExtensionInstallAction(extension, {}, context, activity)
                                                }
                                            },
                                            onUninstallClick = {
                                                runExtensionUninstallAction(extension, {}, scope, activity)
                                            },
                                            onUpdateClick = {
                                                if (extension !is UpdatableExtension) return@ExtensionCard
                                                runExtensionUpdateAction(extension, {}, context, activity)
                                            },
                                            onClick = {
                                                navController.navigate(
                                                    "${SettingsRoutes.ExtensionDetail.route}/${it.id}"
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (storeExtensions.isNotEmpty()) {
                        item {
                            PreferenceGroup(
                                heading =
                                    stringResource(strings.store).takeIf {
                                        hasLocalExtensions
                                    }
                            ) {
                                storeExtensions.forEach { extension ->
                                    key(extension.id) {
                                        val installState =
                                            remember(
                                                extension,
                                                ExtensionRegistry.activeInstalls[extension.id],
                                            ) {
                                                val active = ExtensionRegistry.activeInstalls[extension.id]
                                                active
                                                    ?: if (extensionManager.isInstalled(extension.id)) {
                                                        if (extension is UpdatableExtension && extension.hasUpdate()) {
                                                            InstallState.Updatable
                                                        } else {
                                                            InstallState.Installed
                                                        }
                                                    } else {
                                                        InstallState.Idle
                                                    }
                                            }

                                        ExtensionCard(
                                            extension = extension,
                                            installState = installState,
                                            onInstallClick = {
                                                checkExtensionWarningAndRun(activity) {
                                                    runExtensionInstallAction(extension, {}, context, activity)
                                                }
                                            },
                                            onUninstallClick = {
                                                runExtensionUninstallAction(extension, {}, scope, activity)
                                            },
                                            onUpdateClick = {
                                                if (extension !is UpdatableExtension) return@ExtensionCard
                                                runExtensionUpdateAction(extension, {}, context, activity)
                                            },
                                            onClick = {
                                                navController.navigate(
                                                    "${SettingsRoutes.ExtensionDetail.route}/${it.id}"
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isIndexing || isFetching) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement =
                                    Arrangement.spacedBy(16.dp, alignment = Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(text = stringResource(strings.loading))
                            }
                        }
                    }
                } else {
                    item {
                        PreferenceGroup {
                            Text(
                                text = stringResource(strings.no_ext),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
            StoreCategory.THEMES -> {
                if (sortedThemes.isNotEmpty() || isRefreshing) {
                    item {
                        PreferenceGroup {
                            sortedThemes.forEach { themeEntry ->
                                key(themeEntry.id) {
                                    val isInstalled = themes.any { it.id == themeEntry.id }
                                    ThemeStoreCard(
                                        themeEntry = themeEntry,
                                        isInstalled = isInstalled,
                                        onInstallClick = {
                                            val manifestJsonString = themeEntry.manifest.toString()
                                            val gson =
                                                GsonBuilder()
                                                    .excludeFieldsWithModifiers(java.lang.reflect.Modifier.STATIC)
                                                    .create()
                                            val themeConfig = gson.fromJson(manifestJsonString, ThemeConfig::class.java)
                                            DefaultScope.launch(Dispatchers.IO) {
                                                runCatching {
                                                    themeConfig.installTheme()
                                                }
                                                    .onSuccess {
                                                        withContext(Dispatchers.Main) {
                                                            toast(strings.installed)
                                                        }
                                                    }
                                                    .onFailure { err ->
                                                        withContext(Dispatchers.Main) {
                                                            errorDialog(activity, err)
                                                        }
                                                    }
                                            }
                                        },
                                        onUninstallClick = {
                                            val installedTheme = themes.find { it.id == themeEntry.id }
                                            if (installedTheme != null) {
                                                DefaultScope.launch(Dispatchers.IO) {
                                                    runCatching {
                                                        themeDir().child(installedTheme.name).delete()
                                                        withContext(Dispatchers.Main) {
                                                            themes.remove(installedTheme)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        PreferenceGroup {
                            Text(
                                text = stringResource(strings.no_themes),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
            StoreCategory.ICON_PACKS -> {
                if (sortedIconPacks.isNotEmpty() || isRefreshing) {
                    item {
                        PreferenceGroup {
                            sortedIconPacks.forEach { iconPackEntry ->
                                key(iconPackEntry.id) {
                                    val isInstalled = iconPackManager.iconPacks.containsKey(iconPackEntry.id)
                                    IconPackStoreCard(
                                        iconPackEntry = iconPackEntry,
                                        isInstalled = isInstalled,
                                        onInstallClick = {
                                            runIconPackInstallAction(
                                                iconPackEntry.id,
                                                iconPackEntry.manifest.name,
                                                context,
                                                activity,
                                            )
                                        },
                                        onUninstallClick = {
                                            DefaultScope.launch(Dispatchers.IO) {
                                                runCatching {
                                                    iconPackManager.uninstallIconPack(iconPackEntry.id)
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        PreferenceGroup {
                            Text(
                                text = stringResource(strings.no_icon_packs),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            // Add extra space so that FAB doesn't cover content
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StoreSearchBar(
    searchQuery: TextFieldState,
    placeholderText: String,
) {
    SearchBarDefaults.InputField(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        state = searchQuery,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            IconButton({ searchQuery.clearText() }) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = stringResource(strings.close))
            }
        },
        onSearch = {},
        expanded = false,
        onExpandedChange = {},
        placeholder = { Text(placeholderText) },
    )
}

@Composable
fun ThemeStoreCard(
    themeEntry: ThemeStoreEntry,
    isInstalled: Boolean,
    onInstallClick: () -> Unit,
    onUninstallClick: () -> Unit,
) {
    val name = themeEntry.manifest.name
    val progress = ExtensionRegistry.downloadProgress[themeEntry.id]
    PreferenceTemplate(
        modifier = Modifier.fillMaxWidth(),
        startWidget = {
            Icon(
                painter = painterResource(drawables.palette),
                contentDescription = null,
                modifier = Modifier.size(48.dp).padding(8.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(text = name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        description = {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = themeEntry.id,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = Typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress != null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                    if (progress >= 0f) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        endWidget = {
            if (isInstalled) {
                IconButton(
                    onClick = onUninstallClick,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(strings.delete))
                }
            } else {
                IconButton(onClick = onInstallClick) {
                    Icon(XedIcons.Download, contentDescription = null)
                }
            }
        },
    )
}

@Composable
fun IconPackStoreCard(
    iconPackEntry: IconPackStoreEntry,
    isInstalled: Boolean,
    onInstallClick: () -> Unit,
    onUninstallClick: () -> Unit,
) {
    val name = iconPackEntry.manifest.name
    val id = iconPackEntry.manifest.id
    val progress = ExtensionRegistry.downloadProgress[id]
    PreferenceTemplate(
        modifier = Modifier.fillMaxWidth(),
        startWidget = {
            Icon(
                painter = painterResource(drawables.widgets),
                contentDescription = null,
                modifier = Modifier.size(48.dp).padding(8.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        title = {
            Text(text = name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        description = {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = id,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = Typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress != null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                    if (progress >= 0f) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        endWidget = {
            if (isInstalled) {
                IconButton(
                    onClick = onUninstallClick,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(strings.delete))
                }
            } else {
                IconButton(onClick = onInstallClick) {
                    Icon(XedIcons.Download, contentDescription = null)
                }
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExtensionSearchBar(
    searchQuery: TextFieldState,
    currentSortOption: ExtensionSortOptions,
    currentFilterOption: ExtensionFilterOptions,
    onSortOptionChange: (ExtensionSortOptions) -> Unit,
    onFilterOptionChange: (ExtensionFilterOptions) -> Unit,
) {
    var searchOptionsExpanded by remember { mutableStateOf(false) }

    SearchBarDefaults.InputField(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        state = searchQuery,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton({ searchOptionsExpanded = true }) {
                        Icon(painter = painterResource(drawables.filter), contentDescription = null)
                    }

                    DropdownMenu(searchOptionsExpanded, { searchOptionsExpanded = false }) {
                        Text(
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp),
                            text = stringResource(strings.sort_options),
                            style = Typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        ExtensionSortOptions.entries.forEach { sortOption ->
                            DropdownMenuItem(
                                text = { Text(stringResource(sortOption.stringRes)) },
                                onClick = {
                                    onSortOptionChange(sortOption)
                                    searchOptionsExpanded = false
                                },
                                leadingIcon = { RadioButton(currentSortOption == sortOption, null) },
                            )
                        }

                        HorizontalDivider()

                        Text(
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp),
                            text = stringResource(strings.filter_options),
                            style = Typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        ExtensionFilterOptions.entries.forEach { filterOption ->
                            DropdownMenuItem(
                                text = { Text(stringResource(filterOption.stringRes)) },
                                onClick = {
                                    onFilterOptionChange(filterOption)
                                    searchOptionsExpanded = false
                                },
                                leadingIcon = { RadioButton(currentFilterOption == filterOption, null) },
                            )
                        }
                    }
                }

                IconButton({ searchQuery.clearText() }) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = stringResource(strings.close))
                }
            }
        },
        onSearch = {},
        expanded = false,
        onExpandedChange = {},
        placeholder = { Text(stringResource(strings.search_extensions)) },
    )
}

private fun applySort(
    currentSortOption: ExtensionSortOptions,
    filtered: List<Extension>,
    statsMap: SnapshotStateMap<ExtensionId, ExtensionStats>,
): List<Extension> =
    when (currentSortOption) {
        ExtensionSortOptions.NAME -> filtered.sortedBy { it.name }
        ExtensionSortOptions.RATING -> filtered.sortedByDescending { statsMap[it.id]?.rating ?: 0f }
        ExtensionSortOptions.DOWNLOAD_COUNT -> filtered.sortedByDescending { statsMap[it.id]?.downloadCount ?: 0 }
    // TODO: ExtensionSortOptions.PUBLISH_DATE -> { }
    }

private fun applyFilter(
    searchQuery: TextFieldState,
    extensions: List<Extension>,
    currentFilterOption: ExtensionFilterOptions,
): List<Extension> {
    val query = searchQuery.text
    val filteredBySearchQuery =
        if (query.isEmpty()) {
            extensions
        } else {
            extensions.filter { extension ->
                val labelMatch = extension.name.contains(query, ignoreCase = true)
                val descriptionMatch = extension.description?.contains(query, ignoreCase = true) == true
                val tagMatch = extension.tags.any { it.contains(query, ignoreCase = true) }
                val authorMatch = extension.author.displayName.contains(query, ignoreCase = true)
                val authorGithubMatch = extension.author.github?.contains(query, ignoreCase = true) == true
                labelMatch || descriptionMatch || tagMatch || authorMatch || authorGithubMatch
            }
        }

    val xedVersionCode = App.versionCode
    return filteredBySearchQuery.filter {
        val minAppVersion = it.minAppVersion
        val maxAppVersion = it.maxAppVersion

        val outdatedClient = minAppVersion != null && xedVersionCode < minAppVersion
        val outdatedExtension = maxAppVersion != null && xedVersionCode > maxAppVersion

        when (currentFilterOption) {
            ExtensionFilterOptions.ALL -> true
            ExtensionFilterOptions.SUPPORTED -> !outdatedClient && !outdatedExtension
            ExtensionFilterOptions.CRASHED -> extensionManager.isExtensionCrashed(it.id)
        }
    }
}
