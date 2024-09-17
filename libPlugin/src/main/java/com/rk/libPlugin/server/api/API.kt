package com.rk.libPlugin.server.api

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.rk.libPlugin.R
import com.rk.libPlugin.server.PluginError
import com.rk.libPlugin.server.api.PluginLifeCycle.ActivityEvent
import com.rk.libPlugin.server.api.PluginLifeCycle.LifeCycleType
import java.lang.ref.WeakReference

// This class will be available to every plugin
object API {
  var application: Application? = null
  private var ActivityContext = WeakReference<Activity?>(null)
  val handler = Handler(Looper.getMainLooper())
  
  
  
  //not for plugin use
  fun getInstance(): Any? {
    return try {
      val instanceField = this::class.java.getDeclaredField("INSTANCE").apply {
        isAccessible = true
      }
      instanceField.get(null)
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }
  
  
  
  
  
  
  fun getActivityContext():Activity?{
    return if (ActivityContext.get() != null){
      ActivityContext.get()
    }else{
      getMainActivity().also { ActivityContext = WeakReference(it) }
    }
  }
  
  fun setActivityContext(activity: Activity?){
    ActivityContext = WeakReference(activity)
  }
  fun resetActivityContext(){
    ActivityContext = WeakReference(getMainActivity())
  }
  
  fun getMainActivity(): Activity? {
    val companionField =
      Class.forName("com.rk.xededitor.BaseActivity").getDeclaredField("Companion").apply {
        isAccessible = true
      }
    val companionObject = companionField.get(null)
    
    val getActivityMethod =
      companionObject::class.java.getDeclaredMethod("getActivity", Class::class.java)
    
    return getActivityMethod.invoke(
      companionObject, Class.forName("com.rk.xededitor.MainActivity.MainActivity")
    ) as Activity?
  }
  
 
  
  fun toast(message: String) {
    runOnUiThread {
      Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
    }
  }
  
  
  fun runOnUiThread(runnable: Runnable?) {
    runnable?.let { handler.post(it) }
  }
  
  fun popup(title: String, message: String): AlertDialog? {
    var popup: AlertDialog? = null
    runOnUiThread {
      getActivityContext()?.let {
        popup = MaterialAlertDialogBuilder(it).setTitle(title).setMessage(message)
          .setPositiveButton("OK", null).show()
      }
    }
    return popup
  }
  
  
  interface InputInterface {
    fun onInputOK(input: String)
  }
  
  fun input(title: String, message: String, inputInterface: InputInterface) {
    runOnUiThread {
      val popupView: View =
        LayoutInflater.from(getActivityContext()).inflate(R.layout.popup_new, null)
      val editText = popupView.findViewById<EditText>(R.id.name)
      editText.setHint("Input")
      
      MaterialAlertDialogBuilder(getActivityContext()!!).setTitle(title).setMessage(message)
        .setView(popupView)
        .setPositiveButton("OK") { _, _ ->
          val text = editText.text.toString()
          Thread {
            inputInterface.onInputOK(text)
          }.start()
          
        }.show()
    }
  }
  
  fun error(error: String) {
    PluginError.showError(Exception(error))
  }
  
  
  
  fun onActivityCreate(id:String,activityEvent: ActivityEvent) {
    PluginLifeCycle.registerLifeCycle(id,LifeCycleType.CREATE, activityEvent)
  }
  
  fun onActivityDestroy(id:String,activityEvent: ActivityEvent) {
    PluginLifeCycle.registerLifeCycle(id,LifeCycleType.DESTROY, activityEvent)
  }
  
  fun onActivityPause(id:String,activityEvent: ActivityEvent) {
    PluginLifeCycle.registerLifeCycle(id,LifeCycleType.PAUSED, activityEvent)
  }
  
  fun onActivityResume(id:String,activityEvent: ActivityEvent) {
    PluginLifeCycle.registerLifeCycle(id,LifeCycleType.RESUMED, activityEvent)
  }
  
  fun unregisterEvent(id: String){
    PluginLifeCycle.unregisterActivityEvent(id)
  }
  
}
