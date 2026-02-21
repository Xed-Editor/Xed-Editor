package com.rk.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.exec.ShellUtils
import com.rk.file.FileObject
import com.rk.file.FileOperations
import com.rk.file.FileWrapper
import com.rk.resources.fillPlaceholders
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.formatFileSize
import com.rk.utils.rememberNumberFormatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContentProgress(val totalSize: Long, val totalItems: Long)

enum class PropertyRoutes(val label: String, val route: String) {
    GENERAL(strings.general.getString(), "general"),
    ADVANCED(strings.advanced.getString(), "advanced"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesDialog(file: FileObject, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = 0) { PropertyRoutes.entries.size }
    val scope = rememberCoroutineScope()

    val scrollStates = PropertyRoutes.entries.map { rememberScrollState() }
    val currentScrollState = scrollStates[pagerState.currentPage]
    val showHorizontalDivider by
        remember(pagerState.currentPage) { derivedStateOf { currentScrollState.canScrollForward } }

    XedDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Text(
                text = stringResource(strings.properties),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
            )

            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                PropertyRoutes.entries.forEachIndexed { index, destination ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(text = destination.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = PropertyRoutes.entries.size,
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollStates[page]).padding(all = 24.dp),
                ) {
                    when (PropertyRoutes.entries[page]) {
                        PropertyRoutes.GENERAL -> GeneralProperties(file)
                        PropertyRoutes.ADVANCED -> AdvancedProperties(file)
                    }
                }
            }

            if (showHorizontalDivider) HorizontalDivider()

            Box(modifier = Modifier.padding(start = 24.dp, bottom = 24.dp, end = 24.dp).align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(text = stringResource(strings.close)) }
            }
        }
    }
}

@Composable
fun GeneralProperties(file: FileObject) {
    var size by remember { mutableStateOf(formatFileSize(0)) }
    var itemsCount by remember { mutableStateOf("0") }
    val numberFormatter = rememberNumberFormatter()

    val lastModified =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
            .format(Date(file.lastModified()))

    LaunchedEffect(file) {
        if (file.isFile()) {
            size = formatFileSize(file.length())
        } else {
            FileOperations.calculateContent(file) {
                size = formatFileSize(it.totalSize)
                itemsCount = numberFormatter.format(it.totalItems)
            }
        }
    }

    InfoRow(stringResource(strings.name), file.getName())
    InfoRow(stringResource(strings.path), file.getAbsolutePath())
    InfoRow(
        label = stringResource(strings.type),
        value =
            if (file.isDirectory()) {
                stringResource(strings.folder)
            } else {
                stringResource(strings.file)
            },
    )
    if (file.isDirectory()) {
        InfoRow(
            label = stringResource(strings.content),
            value = stringResource(strings.content_property).fillPlaceholders(itemsCount, size),
        )
    } else {
        InfoRow(label = stringResource(strings.size), value = size)
    }
    InfoRow(stringResource(strings.last_modified), lastModified)
}

@Composable
fun AdvancedProperties(file: FileObject) {
    InfoRow(stringResource(strings.permissions), getPseudoPermissions(file))
    InfoRow(stringResource(strings.wrapper_type), file.javaClass.simpleName)

    if (file is FileWrapper && file.isFile()) {
        var fileInfo by remember { mutableStateOf(strings.loading.getString()) }
        InfoRow(label = "file", fileInfo)

        LaunchedEffect(file) {
            val result = withContext(Dispatchers.IO) { ShellUtils.run("file", file.getAbsolutePath()) }

            if (result.exitCode == 0) {
                fileInfo =
                    result.output
                        .removePrefix(file.getAbsolutePath())
                        .removePrefix(file.getCanonicalPath())
                        .removePrefix(":")
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    OutlinedTextField(value = value, onValueChange = {}, label = { Text(label) }, readOnly = true)
}

private fun getPseudoPermissions(file: FileObject): String {
    val type =
        when {
            file.isDirectory() -> "d"
            file.isSymlink() -> "l"
            else -> "-"
        }

    val r = if (file.canRead()) "r" else "-"
    val w = if (file.canWrite()) "w" else "-"
    val x = if (file.canExecute()) "x" else "-"
    return "$type$r$w$x"
}
