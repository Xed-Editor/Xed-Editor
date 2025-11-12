package com.rk.tabs

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
import com.github.chrisbanes.photoview.PhotoView
import com.bumptech.glide.Glide
import com.rk.file.FileObject
import com.rk.icons.Photo
import com.rk.icons.XedIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ImageTab(
    override val file: FileObject
) : Tab() {

    override var tabTitle: MutableState<String> = mutableStateOf(file.getName())

    override val icon: ImageVector
        get() = XedIcons.Photo

    override val name: String
        get() = "Image Viewer"

    @Composable
    override fun Content() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            val scope = rememberCoroutineScope()
            AndroidView(
                factory = { context ->
                    PhotoView(context).apply {
                        this.scaleType = ImageView.ScaleType.FIT_CENTER
                        scope.launch(Dispatchers.IO){
                            Glide.with(context).load(file.toUri()).into(this@apply)
                        }
                    }
                }
            )
        }
    }
}
