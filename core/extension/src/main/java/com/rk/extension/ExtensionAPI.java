package com.rk.extension;

public abstract class ExtensionAPI {
    public abstract void onPluginLoaded();
    public abstract void onAppCreated();
    public abstract void onAppLaunched();
    public abstract void onAppPaused();
    public abstract void onAppResumed();
    public abstract void onAppDestroyed();
    public abstract void onLowMemory();
}
