package com.rk.xededitor.MainActivity.editor.fragments.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.rk.libcommons.CustomScope
import com.rk.xededitor.MainActivity.editor.fragments.core.CoreFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ImageFragment(val context: Context) : CoreFragment {
    val scope = CustomScope()
    @JvmField var file: File? = null
    var bitmap: Bitmap? = null
    
    override fun getView(): View {
        return if (bitmap != null) {
            ImageView(context).apply { setImageBitmap(bitmap) }
        } else {
            TextView(context).apply { text = "Failed to create bitmap." }
        }
    }
    
    override fun onDestroy() {
        scope.cancel()
    }
    
    override fun onCreate() {}
    
    override fun loadFile(file: File) {
        this.file = file
        scope.launch(Dispatchers.IO) {
            bitmap = BitmapFactory.decodeFile(file.absolutePath)
        }
    }
    
    override fun getFile(): File? {
        return file
    }
}