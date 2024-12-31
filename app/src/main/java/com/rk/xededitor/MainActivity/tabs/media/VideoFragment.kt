package com.rk.xededitor.MainActivity.tabs.media

import android.content.Context
import android.view.View
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rk.filetree.interfaces.FileObject
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import java.io.File

class VideoFragment(val context: Context) : CoreFragment {
    private val player = ExoPlayer.Builder(context).build()
    private var file: FileObject? = null
    
    override fun getView(): View? {
        return PlayerView(context).apply {
            this.player = this@VideoFragment.player
        }
    }
    
    override fun onDestroy() {
        player.stop()
        player.release()
    }
    
    override fun onCreate() {
    
    }
    
    override fun loadFile(file: FileObject) {
        this.file = file
        val mediaItem = MediaItem.fromUri(file.toUri())
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }
    
    override fun getFile(): FileObject? {
        return file
    }
    
    override fun onClosed() {
        onDestroy()
    }
}