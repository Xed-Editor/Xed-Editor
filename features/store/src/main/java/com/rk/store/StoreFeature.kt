package com.rk.store

import android.app.Application
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.rk.App.Companion.iconPackManager
import com.rk.App.Companion.themeManager
import com.rk.activities.main.MainActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.common.PackageType
import com.rk.common.XedPackage
import com.rk.components.DialogProvider
import com.rk.components.DialogRegistry
import com.rk.extension.api.DynamicRoute
import com.rk.extension.api.IntentHandleRegistry
import com.rk.extension.api.IntentHandler
import com.rk.feature.Feature
import com.rk.file.FileWrapper
import com.rk.file.copyToTempDir
import com.rk.filetree.isXedPackage
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.SettingsCategory
import com.rk.settings.SettingsRegistry
import com.rk.store.actions.detectedManifest
import com.rk.store.sections.IconPackStoreSection
import com.rk.store.sections.ThemeStoreSection
import com.rk.store.ui.PackageDetail
import com.rk.store.ui.StoreScreen
import com.rk.store.ui.XedInstallDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The store: package catalogs, package detail screens and package installs.
 *
 * The feature is always enabled and only depends on `:core:main`, so themes and icon packs keep working in builds that
 * do not bundle `:features:extensions` and while the user has extensions disabled. Feature modules for other package
 * types (extensions) contribute their own [StoreSection] to [StoreRegistry].
 */
class StoreFeature : Feature {
    private var intentHandler: IntentHandler? = null
    private var dialogProvider: DialogProvider? = null
    private var settingsCategory: SettingsCategory? = null
    private val routes = mutableListOf<DynamicRoute>()
    private val sections = listOf(ThemeStoreSection(), IconPackStoreSection())

    override fun init(application: Application) {
        sections.forEach { StoreRegistry.register(it) }

        intentHandler =
            IntentHandler { file ->
                if (!file.isXedPackage()) return@IntentHandler false

                withContext(Dispatchers.IO) {
                    val context = application.applicationContext
                    val tempFile = file.copyToTempDir()
                    val tempDir = File(context.cacheDir, "pkg_temp_${System.currentTimeMillis()}")
                    tempDir.mkdirs()
                    try {
                        XedPackage.extract(tempFile, tempDir)

                        val type = XedPackage.detectPackageType(tempDir)
                        if (type != null) {
                            val manifest = detectedManifest(tempDir, type)
                            val packageIcon = tempDir.resolve("icon.png")
                            val iconFile = FileWrapper(packageIcon).takeIf { it.exists() }?.copyToTempDir()

                            withContext(Dispatchers.Main) {
                                MainActivity.instance
                                    ?.viewModel
                                    ?.openPackageInstallDialog(
                                        manifest,
                                        tempFile,
                                        iconFile,
                                    )
                            }
                        }
                    } finally {
                        tempDir.deleteRecursively()
                    }
                }
                return@IntentHandler true
            }
                .also { IntentHandleRegistry.register(it) }

        dialogProvider =
            DialogProvider {
                MainActivity.instance?.let { activity ->
                    val viewModel = activity.viewModel
                    val pendingInstall =
                        viewModel.pendingPackageInstall.collectAsStateWithLifecycle().value ?: return@let
                    XedInstallDialog(
                        pendingInstall.manifest,
                        pendingInstall.icon,
                        pendingInstall.packageFile,
                        viewModel::closePackageInstallDialog,
                    )
                }
            }
                .also { DialogRegistry.register(it) }

        settingsCategory =
            SettingsCategory(
                    label = strings.store.getString(),
                    icon = Icon.ResourceIcon(drawables.store),
                    route = SettingsRoutes.Store.route,
                ) {
                    // Extensions are a contributed catalog: only advertise them when that feature registered one.
                    if (StoreRegistry.sections.any { it.type == PackageType.EXTENSION }) {
                        strings.store_desc.getString()
                    } else {
                        strings.store_desc_no_extensions.getString()
                    }
                }
                .also { SettingsRegistry.registerCategory(it) }

        routes.add(
            DynamicRoute(
                "${SettingsRoutes.Store.route}?query={query}&category={category}",
                arguments =
                    listOf(
                        navArgument("query") {
                            nullable = true
                            type = NavType.StringType
                        },
                        navArgument("category") {
                            nullable = true
                            type = NavType.StringType
                        },
                    ),
            ) { navController, backStackEntry ->
                val query = backStackEntry.arguments?.getString("query")
                val category = backStackEntry.arguments?.getString("category")
                StoreScreen(navController = navController, query = query, category = category)
            }
        )
        routes.add(
            DynamicRoute("${SettingsRoutes.ThemeDetail.route}/{themeId}") { navController, backStackEntry ->
                val themeId = backStackEntry.arguments?.getString("themeId")
                val theme = themeId?.let { themeManager.getTheme(it) }
                PackageDetail(theme, navController)
            }
        )
        routes.add(
            DynamicRoute("${SettingsRoutes.IconPackDetail.route}/{iconPackId}") { navController, backStackEntry ->
                val iconPackId = backStackEntry.arguments?.getString("iconPackId")
                val iconPack = iconPackId?.let { iconPackManager.getIconPackPackage(it) }
                PackageDetail(iconPack, navController)
            }
        )

        routes.forEach { SettingsRegistry.registerRoute(it) }
    }

    override fun dispose(application: Application) {
        intentHandler?.let { IntentHandleRegistry.unregister(it) }
        dialogProvider?.let { DialogRegistry.unregister(it) }
        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        routes.forEach { SettingsRegistry.unregisterRoute(it) }
        routes.clear()
        sections.forEach { StoreRegistry.unregister(it) }
    }
}
