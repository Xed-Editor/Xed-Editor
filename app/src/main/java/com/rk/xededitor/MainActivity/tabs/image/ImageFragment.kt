package com.rk.xededitor.MainActivity.tabs.image

import android.content.Context
import android.view.View
import com.github.chrisbanes.photoview.PhotoView
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import java.io.File
import com.bumptech.glide.Glide

class ImageFragment(val context:Context) : CoreFragment {
    private val photoView = PhotoView(context)
    private var file:File? = null
    
    override fun getView(): View {
        return photoView
    }
    
    override fun onDestroy() {}
    
    override fun onCreate() {}
    
    override fun loadFile(file: File) {
        this.file = file
        Glide.with(context).load(file).into(photoView)
    }
    
    override fun getFile(): File? {
        return file
    }
    
    override fun onClosed() {}
}