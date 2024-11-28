package com.rk.crashhandler

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.util.Log
import java.util.LinkedList
import kotlin.system.exitProcess

class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {
    
    private var context:Application? = null
    
    private val info = LinkedList<String>()
    private val tempInfo = LinkedList<String>()
    
    fun init(context: Application){
        this.context = context
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    fun setPermanentCrashInfo(infoString:String){
        info.add(infoString)
    }
    
    fun setNextCrashInfo(infoString:String){
        tempInfo.add(infoString)
    }
    
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        if (context == null){
            Log.e("CrashHandler","init not called")
            exitProcess(1)
        }
        
        launchCrashActivity()
        
        if (Looper.myLooper() != null) {
            while (true) {
                try {
                    Looper.loop()
                    return // Quit loop if no exception
                } catch (t: Throwable) {
                    // process cannot be recovered, exit
                    exitProcess(1)
                }
            }
        }
    }
    
    private fun launchCrashActivity() {
        val intent = Intent(context, CrashActivity::class.java)
        intent.putExtra("info",info)
        intent.putExtra("extraInfo",tempInfo.clone() as LinkedList<String>)
        tempInfo.clear()
        context!!.startActivity(intent)
    }
    
    companion object{
        @Suppress("STATIC_FIELD_LEAK")
        val INSTANCE = CrashHandler()
    }
    
}
