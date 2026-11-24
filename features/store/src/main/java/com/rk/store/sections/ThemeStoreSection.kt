package com.rk.store.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rk.App.Companion.themeManager
import com.rk.DefaultScope
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.settings.StoreCategories
import com.rk.common.PackageType
import com.rk.packages.InstallState
import com.rk.packages.StoreManager
import com.rk.packages.model.Package
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.store.CatalogLoadState
import com.rk.store.CatalogSection
import com.rk.store.PackageActionContext
import com.rk.store.StoreSectionContext
import com.rk.store.actions.applyThemeAfterInstall
import com.rk.store.actions.downloadPackageFile
import com.rk.store.actions.showDownloadNotification
import com.rk.store.ui.PackageCatalogBody
import com.rk.store.ui.StoreSearchBar
import com.rk.store.ui.applyPackageFilter
import com.rk.theme.ThemePackage
import com.rk.utils.dialogRes
import com.rk.utils.errorDialog
import com.rk.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Catalog of themes: installed themes plus the ones published in the store.
 *
 * Themes live in `:core:main`; listing, installing and uninstalling them here means browsing themes no longer requires
 * the extensions feature (or its store UI) to be present in the build.
 */
class ThemeStoreSection : CatalogSection() {
    override val type = PackageType.THEME

    override val id = StoreCategories.THEMES

    override val labelRes = strings.themes

    override val iconRes = drawables.palette

    override val emptyLabelRes = strings.no_themes

    override fun detailRoute(pkg: Package): String = "${SettingsRoutes.ThemeDetail.route}/${pkg.id}"

    override fun packages(): List<Package> = themeManager.getSyncedThemes()

    override fun getPackage(id: String): Package? = themeManager.getTheme(id)

    override fun isInstalled(id: String): Boolean = themeManager.isInstalled(id)

    override fun localCount(): Int = themeManager.localThemes.value.size

    override fun storeCount(): Int = themeManager.storeThemes.value.size

    override suspend fun indexLocalNow() = themeManager.indexLocalThemes()

    override suspend fun indexStoreNow() = themeManager.indexStoreThemes()

    override suspend fun invalidateSize(pkg: Package) {
        if (pkg is ThemePackage) themeManager.invalidateSize(pkg)
    }

    override fun install(pkg: Package, action: PackageActionContext) = installTheme(pkg.id, pkg.name, action)

    override fun update(pkg: Package, action: PackageActionContext) = installTheme(pkg.id, pkg.name, action)

    override fun uninstall(pkg: Package, action: PackageActionContext) {
        dialogRes(
            activity = action.activity,
            title = strings.uninstall_theme_dialog.getString(),
            msg = strings.uninstall_theme_dialog_desc.getFilledString(pkg.name),
            okRes = strings.uninstall,
            onOk = {
                action.scope.launch(Dispatchers.Main) {
                    themeManager.uninstallTheme(pkg.id)
                    action.updateInstallState(InstallState.Idle)
                }
            },
            onCancel = {},
        )
    }

    override suspend fun installFromFile(file: File, action: PackageActionContext): Boolean =
        withContext(Dispatchers.IO) {
            themeManager.installTheme(file)?.let { installedId ->
                withContext(Dispatchers.Main) {
                    applyThemeAfterInstall(installedId)
                    toast(strings.installed)
                }
                true
            } ?: false
        }

    @Composable
    override fun SearchBar(section: StoreSectionContext) {
        StoreSearchBar(section.searchQuery, stringResource(strings.search_themes))
    }

    override fun catalog(list: LazyListScope, section: StoreSectionContext) {
        list.item { ThemeCatalog(section, loadState) }
    }
}

@Composable
private fun ThemeCatalog(section: StoreSectionContext, loadState: CatalogLoadState) {
    val localThemes by themeManager.localThemes.collectAsStateWithLifecycle()
    val storeThemes by themeManager.storeThemes.collectAsStateWithLifecycle()
    val query = section.searchQuery.text

    val themes by
        remember(localThemes, storeThemes, query) {
            derivedStateOf { applyPackageFilter(query, themeManager.getSyncedThemes()).sortedBy { it.name } }
        }

    PackageCatalogBody(
        packages = themes,
        isLoading = loadState.isLoading,
        emptyLabel = stringResource(strings.no_themes),
        navController = section.navController,
    )
}

private fun installTheme(id: String, name: String, action: PackageActionContext) {
    DefaultScope.launch(Dispatchers.IO) {
        var errorMsg: String? = null
        var success = false

        try {
            val file =
                downloadPackageFile(
                    context = action.context,
                    id = id,
                    name = name,
                    url = StoreManager.themeDownloadUrl(id),
                    cacheFileName = "theme_$id.xed",
                )

            if (file != null) {
                success = themeManager.installTheme(file) != null
                if (!success) errorMsg = "Failed to install theme"
                file.delete()
            } else {
                errorMsg = "Download failed"
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: strings.unknown_err.getString()
        } finally {
            StoreManager.clearInstall(id)

            withContext(Dispatchers.Main) {
                if (success) {
                    showDownloadNotification(action.context, id, name, 1f, isFinished = true)
                    applyThemeAfterInstall(id)
                } else {
                    showDownloadNotification(action.context, id, name, 0f, isFinished = true, errorMessage = errorMsg)
                    errorDialog(action.activity, msg = errorMsg ?: strings.unknown_err.getString())
                }
            }
        }
    }
}
