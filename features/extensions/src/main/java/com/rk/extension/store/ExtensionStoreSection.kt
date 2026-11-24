package com.rk.extension.store

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rk.App
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.settings.StoreCategories
import com.rk.common.PackageType
import com.rk.components.XedDropdownMenuItem
import com.rk.extension.Extension
import com.rk.extension.UpdatableExtension
import com.rk.extension.extensionManager
import com.rk.packages.model.Package
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.extension.ExtensionDialogManager
import com.rk.settings.extension.ExtensionDialogRenderer
import com.rk.store.CatalogLoadState
import com.rk.store.CatalogSection
import com.rk.store.PackageActionContext
import com.rk.store.StoreSectionContext
import com.rk.store.ui.PackageCatalogBody
import com.rk.theme.Typography
import com.rk.utils.toast
import java.io.File

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

/**
 * Catalog of extensions plus the install/update/uninstall flow for them.
 *
 * This is the extensions feature's contribution to [com.rk.store.StoreRegistry]: without the feature (or with the user
 * toggle off) nothing is registered here, and the store keeps showing themes and icon packs.
 */
class ExtensionStoreSection : CatalogSection() {
    override val type = PackageType.EXTENSION

    override val id = StoreCategories.EXTENSIONS

    override val labelRes = strings.ext

    override val iconRes = drawables.extension

    override val emptyLabelRes = strings.no_ext

    private val dialogManager = ExtensionDialogManager()

    private var sortOption by mutableStateOf(ExtensionSortOptions.DOWNLOAD_COUNT)

    private var filterOption by mutableStateOf(ExtensionFilterOptions.ALL)

    override fun detailRoute(pkg: Package): String = "${SettingsRoutes.ExtensionDetail.route}/${pkg.id}"

    override fun packages(): List<Package> = extensionManager.getSyncedExtensions()

    override fun getPackage(id: String): Package? = extensionManager.getExtension(id)

    override fun isInstalled(id: String): Boolean = extensionManager.isInstalled(id)

    override fun localCount(): Int = extensionManager.installedExtensions.value.size

    override fun storeCount(): Int = extensionManager.storeExtension.value.size

    override suspend fun indexLocalNow() = extensionManager.indexLocalExtensions()

    override suspend fun indexStoreNow() = extensionManager.indexStoreExtensions()

    override suspend fun invalidateSize(pkg: Package) {
        if (pkg is Extension) extensionManager.invalidateSize(pkg)
    }

    override fun settingsRoute(pkg: Package): String? =
        if (pkg.hasSettings && isInstalled(pkg.id)) {
            "${SettingsRoutes.ExtensionSettings.route}/${pkg.id}"
        } else {
            null
        }

    override fun install(pkg: Package, action: PackageActionContext) {
        val extension = pkg as? Extension ?: return
        requestExtensionInstall(extension, action, dialogManager)
    }

    override fun update(pkg: Package, action: PackageActionContext) {
        val extension = pkg as? Extension ?: return
        val updatable = extension as? UpdatableExtension
        if (updatable == null) {
            requestExtensionInstall(extension, action, dialogManager)
        } else {
            requestExtensionUpdate(updatable, action, dialogManager)
        }
    }

    override fun uninstall(pkg: Package, action: PackageActionContext) {
        val extension = pkg as? Extension ?: return
        requestExtensionUninstall(extension, action)
    }

    override suspend fun installFromFile(file: File, action: PackageActionContext): Boolean =
        installExtensionFromFile(file, action, dialogManager)

    @Composable
    override fun DetailActions(pkg: Package, navController: NavController, scope: RowScope) {
        val route = settingsRoute(pkg) ?: return

        IconButton(onClick = { navController.navigate(route) }) {
            Icon(painter = painterResource(drawables.settings), contentDescription = stringResource(strings.settings))
        }
    }

