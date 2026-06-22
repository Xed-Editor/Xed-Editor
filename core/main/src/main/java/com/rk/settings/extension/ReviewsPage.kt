package com.rk.settings.extension

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.components.StateScreen
import com.rk.extension.Extension
import com.rk.extension.LocalExtension
import com.rk.extension.Review
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.theme.Typography
import androidx.compose.ui.graphics.Color
import java.io.IOException

sealed interface ReviewsStatus {
    object Loading : ReviewsStatus

    sealed class Error(val stringRes: Int, val drawableRes: Int) : ReviewsStatus {
        object Network : Error(strings.network_err, drawables.cloud_off)

        object Unknown : Error(strings.unknown_err, drawables.error)

        object NotSupported : Error(strings.reviews_not_supported, drawables.comment)
    }

    data class Success(val reviews: List<Review>) : ReviewsStatus
}

@Composable
fun ReviewsPage(extension: Extension, refreshKey: Int, onLoaded: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember(extension) { mutableStateOf<ReviewsStatus>(ReviewsStatus.Loading) }

    LaunchedEffect(extension, refreshKey) {
        state = ReviewsStatus.Loading
        try {
            if (extension is LocalExtension) {
                state = ReviewsStatus.Error.NotSupported
            } else {
                val reviews = extension.getReviews()
                state = ReviewsStatus.Success(reviews)
            }
        } catch (e: IOException) {
            state = ReviewsStatus.Error.Network
        } catch (e: Exception) {
            e.printStackTrace()
            state = ReviewsStatus.Error.Unknown
        } finally {
            onLoaded()
        }
    }

    AnimatedContent(targetState = state, modifier = modifier.fillMaxWidth()) { targetState ->
        when (targetState) {
            ReviewsStatus.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ReviewsStatus.Error -> {
                val color =
                    when (targetState) {
                        is ReviewsStatus.Error.NotSupported -> LocalContentColor.current
                        else -> MaterialTheme.colorScheme.error
                    }
                StateScreen(
                    painter = painterResource(targetState.drawableRes),
                    text = stringResource(targetState.stringRes),
                    color = color,
                )
            }

            is ReviewsStatus.Success -> {
                if (targetState.reviews.isEmpty()) {
                    StateScreen(
                        painter = painterResource(drawables.comment),
                        text = "No reviews yet",
                        color = LocalContentColor.current,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(targetState.reviews) { review ->
                            ReviewItem(review = review)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewItem(review: Review, modifier: Modifier = Modifier) {
    val cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = contentColorFor(cardColor)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = review.author,
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { index ->
                        val tint = if (index < review.rating) {
                            Color(0xFFFFB300) // Golden Star
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = tint
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = review.text,
                style = Typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            review.authorResponse?.let { response ->
                if (response.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Author's Response:",
                                style = Typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = response,
                                style = Typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
