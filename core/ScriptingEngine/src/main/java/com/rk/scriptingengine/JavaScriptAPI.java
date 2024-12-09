package com.rk.scriptingengine;

public interface JavaScriptAPI {
    void showToast(String text);
    String getEditorText();
    void setEditorText();
    void showInputPopup(String title,String message,String onDoneCallbackScript,String onCancelCallbackScript);
    void showLoadingPopup();
    void hideLoadingPopup();
    void showPopup(String title,String message,String onDoneCallbackScript,String onCancelCallbackScript);
    void setString(String key,String value,boolean SaveAsSetting);
    void setString(String key,String value);
    String getString(String key,String defaultValue);
    void registerKeybinding(String id,boolean ctrl,boolean alt,boolean fn,String keystroke,String callbackScript);
    void unregisterKeybinding(String id);
    void setSettingToggle(String id,String label,String description,String isEnabledCallbackScript,String onToggleCallback);
    boolean isFileReadable(String path);

    //sanitize
    String readFile(String path);
    void writeFile(String path);
}
