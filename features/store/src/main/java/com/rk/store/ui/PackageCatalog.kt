package com.rk.store.ui

import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.packages.model.Package
import com.rk.resources.strings
import com.rk.store.StoreRegistry
import com.rk.store.actions.rememberPackageActionContext
import com.rk.store.actions.rememberPackageInstallState
import com.rk.store.actions.runPackageInstallAction
import com.rk.store.actions.runPackageUninstallAction
import com.rk.store.actions.runPackageUpdateAction

/** Renders the packages of a catalog and wires their cards to the section that owns their type. */
@Composable
fun PackageCatalogBody(
    packages: List<Package>,
    isLoading: Boolean,
    emptyLabel: String,
    navController: NavController,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? AppCompatActivity
    val scope = rememberCoroutineScope()
    val action = rememberPackageActionContext(context, activity, scope)

    if (packages.isEmpty() && !isLoading) {
        PreferenceGroup { Text(text = emptyLabel, modifier = Modifier.padding(16.dp)) }
        return
    }

    PreferenceGroup {
        packages.forEach { pkg ->
            key(pkg.id) {
                PackageCard(
                    pkg = pkg,
                    installState = rememberPackageInstallState(pkg),
                    onInstallClick = { runPackageInstallAction(pkg, action) },
                    onUninstallClick = { runPackageUninstallAction(pkg, action) },
                    onUpdateClick = { runPackageUpdateAction(pkg, action) },
                    onClick = { clicked ->
                        StoreRegistry.sectionFor(clicked.type)?.let { section ->
                            navController.navigate(section.detailRoute(clicked))
                        }
                    },
                )
            }
        }
    }

    if (isLoading) {
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

/** Filter used by the package catalogs that only need a free-text search. */
fun applyPackageFilter(query: CharSequence, packages: List<Package>): List<Package> {
    if (query.isEmpty()) return packages

    return packages.filter { pkg ->
        val labelMatch = pkg.name.contains(query, ignoreCase = true)
        val descriptionMatch = pkg.description?.contains(query, ignoreCase = true) == true
        val tagMatch = pkg.tags.any { it.contains(query, ignoreCase = true) }
        val authorMatch = pkg.author.displayName.contains(query, ignoreCase = true)
        val authorGithubMatch = pkg.author.github?.contains(query, ignoreCase = true) == true
        labelMatch || descriptionMatch || tagMatch || authorMatch || authorGithubMatch
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StoreSearchBar(
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
