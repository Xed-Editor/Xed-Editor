package com.rk.settings.extension

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.App
import com.rk.App.Companion.iconPackManager
import com.rk.DefaultScope
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.XedDropdownMenuItem
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayoutLazyColumn
import com.rk.extension.Extension
import com.rk.extension.UpdatableExtension
import com.rk.extension.extensionManager
import com.rk.extension.manager.StoreManager
import com.rk.file.child
import com.rk.file.themeDir
import com.rk.icons.Download
import com.rk.icons.XedIcons
import com.rk.icons.pack.IconPackEntry
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.theme.themes
import com.rk.theme.ThemeEntry
import com.rk.theme.ThemeManager
import com.rk.theme.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ExtensionSortOptions(val stringRes: Int) {
    NAME(strings.name),
    RATING(strings.rating),
    DOWNLOAD_COUNT(strings.download_count),
    PUBLISH_DATE(strings.publish_date),
    UPDATE_DATE(strings.update_date),
}

private enum class ExtensionFilterOptions(val stringRes: Int) {
    ALL(strings.all),
    SUPPORTED(strings.supported),
    CRASHED(strings.status_crashed),
}

private enum class StoreCategory(val stringRes: Int, val drawableRes: Int) {
    EXTENSIONS(strings.ext, drawables.extension),
    THEMES(strings.themes, drawables.palette),
    ICON_PACKS(strings.icon_packs, drawables.widgets);

