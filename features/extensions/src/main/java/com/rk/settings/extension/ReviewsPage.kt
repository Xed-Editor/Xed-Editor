package com.rk.settings.extension

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.common.PackageType
import com.rk.components.StateScreen
import com.rk.extension.REVIEWS_API_BASE
import com.rk.extension.model.Package
import com.rk.extension.model.Review
import com.rk.extension.model.ReviewsResponse
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.utils.logError
import com.rk.utils.okHttpClient
import com.rk.utils.timeAgo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface ReviewsStatus {
    object Loading : ReviewsStatus

    sealed class Error(val stringRes: Int, val drawableRes: Int) : ReviewsStatus {
        object Network : Error(strings.network_err, drawables.cloud_off)

        object Unknown : Error(strings.unknown_err, drawables.error)

        object NotSupported : Error(strings.reviews_not_supported, drawables.comment)
    }

    data class Success(val response: ReviewsResponse) : ReviewsStatus
}

@Composable
fun ReviewsPage(pkg: Package, refreshKey: Int, onLoaded: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember(pkg) { mutableStateOf<ReviewsStatus>(ReviewsStatus.Loading) }
    val client = remember { okHttpClient }

    LaunchedEffect(pkg, refreshKey) {
        state = ReviewsStatus.Loading
        val forceRefresh = refreshKey > 0
        state = loadReviews(pkg.id, pkg.type, client, forceRefresh)
        onLoaded()
    }

    AnimatedContent(targetState = state, modifier = modifier.fillMaxWidth()) { state ->
        when (state) {
            ReviewsStatus.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ReviewsStatus.Error -> {
                val color =
                    when (state) {
                        is ReviewsStatus.Error.NotSupported -> LocalContentColor.current
                        else -> MaterialTheme.colorScheme.error
                    }
                StateScreen(
                    painter = painterResource(state.drawableRes),
                    text = stringResource(state.stringRes),
                    color = color,
                )
            }

            is ReviewsStatus.Success -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    val reviews = state.response.reviews
                    ReviewList(reviews)
                }
            }
        }
    }
}

@Composable
fun ReviewList(reviews: List<Review>) {
    if (reviews.isEmpty()) {
        StateScreen(
            painter = painterResource(drawables.comment),
            text = stringResource(strings.no_reviews),
        )
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            reviews.forEach { ReviewItem(it) }
        }
    }
}

@Composable
fun ReviewItem(review: Review) {
    val context = LocalContext.current
    val cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = contentColorFor(cardColor)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model =
                        ImageRequest.Builder(context)
                            .data(review.userImage)
                            .fallback(drawables.person)
                            .placeholder(drawables.person)
                            .error(drawables.person)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp).clip(CircleShape),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = review.userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    ReviewStars(review.score)
                }

                val timeAgo = timeAgo(System.currentTimeMillis(), review.createdAt)
                timeAgo?.let {
                    Text(
                        modifier = Modifier.align(Alignment.Top),
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SelectionContainer {
                Text(
                    text = review.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun ReviewStars(score: Int) {
    Row {
        for (i in 1..5) {
            val isTinted = i <= score
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint =
                    if (isTinted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private suspend fun loadReviews(
    packageId: String,
    packageType: PackageType,
    client: OkHttpClient,
    forceRefresh: Boolean = false,
): ReviewsStatus {
    return withContext(Dispatchers.IO) {
        runCatching {
            val url =
                REVIEWS_API_BASE.toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("packageId", packageId)
                    .addQueryParameter("packageType", packageType.value)
                    .build()

            val requestBuilder = Request.Builder().url(url)
            if (forceRefresh) {
                requestBuilder.cacheControl(CacheControl.FORCE_NETWORK)
            }

            val request = requestBuilder.build()

            val jsonString =
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext ReviewsStatus.Error.Unknown
                    }
                    response.body.string()
                }

            val json = Json {
                ignoreUnknownKeys = true
                allowTrailingComma = true
            }
            val reviews = json.decodeFromString<ReviewsResponse>(jsonString)

            return@runCatching ReviewsStatus.Success(reviews)
        }
            .getOrElse {
                logError(it)
                ReviewsStatus.Error.Network
            }
    }
}
