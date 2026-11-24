package com.rk.store.ui

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.LeadingIconXedTab
import com.rk.components.compose.preferences.base.RefreshablePreferenceLayoutLazyColumn
import com.rk.resources.strings
import com.rk.store.StoreRegistry
import com.rk.store.StoreSectionContext
import com.rk.store.actions.installAutoDetect

/**
 * The store: every package catalog this build provides, as tabs.
 *
 * The tabs are the [StoreSection]s registered in [StoreRegistry]; the store only knows how to display them and how to
 * install a package that the user opened from storage. Themes and icon packs come from this module, extensions from
 * `:features:extensions` when it is bundled and enabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(navController: NavController, query: String? = null, category: String? = null) {
    val activity = LocalActivity.current as? AppCompatActivity
    val scope = rememberCoroutineScope()

    val sections = StoreRegistry.sections.toList()
    val initialState = remember(category) { category?.let { StoreRegistry.sectionForId(it) } ?: sections.firstOrNull() }
    var selectedId by rememberSaveable { mutableStateOf(initialState?.id) }
    val selected = sections.firstOrNull { it.id == selectedId } ?: sections.firstOrNull()

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val searchQuery = rememberTextFieldState(query ?: "")

    LaunchedEffect(refreshKey, selected?.id) {
        selected?.load(forceRefresh = refreshKey > 0)
        isRefreshing = false
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            installAutoDetect(scope, uri, activity)
        }

    val sectionContext = remember(navController) { StoreSectionContext(navController, searchQuery) }

    Box {
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
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(strings.install_from_storage)) },
                )
            },
        ) {
            if (selected == null) {
                item { Text(stringResource(strings.loading), modifier = Modifier.padding(16.dp)) }
                return@RefreshablePreferenceLayoutLazyColumn
            }

            item { selected.SearchBar(sectionContext) }

            if (sections.size > 1) {
                item {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = sections.indexOf(selected),
                        modifier = Modifier.padding(bottom = 8.dp),
                        edgePadding = 16.dp,
                    ) {
                        sections.forEach { section ->
                            LeadingIconXedTab(
                                selected = section.id == selected.id,
                                onClick = { selectedId = section.id },
                                text = { Text(stringResource(section.labelRes)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(section.iconRes),
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            selected.catalog(this, sectionContext)

            item {
                // Add extra space so that FAB doesn't cover content
                Spacer(modifier = Modifier.height(88.dp))
            }
        }

        // Dialogs owned by the sections (e.g. the extension security scan report).
        sections.forEach { section -> section.Overlays() }
    }
}
