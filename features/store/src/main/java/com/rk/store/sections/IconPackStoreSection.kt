package com.rk.store.sections

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rk.App.Companion.iconPackManager
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
import com.rk.store.actions.applyIconPackAfterInstall
import com.rk.store.actions.downloadPackageFile
import com.rk.store.actions.showDownloadNotification
import com.rk.store.ui.PackageCatalogBody
import com.rk.store.ui.StoreSearchBar
import com.rk.store.ui.applyPackageFilter
import com.rk.utils.dialogRes
import com.rk.utils.errorDialog
import com.rk.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Catalog of icon packs: installed packs plus the ones published in the store.
 *
 * Icon packs live in `:core:main`; listing, installing and uninstalling them here means browsing icon packs no longer
 * requires the extensions feature (or its store UI) to be present in the build.
 */
class IconPackStoreSection : CatalogSection() {
    override val type = PackageType.ICON_PACK

    override val id = StoreCategories.ICON_PACKS

    override val labelRes = strings.icon_packs

    override val iconRes = drawables.widgets

    override val emptyLabelRes = strings.no_icon_packs

    override fun detailRoute(pkg: Package): String = "${SettingsRoutes.IconPackDetail.route}/${pkg.id}"

    override fun packages(): List<Package> = iconPackManager.getSyncedIconPacks()

    override fun getPackage(id: String): Package? = iconPackManager.getIconPackPackage(id)

    override fun isInstalled(id: String): Boolean = iconPackManager.isInstalled(id)

    override fun localCount(): Int = iconPackManager.localIconPacks.value.size

    override fun storeCount(): Int = iconPackManager.storeIconPacks.value.size

    override suspend fun indexLocalNow() = iconPackManager.indexLocalPacks()

    override suspend fun indexStoreNow() = iconPackManager.indexStoreIconPacks()

    override fun install(pkg: Package, action: PackageActionContext) = installIconPack(pkg.id, pkg.name, action)

    override fun update(pkg: Package, action: PackageActionContext) = installIconPack(pkg.id, pkg.name, action)

    override fun uninstall(pkg: Package, action: PackageActionContext) {
        dialogRes(
            activity = action.activity,
            title = strings.uninstall_icon_pack_dialog.getString(),
            msg = strings.uninstall_icon_pack_dialog_desc.getFilledString(pkg.name),
            okRes = strings.uninstall,
            onOk = {
                action.scope.launch(Dispatchers.Main) {
                    iconPackManager.uninstallIconPack(pkg.id)
                    action.updateInstallState(InstallState.Idle)
                }
            },
            onCancel = {},
        )
    }

    override suspend fun installFromFile(file: File, action: PackageActionContext): Boolean =
        withContext(Dispatchers.IO) {
            iconPackManager.installIconPack(file)?.let { installedId ->
                withContext(Dispatchers.Main) {
                    applyIconPackAfterInstall(installedId)
                    toast(strings.installed)
                }
                true
            } ?: false
        }

    @Composable
    override fun SearchBar(section: StoreSectionContext) {
        StoreSearchBar(section.searchQuery, stringResource(strings.search_icon_packs))
    }

    override fun catalog(list: LazyListScope, section: StoreSectionContext) {
        list.item { IconPackCatalog(section, loadState) }
    }
}

@Composable
private fun IconPackCatalog(section: StoreSectionContext, loadState: CatalogLoadState) {
    val localIconPacks by iconPackManager.localIconPacks.collectAsStateWithLifecycle()
    val storeIconPacks by iconPackManager.storeIconPacks.collectAsStateWithLifecycle()
    val query = section.searchQuery.text

    val iconPacks by
        remember(localIconPacks, storeIconPacks, query) {
            derivedStateOf { applyPackageFilter(query, iconPackManager.getSyncedIconPacks()).sortedBy { it.name } }
        }

    PackageCatalogBody(
        packages = iconPacks,
        isLoading = loadState.isLoading,
        emptyLabel = stringResource(strings.no_icon_packs),
        navController = section.navController,
    )
}

private fun installIconPack(id: String, name: String, action: PackageActionContext) {
    DefaultScope.launch(Dispatchers.IO) {
        var errorMsg: String? = null
        var success = false

        try {
            val file =
                downloadPackageFile(
                    context = action.context,
                    id = id,
                    name = name,
                    url = StoreManager.iconPackDownloadUrl(id),
                    cacheFileName = "iconpack_$id.xed",
                )

            if (file != null) {
                success = iconPackManager.installIconPack(file) != null
                if (!success) errorMsg = "Failed to install icon pack"
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
                    applyIconPackAfterInstall(id)
                } else {
                    showDownloadNotification(action.context, id, name, 0f, isFinished = true, errorMessage = errorMsg)
                    errorDialog(action.activity, msg = errorMsg ?: strings.unknown_err.getString())
                }
            }
        }
    }
}
