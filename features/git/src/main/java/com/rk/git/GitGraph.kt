package com.rk.git

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.drawscope.clipRect
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

/**
 * Connection from a child commit to one of its parents. Stored with row indices so any row can render its own visible
 * part of the connection.
 */
private data class GitLine(
    val childIndex: Int,
    val parentIndex: Int,
    val childLane: Int,
    val parentLane: Int,
    val color: Color,
)

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
            commits.mapIndexed { index, commit -> commit.hash to index }.toMap()
        }

    val maxLane =
        remember(commits) {
            commits.maxOfOrNull { it.lane } ?: 0
        }

    // Pre-compute all child -> parent connections
    val edges =
        remember(commits, colors) {
            val list = mutableListOf<GitLine>()
            commits.forEachIndexed { index, commit ->
                commit.parentHashes.forEach { parentHash ->
                    val parentIndex = indexByHash[parentHash] ?: return@forEach
                    val parentLane = laneByHash[parentHash] ?: return@forEach

                    val maxLane = maxOf(commit.lane, parentLane)

                    list.add(
                        GitLine(
                            childIndex = index,
                            parentIndex = parentIndex,
                            childLane = commit.lane,
                            parentLane = parentLane,
                            color = colors[maxLane % colors.size],
                        )
                    )
                }
            }
            list
        }

    // Group each row to the relevant lines that cross it. Therefore, each visible row can
    // draw its own segment, even if the child commit is off-screen.
    val edgesByRow =
        remember(edges) {
            val map = HashMap<Int, MutableList<GitLine>>()
            edges.forEach { edge ->
                val start = edge.childIndex
                val end = edge.parentIndex
                for (row in start..end) {
                    map.getOrPut(row) { mutableListOf() }.add(edge)
                }
            }
            map
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
                rowLines = edgesByRow[index].orEmpty(),
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
    rowLines: List<GitLine>,
    colors: List<Color>,
    maxLane: Int,
    dateFormatter: SimpleDateFormat,
) {
    val totalLaneWidth = ((maxLane + 1) * 20).dp

    // Keep the text right of all lanes occupied in this row,
    // including lines that only pass through it.
    var effectiveLane = commit.lane
    rowLines.forEach { edge ->
        if (edge.childLane > effectiveLane) {
            effectiveLane = edge.childLane
        }
        if (edge.parentLane > effectiveLane) {
            effectiveLane = edge.parentLane
        }
    }
    val currentLaneWidth = ((effectiveLane + 1) * 20).dp

    Box(modifier = Modifier.fillMaxWidth().height(42.dp)) {
        Canvas(modifier = Modifier.width(totalLaneWidth).fillMaxHeight()) {
            val laneWidth = 20.dp.toPx()
            val centerOffset = laneWidth / 2
            val centerY = size.height / 2
            val strokeWidth = 2.dp.toPx()

            fun xFor(lane: Int): Float {
                return lane * laneWidth + centerOffset
            }

            // Draw the full line translated into this row's coordinate space.
            // clipRect keeps only the visible portion inside this row (to avoid overlaps).
            clipRect {
                rowLines.forEach { line ->
                    val rowsBetween = line.parentIndex - line.childIndex
                    val rowOffset = index - line.childIndex

                    val childCenterY = centerY - rowOffset * size.height
                    val parentCenterY = childCenterY + size.height * rowsBetween

                    val currentX = xFor(line.childLane)
                    val parentX = xFor(line.parentLane)

                    val path =
                        Path().apply {
                            moveTo(currentX, childCenterY)

                            if (currentX == parentX) {
                                // No lane change
                                lineTo(parentX, parentCenterY)
                            } else if (currentX < parentX) {
                                // Lane change to the right
                                val bendEndY = childCenterY + size.height

                                cubicTo(
                                    currentX,
                                    childCenterY + size.height * 0.5f,
                                    parentX,
                                    childCenterY + size.height * 0.5f,
                                    parentX,
                                    bendEndY,
                                )

                                if (rowsBetween > 1) {
                                    lineTo(parentX, parentCenterY)
                                }
                            } else {
                                // Lane change to the left
                                val bendStartY = childCenterY + size.height * (rowsBetween - 1)

                                if (rowsBetween > 1) {
                                    lineTo(currentX, bendStartY)
                                }

                                cubicTo(
                                    currentX,
                                    parentCenterY - size.height * 0.5f,
                                    parentX,
                                    parentCenterY - size.height * 0.5f,
                                    parentX,
                                    parentCenterY,
                                )
                            }
                        }

                    drawPath(
                        path = path,
                        color = line.color.copy(alpha = 0.5f),
                        style = Stroke(width = strokeWidth),
                    )
                }
            }

            // Draw commit node.
            drawCircle(
                color = colors[commit.lane % colors.size],
                radius = 5.dp.toPx(),
                center = Offset(xFor(commit.lane), centerY),
            )
        }

        Column(
            modifier =
                Modifier.fillMaxWidth().padding(start = currentLaneWidth + 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
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
