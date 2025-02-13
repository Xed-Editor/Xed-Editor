package com.rk.xededitor.MainActivity.tabs.media

import android.content.Context
import android.view.View
import com.github.chrisbanes.photoview.PhotoView
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.bumptech.glide.Glide
import com.rk.file_wrapper.FileObject

class ImageFragment(val context:Context) : CoreFragment {
    private val photoView = PhotoView(context)
    private var file: FileObject? = null
    
    override fun getView(): View {
        return photoView
    }
    
    override fun onDestroy() {}


    override fun onCreate() {}
    
    override fun loadFile(file: com.rk.file_wrapper.FileObject) {
        this.file = file
        Glide.with(context).load(file.toUri()).into(photoView)
    }
    
    override fun getFile(): com.rk.file_wrapper.FileObject? {
        return file
    }
    
    override fun onClosed() {}
}