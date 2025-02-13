package com.rk.xededitor.MainActivity.tabs.media

import android.content.Context
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment

class VideoFragment(val context: Context) : CoreFragment {
    private val player = ExoPlayer.Builder(context).build()
    private var file: com.rk.file_wrapper.FileObject? = null
    
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
    
    override fun loadFile(file: com.rk.file_wrapper.FileObject) {
        this.file = file
        val mediaItem = MediaItem.fromUri(file.toUri())
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }
    
    override fun getFile(): com.rk.file_wrapper.FileObject? {
        return file
    }
    
    override fun onClosed() {
        onDestroy()
    }
}