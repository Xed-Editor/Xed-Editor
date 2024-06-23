package com.rk.xededitor.plugin;

import android.app.Activity;
import android.app.Application;

public interface API {
    public void onLoad(Application application);
    public void onActivityCreate(Activity activity);
}
