package com.rk.libPlugin.server;


import android.app.Activity;
import android.util.Log;

import java.lang.reflect.Method;

//instance of this class will be available to every plugin
public class API {

    public API() {

    }

    public Activity getActivity() {
        try {
            var baseActivityClass = Class.forName("com.rk.xededitor.BaseActivity");
            var mainActivityClass = Class.forName("com.rk.xededitor.MainActivity.MainActivity");

            // Get the Companion object
            Object companionObject = baseActivityClass.getDeclaredField("Companion").get(null);

            // Get the getActivity method
            assert companionObject != null;
            Method getActivityMethod = companionObject.getClass().getMethod("getActivity", Class.class);

            // Invoke the method with MainActivity.class as an argument
            Object result = getActivityMethod.invoke(companionObject, mainActivityClass);

            if (result != null) {
                return (Activity) result;
            }
        } catch (Exception e) {
            Log.e("PluginAPI", "unable to find mainactivity");
            e.printStackTrace();
        }
        return null;
    }





}
