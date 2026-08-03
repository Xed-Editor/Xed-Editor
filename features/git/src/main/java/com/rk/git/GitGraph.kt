package com.rk.git

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.theme.harmonize
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

    val laneByHash =
        remember(commits) {
            commits.associate { it.hash to it.lane }
        }

    val indexByHash =
        remember(commits) {
            commits
                .mapIndexed { index, commit ->
                    commit.hash to index
                }
                .toMap()
        }

    val maxLane =
        remember(commits) {
            commits.maxOfOrNull { it.lane } ?: 0
        }

    val dateFormatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(
            items = commits,
            key = { _, commit -> commit.hash },
        ) { index, commit ->
            CommitItem(
                commit = commit,
                index = index,
                laneByHash = laneByHash,
                indexByHash = indexByHash,
                colors = colors,
                maxLane = maxLane,
                dateFormatter = dateFormatter,
            )
        }
    }
}

@Composable
private fun CommitItem(
    commit: GitCommit,
    index: Int,
    laneByHash: Map<String, Int>,
    indexByHash: Map<String, Int>,
    colors: List<Color>,
    maxLane: Int,
    dateFormatter: SimpleDateFormat,
) {
    Row(modifier = Modifier.fillMaxWidth().height(42.dp)) {
        val currentLaneWidth = ((commit.lane + 1) * 20).dp
        val totalLaneWidth = ((maxLane + 1) * 20).dp

        Canvas(modifier = Modifier.width(totalLaneWidth).fillMaxHeight()) {
            val laneWidth = 20.dp.toPx()
            val centerOffset = laneWidth / 2
            val centerY = size.height / 2
            val strokeWidth = 2.dp.toPx()

            fun xFor(lane: Int): Float {
                return lane * laneWidth + centerOffset
            }

            val currentX = xFor(commit.lane)
            val currentColor = colors[commit.lane % colors.size]

            // Draw connections to parents.
            commit.parentHashes.forEach { parentHash ->
                val parentLane = laneByHash[parentHash] ?: return@forEach
                val parentIndex = indexByHash[parentHash] ?: return@forEach
                val rowsBetween = parentIndex - index

                val parentX = xFor(parentLane)

                val path =
                    Path().apply {
                        moveTo(currentX, centerY)

                        val endY = centerY + size.height * rowsBetween

                        if (currentX == parentX) {
                            // No lane change
                            lineTo(parentX, centerY + size.height * rowsBetween)
                        } else if (currentX < parentX) {
                            // Lane change to the right
                            val bendEndY = centerY + size.height

                            cubicTo(
                                currentX,
                                centerY + size.height * 0.5f,
                                parentX,
                                centerY + size.height * 0.5f,
                                parentX,
                                bendEndY,
                            )

                            if (rowsBetween > 1) {
                                lineTo(parentX, endY)
                            }
                        } else {
                            // Lane change to the left
                            val bendStartY = centerY + size.height * (rowsBetween - 1)

                            if (rowsBetween > 1) {
                                lineTo(currentX, bendStartY)
                            }

                            cubicTo(
                                currentX,
                                endY - size.height * 0.5f,
                                parentX,
                                endY - size.height * 0.5f,
                                parentX,
                                endY,
                            )
                        }
                    }

                drawPath(
                    path = path,
                    color = currentColor.copy(alpha = 0.5f),
                    style = Stroke(width = strokeWidth),
                )
            }

            // Draw commit node.
            drawCircle(
                color = currentColor,
                radius = 5.dp.toPx(),
                center = Offset(currentX, centerY),
            )
        }

        Column(
            modifier =
                Modifier.weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .offset(x = -totalLaneWidth + currentLaneWidth)
        ) {
            Text(
                text = commit.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text =
                    buildString {
                        if (commit.author.isNotBlank()) {
                            append(commit.author)
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
