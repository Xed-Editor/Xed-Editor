package com.rk.tabs.image

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.rk.activities.main.FileTabState
import com.rk.activities.main.TabState
import com.rk.file.FileObject
import com.rk.icons.Error
import com.rk.icons.Photo
import com.rk.icons.XedIcons
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.tabs.base.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ImageState {
    object Loading : ImageState()

    data class Success(val drawable: Drawable) : ImageState()

    data class Error(val message: String) : ImageState()
}

class ImageTab(override val file: FileObject) : Tab() {

    override var tabTitle: MutableState<String> = mutableStateOf(file.getName())

    override val icon: ImageVector
        get() = XedIcons.Photo

    override val name: String
        get() = "Image viewer"

    @Composable
    override fun Content() {
        var state by remember { mutableStateOf<ImageState>(ImageState.Loading) }
        val context = LocalContext.current

        LaunchedEffect(file) {
            state =
                try {
                    val requestManager = Glide.with(context)
                    val drawable = withContext(Dispatchers.IO) { requestManager.load(file.toUri()).submit().get() }
                    ImageState.Success(drawable)
                } catch (_: Exception) {
                    ImageState.Error(strings.resource_loading_error.getString())
                }
        }

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            AnimatedContent(targetState = state) { targetState ->
                when (targetState) {
                    is ImageState.Loading -> LoadingView()
                    is ImageState.Error -> ErrorView(targetState.message)
                    is ImageState.Success -> {
                        AndroidView(
                            factory = { context ->
                                PhotoView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                            },
                            update = { view -> Glide.with(view).load(targetState.drawable).into(view) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ErrorView(message: String) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = XedIcons.Error,
                contentDescription = stringResource(strings.error),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    @Composable
    private fun LoadingView() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
    }

    override fun getState(): TabState {
        return FileTabState(file)
    }
}
