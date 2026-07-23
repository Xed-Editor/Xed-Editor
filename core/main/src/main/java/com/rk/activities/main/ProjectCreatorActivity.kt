package com.rk.activities.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.animations.NavigationAnimationTransitions
import com.rk.file.FileObject
import com.rk.file.toFileObject
import com.rk.icons.XedIcon
import com.rk.project.ProjectCategory
import com.rk.project.ProjectTemplate
import com.rk.project.ProjectTemplateRegistry
import com.rk.resources.fillPlaceholders
import com.rk.resources.getFilledString
import com.rk.resources.strings
import com.rk.theme.XedTheme
import com.rk.utils.formatFileSize
import kotlinx.coroutines.launch

private enum class ProjectCreatorPage {
    SELECTION,
    CONFIGURATION,
}

class ProjectCreatorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val root =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("root", Uri::class.java)
            } else {
                intent.getParcelableExtra("root")
            }
        val parentFolder = root?.toFileObject(expectedIsFile = false)

        setContent {
            XedTheme {
                ProjectCreatorContent(parentFolder)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ProjectCreatorContent(parentFolder: FileObject?) {

        val categories = ProjectTemplateRegistry.categories
        val categoryPagerState =
            rememberPagerState(
                initialPage = 0,
                pageCount = { categories.size },
            )

        var currentPage by remember { mutableStateOf(ProjectCreatorPage.SELECTION) }
        var selectedTemplate by remember { mutableStateOf<ProjectTemplate?>(null) }

        var isCreating by remember { mutableStateOf(false) }
        var creationProgress by remember { mutableFloatStateOf(0f) }
        var creationStatus by remember { mutableStateOf("") }

        val openFolder =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
                onResult = { uri ->
                    if (uri == null) {
                        return@rememberLauncherForActivityResult
                    }

                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    val folder = uri.toFileObject(expectedIsFile = false)
                    selectedTemplate?.let { template ->
                        isCreating = true
                        template.createProject(
                            this,
                            folder,
                            onProgress = { progress, status ->
                                creationProgress = progress
                                creationStatus = status
                            },
                            onComplete = { project ->
                                isCreating = false
                                runOnUiThread {
                                    strings.template_create_success.getFilledString(
                                        this@ProjectCreatorActivity,
                                        selectedTemplate?.label,
                                    )
                                    if (project != null) {
                                        MainActivity.instance?.drawerViewModel?.addFileTreeTab(project, true)
                                        finish()
                                    }
                                }
                            },
                        )
                    }
                },
            )

        if (isCreating) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(strings.wait)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = creationStatus,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(progress = { creationProgress })
                    }
                },
                confirmButton = {},
            )
        }

        BackHandler(enabled = currentPage == ProjectCreatorPage.CONFIGURATION) {
            currentPage = ProjectCreatorPage.SELECTION
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentPage) {
                                ProjectCreatorPage.SELECTION -> stringResource(strings.new_project)
                                ProjectCreatorPage.CONFIGURATION ->
                                    selectedTemplate?.label ?: stringResource(strings.new_project)
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when (currentPage) {
                                    ProjectCreatorPage.SELECTION -> finish()
                                    ProjectCreatorPage.CONFIGURATION -> currentPage = ProjectCreatorPage.SELECTION
                                }
                            }
                        ) {
                            Icon(
                                when (currentPage) {
                                    ProjectCreatorPage.SELECTION -> Icons.Default.Close
                                    ProjectCreatorPage.CONFIGURATION -> Icons.AutoMirrored.Filled.ArrowBack
                                },
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (currentPage == ProjectCreatorPage.CONFIGURATION) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().imePadding(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = { currentPage = ProjectCreatorPage.SELECTION }) {
                            Text(stringResource(strings.back))
                        }
                        Button(
                            onClick = {
                                if (parentFolder != null) {
                                    isCreating = true
                                    selectedTemplate?.createProject(
                                        this@ProjectCreatorActivity,
                                        parentFolder,
                                        onProgress = { progress, status ->
                                            creationProgress = progress
                                            creationStatus = status
                                        },
                                        onComplete = { project ->
                                            isCreating = false
                                            runOnUiThread {
                                                strings.template_create_success.getFilledString(
                                                    this@ProjectCreatorActivity,
                                                    selectedTemplate?.label,
                                                )
                                                if (project != null) {
                                                    MainActivity.instance
                                                        ?.drawerViewModel
                                                        ?.addFileTreeTab(project, true)
                                                    finish()
                                                }
                                            }
                                        },
                                    )
                                } else {
                                    openFolder.launch(null)
                                }
                            },
                            enabled = (selectedTemplate?.validConfiguration ?: false) && !isCreating,
                        ) {
                            Text(stringResource(strings.create))
                        }
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = currentPage,
                modifier = Modifier.padding(padding).fillMaxSize(),
                transitionSpec = {
                    if (targetState > initialState) {
                        NavigationAnimationTransitions.enterTransition togetherWith
                            NavigationAnimationTransitions.exitTransition using
                            null
                    } else {
                        NavigationAnimationTransitions.popEnterTransition togetherWith
                            NavigationAnimationTransitions.popExitTransition using
                            null
                    }
                },
                label = "page_transition",
            ) { page ->
                when (page) {
                    ProjectCreatorPage.SELECTION -> {
                        TemplateSelectionPage(
                            categories = categories,
                            categoryPagerState = categoryPagerState,
                            onTemplateSelect = { template ->
                                selectedTemplate = template
                                currentPage = ProjectCreatorPage.CONFIGURATION
                            },
                        )
                    }
                    ProjectCreatorPage.CONFIGURATION -> {
                        selectedTemplate?.let {
                            TemplateConfigurationPage(template = it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateSelectionPage(
    categories: List<ProjectCategory>,
    categoryPagerState: PagerState,
    onTemplateSelect: (ProjectTemplate) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        if (categories.isNotEmpty()) {
            PrimaryScrollableTabRow(
                selectedTabIndex = categoryPagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 0.dp,
            ) {
                categories.forEachIndexed { index, category ->
                    LeadingIconTab(
                        selected = categoryPagerState.currentPage == index,
                        onClick = { scope.launch { categoryPagerState.animateScrollToPage(index) } },
                        text = { Text(category.label) },
                        icon = {
                            category.icon?.let {
                                XedIcon(it, modifier = Modifier.size(20.dp))
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalPager(
                state = categoryPagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = categories.size,
            ) { pageIndex ->
                val currentCategory = categories[pageIndex]

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(currentCategory.templates) { template ->
                        ProjectTemplateItem(
                            template = template,
                            onClick = { onTemplateSelect(template) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateConfigurationPage(template: ProjectTemplate) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        template.Configuration()

        Spacer(Modifier.height(16.dp))

        template.size?.let { size ->
            Text(
                text = stringResource(strings.template_size).fillPlaceholders(formatFileSize(size)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ProjectTemplateItem(template: ProjectTemplate, onClick: () -> Unit) {
    val cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(144.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = contentColorFor(cardColor)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier.size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                XedIcon(
                    template.icon,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = template.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            template.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