    companion object {
        fun fromName(name: String): StoreCategory? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// TODO: Rename to StoreScreen and fix local themes/icon-packs not showing
fun ExtensionScreen(navController: NavController, query: String?, category: String? = null) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var selectedCategory by remember {
        mutableStateOf(category?.let { StoreCategory.fromName(it) } ?: StoreCategory.EXTENSIONS)
    }

    var currentSortOption by remember { mutableStateOf(ExtensionSortOptions.DOWNLOAD_COUNT) }
    var currentFilterOption by remember { mutableStateOf(ExtensionFilterOptions.ALL) }
    val searchQuery = rememberTextFieldState(query ?: "")

    val dialogManager = remember { ExtensionDialogManager() }

    var isIndexing by remember { mutableStateOf(false) }
    var isFetching by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        val shouldLoad =
            refreshKey > 0 ||
                extensionManager.localExtensions.isEmpty() ||
                extensionManager.storeExtension.isEmpty() ||
                ThemeManager.storeThemes.isEmpty() ||
                iconPackManager.storeIconPacks.isEmpty()

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
                        StoreManager.fetchThemes()
                    }
                }
            val iconPacksJob =
                launch(Dispatchers.IO) {
                    runCatching {
                        StoreManager.fetchIconPacks()
                    }
                }

            localJob.join()
            storeJob.join()
            themesJob.join()
            iconPacksJob.join()
            isRefreshing = false
        }
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            installAutoDetect(scope, uri, activity)
        }

    val localExtensions by remember {
        derivedStateOf {
            val allLocal = extensionManager.getLocalExtensions()
            val filtered = applyFilter(searchQuery, allLocal, currentFilterOption)
            applySort(currentSortOption, filtered)
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
            applySort(currentSortOption, filtered)
        }
    }

    val sortedThemes by remember {
        derivedStateOf {
            val query = searchQuery.text
            val filtered =
                if (query.isEmpty()) {
                    ThemeManager.storeThemes.values
                } else {
                    ThemeManager.storeThemes.values.filter { theme ->
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
                    iconPackManager.storeIconPacks.values
                } else {
                    iconPackManager.storeIconPacks.values.filter { pack ->
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
                    if (Settings.warn_extensions) {
                        dialogManager.showWarning {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    } else {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                },
                icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(strings.install_from_storage)) },
            )
        },
    ) {
        item {
            ExtensionDialogRenderer(dialogManager)
        }

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
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedCategory.ordinal,
                modifier = Modifier.padding(bottom = 8.dp),
                edgePadding = 16.dp,
            ) {
                StoreCategory.entries.forEach { category ->
                    LeadingIconTab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = {
                            Text(stringResource(category.stringRes))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(category.drawableRes),
                                contentDescription = null,
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        val installState = rememberInstallState(extension)

                                        ExtensionCard(
                                            extension = extension,
                                            installState = installState,
                                            onInstallClick = {
                                                val action = {
                                                    val missing = getMissingDependencies(extension)
                                                    if (missing.isNotEmpty()) {
                                                        dialogManager.showDependencies(extension, missing) {
                                                            runExtensionInstallAction(extension, {}, context, activity)
                                                        }
                                                    } else {
                                                        runExtensionInstallAction(extension, {}, context, activity)
                                                    }
                                                }

                                                if (Settings.warn_extensions) {
                                                    dialogManager.showWarning(action)
                                                } else {
                                                    action()
                                                }
                                            },
                                            onUninstallClick = {
                                                runExtensionUninstallAction(extension, {}, scope, activity)
                                            },
                                            onUpdateClick = {
                                                if (extension !is UpdatableExtension) return@ExtensionCard
                                                val missing = getMissingDependencies(extension)
                                                if (missing.isNotEmpty()) {
                                                    dialogManager.showDependencies(extension, missing) {
                                                        runExtensionUpdateAction(extension, {}, context, activity)
                                                    }
                                                } else {
                                                    runExtensionUpdateAction(extension, {}, context, activity)
                                                }
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
                                        val installState = rememberInstallState(extension)

                                        ExtensionCard(
                                            extension = extension,
                                            installState = installState,
                                            onInstallClick = {
                                                val action = {
                                                    val missing = getMissingDependencies(extension)
                                                    if (missing.isNotEmpty()) {
                                                        dialogManager.showDependencies(extension, missing) {
                                                            runExtensionInstallAction(extension, {}, context, activity)
                                                        }
                                                    } else {
                                                        runExtensionInstallAction(extension, {}, context, activity)
                                                    }
                                                }

                                                if (Settings.warn_extensions) {
                                                    dialogManager.showWarning(action)
                                                } else {
                                                    action()
                                                }
                                            },
                                            onUninstallClick = {
                                                runExtensionUninstallAction(extension, {}, scope, activity)
                                            },
                                            onUpdateClick = {
                                                if (extension !is UpdatableExtension) return@ExtensionCard
                                                val missing = getMissingDependencies(extension)
                                                if (missing.isNotEmpty()) {
                                                    dialogManager.showDependencies(extension, missing) {
                                                        runExtensionUpdateAction(extension, {}, context, activity)
                                                    }
                                                } else {
                                                    runExtensionUpdateAction(extension, {}, context, activity)
                                                }
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
                                            runThemeInstallAction(
                                                themeEntry.id,
                                                themeEntry.manifest.name,
                                                context,
                                                activity,
                                            )
                                        },
                                        onUninstallClick = {
                                            val installedTheme = themes.find { it.id == themeEntry.id }
                                            if (installedTheme != null) {
                                                DefaultScope.launch(Dispatchers.IO) {
                                                    runCatching {
                                                        themeDir().child(installedTheme.id).deleteRecursively()
                                                        withContext(Dispatchers.Main) {
                                                            themes.remove(installedTheme)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            navController.navigate(
                                                "${SettingsRoutes.ThemeDetail.route}/${themeEntry.id}"
                                            )
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
                                        onClick = {
                                            navController.navigate(
                                                "${SettingsRoutes.IconPackDetail.route}/${iconPackEntry.id}"
                                            )
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
        placeholder = { Text(placeholderText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
fun ThemeStoreCard(
    themeEntry: ThemeEntry,
    isInstalled: Boolean,
    onInstallClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onClick: () -> Unit,
) {
    val name = themeEntry.manifest.name
    val progress = StoreManager.downloadProgress[themeEntry.id]
    PreferenceTemplate(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        startWidget = {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(StoreManager.getThemeIconUrl(themeEntry.id))
                        .placeholder(drawables.palette)
                        .error(drawables.palette)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                contentDescription = null,
                modifier = Modifier.size(48.dp).padding(8.dp).clip(RoundedCornerShape(8.dp)),
            )
        },
        title = {
            Text(text = name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        description = {
            Column {
                Text(
                    text = themeEntry.id,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = Typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    if (progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        LinearProgressIndicator(
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
    iconPackEntry: IconPackEntry,
    isInstalled: Boolean,
    onInstallClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onClick: () -> Unit,
) {
    val name = iconPackEntry.manifest.name
    val id = iconPackEntry.manifest.id
    val progress = StoreManager.downloadProgress[id]
    PreferenceTemplate(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        startWidget = {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(StoreManager.getIconPackIconUrl(id))
                        .placeholder(drawables.widgets)
                        .error(drawables.widgets)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                contentDescription = null,
                modifier = Modifier.size(48.dp).padding(8.dp).clip(RoundedCornerShape(8.dp)),
            )
        },
        title = {
            Text(text = name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        description = {
            Column {
                Text(
                    text = id,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = Typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    if (progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        LinearProgressIndicator(
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
                            XedDropdownMenuItem(
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
                            XedDropdownMenuItem(
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
        placeholder = {
            Text(stringResource(strings.search_extensions), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}

private fun applySort(
    currentSortOption: ExtensionSortOptions,
    filtered: List<Extension>,
): List<Extension> =
    when (currentSortOption) {
        ExtensionSortOptions.NAME -> filtered.sortedBy { it.name }
        ExtensionSortOptions.RATING -> filtered.sortedByDescending { it.rating }
        ExtensionSortOptions.DOWNLOAD_COUNT -> filtered.sortedByDescending { it.downloads }
        ExtensionSortOptions.PUBLISH_DATE -> filtered.sortedByDescending { it.createdAt }
        ExtensionSortOptions.UPDATE_DATE -> filtered.sortedByDescending { it.updatedAt }
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
        val outdatedClient = minAppVersion != null && xedVersionCode < minAppVersion

        val currentArchitecture = Build.SUPPORTED_ABIS.firstOrNull()
        val supportedArchitecture =
            currentArchitecture == null || it.supportedArchitectures?.contains(currentArchitecture) ?: true

        when (currentFilterOption) {
            ExtensionFilterOptions.ALL -> true
            ExtensionFilterOptions.SUPPORTED -> !outdatedClient && supportedArchitecture
            ExtensionFilterOptions.CRASHED -> extensionManager.isExtensionCrashed(it)
        }
    }
}
