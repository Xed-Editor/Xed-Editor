package com.rk.extension;

public abstract class ExtensionAPI {
    public abstract void onPluginLoaded();
    public abstract void onAppLaunch();
    public abstract void onAppPaused();
    public abstract void onLowMemory();
    public abstract void onMainActivityDestroyed();
}
