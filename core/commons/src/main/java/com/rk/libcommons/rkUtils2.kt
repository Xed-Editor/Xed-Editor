package com.rk.libcommons

import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object rkUtils2 {
    suspend fun ui(action: suspend () -> Unit){
        withContext(Dispatchers.Main){
            action.invoke()
        }
    }
    
    suspend fun io(action: suspend () -> Unit){
        withContext(Dispatchers.IO){
            action.invoke()
        }
    }
    
    fun shareText(ctx: Context, text: String?) {
        try {
            val sendIntent = Intent()
            sendIntent.setAction(Intent.ACTION_SEND)
            sendIntent.putExtra(Intent.EXTRA_TEXT, text)
            sendIntent.setType("text/plain")
            val shareIntent = Intent.createChooser(sendIntent, null)
            ctx.startActivity(shareIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(application!!,"error : ${e.printStackTrace()}",Toast.LENGTH_SHORT).show()
        }
    }
}