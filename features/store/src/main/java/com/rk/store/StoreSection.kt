package com.rk.store

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.rk.common.PackageType
import com.rk.packages.InstallState
import com.rk.packages.model.Package
import com.rk.packages.model.UpdatablePackage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Everything a [StoreSection] needs to run a package operation started from the store UI. */
class PackageActionContext(
    val context: Context,
    val activity: AppCompatActivity?,
    val scope: CoroutineScope,
    val updateInstallState: (InstallState) -> Unit = {},
)

/** Ambient state of the store screen, handed to the currently selected [StoreSection]. */
class StoreSectionContext(val navController: NavController, val searchQuery: TextFieldState)

/**
 * Indexing progress of a catalog that is filled from local disk and from the remote registry.
 *
 * Backed by Compose snapshot state so a catalog body can render its progress without the store screen knowing anything
 * about the catalog's data source.
 */
class CatalogLoadState {
    var isIndexing by mutableStateOf(false)

    var isFetching by mutableStateOf(false)

    val isLoading: Boolean
        get() = isIndexing || isFetching
}

/**
 * One package catalog of the store plus the operations that can be performed on its packages.
 *
 * The store itself ships a section per built-in package type (themes and icon packs). Feature modules that own a
 * package type - notably `:features:extensions` - contribute their own section with [StoreRegistry.register], so the
 * store keeps working for the built-in types when such a module is missing from the build or disabled by the user.
 */
interface StoreSection {
    val type: PackageType

    /** Stable identifier, also used to restore the selected tab. */
    val id: String

    val labelRes: Int
    val iconRes: Int
    val emptyLabelRes: Int

    /** Route of the detail screen that shows [pkg]. */
    fun detailRoute(pkg: Package): String

    fun packages(): List<Package>

    fun getPackage(id: String): Package?

    fun isInstalled(id: String): Boolean

    suspend fun indexLocal()

    suspend fun indexStore()

    /** Indexes the catalogs as needed (or always when [forceRefresh]). */
    suspend fun load(forceRefresh: Boolean)

    fun installState(pkg: Package): InstallState

    fun install(pkg: Package, action: PackageActionContext) {}

    fun update(pkg: Package, action: PackageActionContext) {}

    fun uninstall(pkg: Package, action: PackageActionContext) {}

    suspend fun invalidateSize(pkg: Package) {}

    /**
     * Installs a package that the user opened from storage (or sent to the app). Returns whether the package was
     * installed.
     */
    suspend fun installFromFile(file: File, action: PackageActionContext): Boolean = false

    /** Route of the package's own settings screen, or null when the package has no settings. */
    fun settingsRoute(pkg: Package): String? = null

    /** Extra status shown next to the version, e.g. "disabled after a crash". */
    @Composable fun StatusBadge(pkg: Package) {}

    /** Extra actions in the detail screen's top bar. */
    @Composable fun DetailActions(pkg: Package, navController: NavController, scope: RowScope) {}

    /** Extra buttons next to the install/uninstall/update buttons. */
    @Composable fun ActionExtras(pkg: Package, action: PackageActionContext) {}

    /** Search field shown above the catalog body. */
    @Composable fun SearchBar(section: StoreSectionContext) {}

    /** Emits the catalog body into the store screen's lazy list. */
    fun catalog(list: LazyListScope, section: StoreSectionContext)

    /** Dialogs owned by this section. Composed by every screen that hosts package UI. */
    @Composable fun Overlays() {}
}

/**
 * Base class for a [StoreSection] backed by a local directory and the remote registry.
 *
 * Loads both catalogs lazily on first display and forces a reload on pull-to-refresh.
 */
abstract class CatalogSection : StoreSection {
    val loadState = CatalogLoadState()

    protected abstract fun localCount(): Int

    protected abstract fun storeCount(): Int

    final override suspend fun indexLocal() {
        loadState.isIndexing = true
        try {
            indexLocalNow()
        } finally {
            loadState.isIndexing = false
        }
    }

    final override suspend fun indexStore() {
        loadState.isFetching = true
        try {
            indexStoreNow()
        } finally {
            loadState.isFetching = false
        }
    }

    override suspend fun load(forceRefresh: Boolean) {
        val needsLocal = forceRefresh || localCount() == 0
        val needsStore = forceRefresh || storeCount() == 0

        if (!needsLocal && !needsStore) return

        coroutineScope {
            if (needsLocal) {
                launch { runCatching { indexLocal() } }
            }
            if (needsStore) {
                launch { runCatching { indexStore() } }
            }
        }
    }

    protected abstract suspend fun indexLocalNow()

    protected abstract suspend fun indexStoreNow()

    override fun installState(pkg: Package): InstallState {
        if (!isInstalled(pkg.id)) return InstallState.Idle
        return if (pkg is UpdatablePackage && pkg.hasUpdate()) {
            InstallState.Updatable
        } else {
            InstallState.Installed
        }
    }
}
