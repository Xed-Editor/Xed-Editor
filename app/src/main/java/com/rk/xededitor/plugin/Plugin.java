package com.rk.xededitor.plugin;

import android.app.Activity;

public interface Plugin {
   Activity getActivity();
   void runOnUiThread(Runnable runnable);


}
