package com.rk.settings.extension

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.rk.components.StateScreen
import com.rk.extension.Extension
import com.rk.extension.Review
import com.rk.resources.drawables
import com.rk.resources.strings

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
        // TODO: Implement
        state = ReviewsStatus.Error.NotSupported
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

            is ReviewsStatus.Success -> {}
        }
    }
}
