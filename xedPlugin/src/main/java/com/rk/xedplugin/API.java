package com.rk.xedplugin;

import android.app.Activity;
import android.app.Application;

public abstract class API {
    public abstract void onLoad(Application application);
    public abstract void onActivityCreate(Activity activity);
    public abstract void onActivityDestroy(Activity activity);
    public abstract void onActivityPause(Activity activity);
    public abstract void onActivityResume(Activity activity);
}
