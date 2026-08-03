package com.rk.git

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.theme.harmonize
import com.rk.utils.copyToClipboard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GitGraphView(commits: List<GitCommit>, modifier: Modifier = Modifier) {
    if (commits.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(drawables.commit),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(strings.no_commits), color = MaterialTheme.colorScheme.onSurface)
        }
        return
    }

    val colors =
        listOf(
            Color(harmonize(0xFFF44336)),
            Color(harmonize(0xFF4CAF50)),
            Color(harmonize(0xFF2196F3)),
            Color(harmonize(0xFFFFEB3B)),
            Color(harmonize(0xFFE91E63)),
            Color(harmonize(0xFF00BCD4)),
            Color(harmonize(0xFFFF9800)),
            Color(harmonize(0xFF9C27B0)),
            Color(harmonize(0xFF009688)),
            Color(harmonize(0xFF8BC34A)),
        )

    val maxLane = remember(commits) { commits.maxOfOrNull { it.lane } ?: 0 }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(commits, key = { it.hash }) { commit ->
            CommitItem(commit, colors, maxLane, dateFormatter)
        }
    }
}

@Composable
private fun CommitItem(
    commit: GitCommit,
    colors: List<Color>,
    maxLane: Int,
    dateFormatter: SimpleDateFormat,
) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Canvas(modifier = Modifier.width(((maxLane + 1) * 16).dp).fillMaxHeight()) {
            val laneWidth = 16.dp.toPx()
            val centerOffset = laneWidth / 2
            val y = size.height / 2

            val color = colors[commit.lane % colors.size]
            val x = commit.lane * laneWidth + centerOffset

            // Vertical line through the lane
            drawLine(
                color = color.copy(alpha = 0.4f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
            )

            // Commit node
            drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(x, y))
        }

        Column(
            modifier =
                Modifier.weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(
                        onClick = {
                            copyToClipboard("Commit hash", commit.hash)
                        }
                    )
        ) {
            Text(
                text = commit.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text =
                    buildString {
                        commit.author
                            .takeIf { it.isNotBlank() }
                            ?.let {
                                append(it)
                                append(" • ")
                            }
                        append(dateFormatter.format(Date(commit.date)))
                        append(" • ")
                        append(commit.hash.take(7))
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
