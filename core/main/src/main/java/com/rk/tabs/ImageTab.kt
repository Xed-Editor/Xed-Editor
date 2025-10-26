package com.rk.tabs

import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import com.github.chrisbanes.photoview.PhotoView
import com.bumptech.glide.Glide
import com.rk.file.FileObject
import com.rk.icons.Photo
import com.rk.icons.XedIcons

class ImageTab(
    private val fileObject: FileObject
) : Tab() {

    override var tabTitle: MutableState<String> = mutableStateOf(fileObject.getName())

    override val icon: ImageVector
        get() = XedIcons.Photo

    override val name: String
        get() = "Image Viewer"

    @Composable
    override fun Content() {
        Box(modifier = Modifier.fillMaxSize().clipToBounds()){
            AndroidView(
                factory = { context ->
                    PhotoView(context).apply {
                        this.scaleType = ImageView.ScaleType.FIT_CENTER
                        Glide.with(context).load(fileObject.toUri()).into(this)
                    }
                }
            )
        }
    }

    @Composable
    override fun RowScope.Actions() { }

    override fun onTabRemoved() { }
}
