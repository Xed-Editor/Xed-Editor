package com.rk.settings.extension

import android.os.Build
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.App
import com.rk.App.Companion.iconPackManager
import com.rk.activities.settings.SettingsRoutes
import com.rk.common.PackageType
import com.rk.components.XedDropdownMenuItem
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayoutLazyColumn
import com.rk.extension.Extension
import com.rk.extension.extensionManager
import com.rk.extension.manager.StoreManager
import com.rk.extension.model.Package
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.ThemeManager
import com.rk.theme.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
fun StoreScreen(navController: NavController, query: String?, category: String? = null) {
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
                    runCatching {
                        extensionManager.indexLocalExtensions()
                        ThemeManager.indexLocalThemes()
                        iconPackManager.indexIconPacks()
                    }
                    isIndexing = false
                }
            val storeJob =
                launch(Dispatchers.IO) {
                    runCatching {
                        extensionManager.indexStoreExtensions()
                        StoreManager.fetchThemes()
                        StoreManager.fetchIconPacks()
                    }
                    isFetching = false
                }

            localJob.join()
            storeJob.join()
            isRefreshing = false
        }
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            installAutoDetect(scope, uri, activity)
        }

    val extensions by remember {
        derivedStateOf {
            val all = extensionManager.getSyncedExtensions()
            val filtered = applyExtensionsFilter(searchQuery, all, currentFilterOption)
            applyExtensionsSort(currentSortOption, filtered)
        }
    }

    val sortedThemes by remember {
        derivedStateOf {
            val all = ThemeManager.getSyncedThemes()
            val filtered = applyGenericFilter(searchQuery, all)
            filtered.sortedBy { it.name }
        }
    }

    val sortedIconPacks by remember {
        derivedStateOf {
            val all = iconPackManager.getSyncedIconPacks()
            val filtered = applyGenericFilter(searchQuery, all)
            filtered.sortedBy { it.name }
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
                packageList(
                    packages = extensions,
                    isIndexing = isIndexing,
                    isFetching = isFetching,
                    navController = navController,
                    dialogManager = dialogManager,
                    noPkgRes = strings.no_ext,
                )
            }
            StoreCategory.THEMES -> {
                packageList(
                    packages = sortedThemes,
                    isIndexing = isIndexing,
                    isFetching = isFetching,
                    navController = navController,
                    dialogManager = dialogManager,
                    noPkgRes = strings.no_themes,
                )
            }
            StoreCategory.ICON_PACKS -> {
                packageList(
                    packages = sortedIconPacks,
                    isIndexing = isIndexing,
                    isFetching = isFetching,
                    navController = navController,
                    dialogManager = dialogManager,
                    noPkgRes = strings.no_icon_packs,
                )
            }
        }

        item {
            // Add extra space so that FAB doesn't cover content
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

private fun LazyListScope.packageList(
    packages: List<Package>,
    isIndexing: Boolean,
    isFetching: Boolean,
    navController: NavController,
    dialogManager: ExtensionDialogManager,
    noPkgRes: Int,
) {
    item {
        val context = LocalContext.current
        val activity = LocalActivity.current as? AppCompatActivity
        val scope = rememberCoroutineScope()

        if (packages.isNotEmpty() || isIndexing || isFetching) {
            PreferenceGroup {
                packages.forEach { pkg ->
                    key(pkg.id) {
                        val installState = rememberPackageInstallState(pkg)

                        PackageCard(
                            pkg = pkg,
                            installState = installState,
                            onInstallClick = {
                                runPackageInstallAction(pkg, {}, context, activity, dialogManager)
                            },
                            onUninstallClick = {
                                runPackageUninstallAction(pkg, {}, scope, activity)
                            },
                            onUpdateClick = {
                                runPackageUpdateAction(pkg, {}, context, activity, dialogManager)
                            },
                            onClick = {
                                val route =
                                    when (pkg.type) {
                                        PackageType.EXTENSION -> SettingsRoutes.ExtensionDetail.route
                                        PackageType.THEME -> SettingsRoutes.ThemeDetail.route
                                        PackageType.ICON_PACK -> SettingsRoutes.IconPackDetail.route
                                    }
                                navController.navigate("$route/${pkg.id}")
                            },
                        )
                    }
                }
            }

            if (isIndexing || isFetching) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(text = stringResource(strings.loading))
                }
            }
        } else {
            PreferenceGroup {
                Text(
                    text = stringResource(noPkgRes),
                    modifier = Modifier.padding(16.dp),
                )
            }
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

private fun applyExtensionsSort(
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

private fun applyExtensionsFilter(
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

private fun applyGenericFilter(
    searchQuery: TextFieldState,
    packages: List<Package>,
): List<Package> {
    val query = searchQuery.text
    return if (query.isEmpty()) {
        packages
    } else {
        packages.filter { pkg ->
            val labelMatch = pkg.name.contains(query, ignoreCase = true)
            val descriptionMatch = pkg.description?.contains(query, ignoreCase = true) == true
            val tagMatch = pkg.tags.any { it.contains(query, ignoreCase = true) }
            val authorMatch = pkg.author.displayName.contains(query, ignoreCase = true)
            val authorGithubMatch = pkg.author.github?.contains(query, ignoreCase = true) == true
            labelMatch || descriptionMatch || tagMatch || authorMatch || authorGithubMatch
        }
    }
}
