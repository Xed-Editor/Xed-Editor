package com.rk.xededitor;

import android.app.Application;
import android.content.Context;
public class App extends Application {

  @Override
  protected void attachBaseContext(Context arg0) {
    super.attachBaseContext(arg0);
  }

  @Override
  public void onCreate() {
    //load plugins
  }

  @Override
  public void onTerminate() {
    //plugin invoketion
    super.onTerminate();
  }

  @Override
  public void onLowMemory() {
    
    //plugin invoketion
    super.onLowMemory();
  }
}