    @Composable
    override fun StatusBadge(pkg: Package) {
        val extension = pkg as? Extension ?: return

        var crashed by remember(pkg.id) { mutableStateOf(extensionManager.isExtensionCrashed(extension)) }
        if (!crashed) return

        Text(
            text = " • ${stringResource(strings.disabled_crashed)}",
            style = Typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.padding(start = 2.dp).clickable {
                    extensionManager.setExtensionCrashed(extension, false)
                    toast("Re-enabled extension. Restart the app for changes to take effect.")
                    crashed = false
                },
        )
    }

    @Composable
    override fun ActionExtras(pkg: Package, action: PackageActionContext) {
        val extension = pkg as? Extension ?: return
        val recommendations = getRecommendations(extension)
        if (recommendations.isEmpty()) return

        FilledIconButton(
            onClick = { dialogManager.showRecommendations(extension, recommendations) },
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(
                painter = painterResource(drawables.widgets),
                contentDescription = stringResource(strings.recommendations),
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun SearchBar(section: StoreSectionContext) {
        var searchOptionsExpanded by remember { mutableStateOf(false) }

        SearchBarDefaults.InputField(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            state = section.searchQuery,
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
                            ExtensionSortOptions.entries.forEach { option ->
                                XedDropdownMenuItem(
                                    text = { Text(stringResource(option.stringRes)) },
                                    onClick = {
                                        sortOption = option
                                        searchOptionsExpanded = false
                                    },
                                    leadingIcon = { RadioButton(sortOption == option, null) },
                                )
                            }

                            HorizontalDivider()

                            Text(
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp),
                                text = stringResource(strings.filter_options),
                                style = Typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            ExtensionFilterOptions.entries.forEach { option ->
                                XedDropdownMenuItem(
                                    text = { Text(stringResource(option.stringRes)) },
                                    onClick = {
                                        filterOption = option
                                        searchOptionsExpanded = false
                                    },
                                    leadingIcon = { RadioButton(filterOption == option, null) },
                                )
                            }
                        }
                    }

                    IconButton({ section.searchQuery.clearText() }) {
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

    override fun catalog(list: LazyListScope, section: StoreSectionContext) {
        list.item { ExtensionCatalog(section, loadState, sortOption, filterOption) }
    }

    @Composable
    override fun Overlays() {
        ExtensionDialogRenderer(dialogManager)
    }
}

@Composable
private fun ExtensionCatalog(
    section: StoreSectionContext,
    loadState: CatalogLoadState,
    sortOption: ExtensionSortOptions,
    filterOption: ExtensionFilterOptions,
) {
    val installedExtensions by extensionManager.installedExtensions.collectAsStateWithLifecycle()
    val storeExtensions by extensionManager.storeExtension.collectAsStateWithLifecycle()
    val query = section.searchQuery.text

    val extensions by
        remember(installedExtensions, storeExtensions, query, sortOption, filterOption) {
            derivedStateOf {
                sortExtensions(
                    sortOption,
                    filterExtensions(query, extensionManager.getSyncedExtensions(), filterOption),
                )
            }
        }

    PackageCatalogBody(
        packages = extensions,
        isLoading = loadState.isLoading,
        emptyLabel = stringResource(strings.no_ext),
        navController = section.navController,
    )
}

private fun sortExtensions(sortOption: ExtensionSortOptions, extensions: List<Extension>): List<Extension> =
    when (sortOption) {
        ExtensionSortOptions.NAME -> extensions.sortedBy { it.name }
        ExtensionSortOptions.RATING -> extensions.sortedByDescending { it.rating }
        ExtensionSortOptions.DOWNLOAD_COUNT -> extensions.sortedByDescending { it.downloads }
        ExtensionSortOptions.PUBLISH_DATE -> extensions.sortedByDescending { it.createdAt }
        ExtensionSortOptions.UPDATE_DATE -> extensions.sortedByDescending { it.updatedAt }
    }

private fun filterExtensions(
    query: CharSequence,
    extensions: List<Extension>,
    filterOption: ExtensionFilterOptions,
): List<Extension> {
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

        when (filterOption) {
            ExtensionFilterOptions.ALL -> true
            ExtensionFilterOptions.SUPPORTED -> !outdatedClient && supportedArchitecture
            ExtensionFilterOptions.CRASHED -> extensionManager.isExtensionCrashed(it)
        }
    }
}
