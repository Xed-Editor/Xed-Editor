package com.rk.settings.extension

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.App.Companion.extensionManager
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayoutLazyColumn
import com.rk.extension.Extension
import com.rk.extension.StoreExtension
import com.rk.extension.github.GitHubApiException
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.theme.Typography
import com.rk.utils.openUrl
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class ExtensionCategories(val drawableRes: Int, val stringRes: Int) {
    ALL(drawables.widgets, strings.all),
    LOCAL(drawables.sd_card, strings.local),
    STORE(drawables.store, strings.store),
}

private enum class ExtensionSortOptions(val stringRes: Int) {
    NAME(strings.name),
    RATING(strings.rating),
    SIZE(strings.size),
    DATE_ADDED(strings.date_added),
}

private enum class ExtensionFilterOptions(val stringRes: Int) {
    ALL(strings.all),
    VERIFIED(strings.verified),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var currentSortOption by remember { mutableStateOf(ExtensionSortOptions.NAME) }
    var currentFilterOption by remember { mutableStateOf(ExtensionFilterOptions.ALL) }
    val searchQuery = rememberTextFieldState("")

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            runCatching {
                extensionManager.indexLocalExtensions()
                extensionManager.indexStoreExtensions()
            }
        }
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            installExtensionFromUri(scope, uri, activity)
        }

    var selectedCategory by remember { mutableStateOf(ExtensionCategories.ALL) }
    val extensions by
        remember(selectedCategory) {
            derivedStateOf {
                when (selectedCategory) {
                    ExtensionCategories.ALL -> extensionManager.localExtensions + extensionManager.storeExtension
                    ExtensionCategories.LOCAL -> extensionManager.localExtensions
                    ExtensionCategories.STORE -> extensionManager.storeExtension
                }.map { it.value }
            }
        }
    val filteredExtensions by
        remember(searchQuery.text, extensions, currentFilterOption) {
            derivedStateOf { applyFilter(searchQuery, extensions, currentFilterOption) }
        }
    val sortedExtension by
        produceState(filteredExtensions, filteredExtensions, currentSortOption) {
            value = applySort(currentSortOption, filteredExtensions)
        }

    var isIndexing by remember { mutableStateOf(false) }
    var isFetching by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, selectedCategory) {
        if (selectedCategory == ExtensionCategories.STORE) return@LaunchedEffect
        isIndexing = true
        extensionManager.indexLocalExtensions()
        isIndexing = false
        isRefreshing = false
    }

    LaunchedEffect(refreshKey, selectedCategory) {
        if (selectedCategory == ExtensionCategories.LOCAL) return@LaunchedEffect
        try {
            isFetching = true
            extensionManager.indexStoreExtensions()
        } catch (err: GitHubApiException) {
            val message = buildString {
                appendLine(err.message)
                appendLine("Response Code: ${err.statusCode}")

                if (err.response.isNotBlank()) {
                    appendLine("Response: ${err.response}")
                }
            }

            toast(message)
        } finally {
            isFetching = false
            isRefreshing = false
        }
    }

    RefreshablePreferenceLayoutLazyColumn(
        label = stringResource(strings.ext),
        isExpandedScreen = false,
        backArrowVisible = true,
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshKey++
        },
        fab = {
            ExtendedFloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("application/zip")) },
                icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(strings.install_from_storage)) },
            )
        },
    ) {
        item {
            InfoBlock(
                modifier =
                    Modifier.clickable { activity?.openUrl("https://xed-editor.github.io/Xed-Docs/docs/extensions/") }
                        .padding(bottom = 16.dp),
                icon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
                text = stringResource(strings.info_ext),
            )
        }

        item {
            ExtensionSearchBar(
                searchQuery = searchQuery,
                currentSortOption = currentSortOption,
                currentFilterOption = currentFilterOption,
                onSortOptionChange = { currentSortOption = it },
                onFilterOptionChange = { currentFilterOption = it },
            )
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                ExtensionCategories.entries.forEach { category ->
                    SegmentedButton(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(stringResource(category.stringRes)) },
                        icon = { Icon(painterResource(category.drawableRes), null) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = category.ordinal,
                                count = ExtensionCategories.entries.size,
                            ),
                    )
                }
            }
        }

        if (sortedExtension.isNotEmpty() || isIndexing || isFetching) {
            items(sortedExtension, key = { it.id }) { extension ->
                var installState by remember {
                    mutableStateOf(
                        if (extensionManager.isInstalled(extension.id)) {
                            InstallState.Installed
                        } else {
                            InstallState.Idle
                        }
                    )
                }

                ExtensionCard(
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
                    extension = extension,
                    installState = installState,
                    onInstallClick = {
                        runExtensionInstallAction(extension, { installState = it }, scope, context, activity)
                    },
                    onUninstallClick = { runExtensionUninstallAction(extension, { installState = it }, activity) },
                    onClick = { navController.navigate("${SettingsRoutes.ExtensionDetail.route}/${it.id}") },
                )
            }

            if (isIndexing || isFetching) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(text = stringResource(strings.loading))
                    }
                }
            }
        } else {
            item { PreferenceGroup { Text(text = stringResource(strings.no_ext), modifier = Modifier.padding(16.dp)) } }
        }
    }
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

private suspend fun applySort(
    currentSortOption: ExtensionSortOptions,
    filteredExtensions: List<Extension>,
): List<Extension> =
    when (currentSortOption) {
        ExtensionSortOptions.NAME -> filteredExtensions.sortedBy { it.name }
        ExtensionSortOptions.RATING -> filteredExtensions.sortedBy { it.id } // TODO: RATING
        ExtensionSortOptions.SIZE -> {
            val sizes = filteredExtensions.associateWith { ext -> ext.calcSize() }
            filteredExtensions.sortedBy { sizes[it] }
        }

        ExtensionSortOptions.DATE_ADDED -> filteredExtensions.sortedBy { it.id } // TODO: DATE_ADDED
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
    return filteredBySearchQuery.filter {
        when (currentFilterOption) {
            ExtensionFilterOptions.ALL -> true
            ExtensionFilterOptions.VERIFIED -> (it as? StoreExtension)?.verified == true
        }
    }
}
