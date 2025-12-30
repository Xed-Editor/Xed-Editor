package com.rk.tabs.image

import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.rk.activities.main.FileTabState
import com.rk.activities.main.TabState
import com.rk.file.FileObject
import com.rk.icons.Photo
import com.rk.icons.XedIcons
import com.rk.tabs.base.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageTab(override val file: FileObject) : Tab() {

    override var tabTitle: MutableState<String> = mutableStateOf(file.getName())

    override val icon: ImageVector
        get() = XedIcons.Photo

    override val name: String
        get() = "Image viewer"

    @Composable
    override fun Content() {
        Box(modifier = Modifier.Companion.fillMaxSize().clipToBounds()) {
            val scope = rememberCoroutineScope()
            AndroidView(
                factory = { context ->
                    PhotoView(context).apply {
                        this.scaleType = ImageView.ScaleType.FIT_CENTER
                        scope.launch {
                            val drawable =
                                withContext(Dispatchers.IO) { Glide.with(context).load(file.toUri()).submit().get() }

                            Glide.with(context).load(drawable).into(this@apply)
                        }
                    }
                }
            )
        }
    }

    override fun getState(): TabState {
        return FileTabState(file)
    }
}
