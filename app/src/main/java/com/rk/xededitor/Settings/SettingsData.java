package com.rk.xededitor.Settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class SettingsData {


    public static boolean isDarkMode(Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean isOled(Context ctx) {
        return Boolean.parseBoolean(getSetting(ctx, "isOled", "false"));
    }

    public static boolean getBoolean(Context ctx, String key, Boolean Default) {
        return Boolean.parseBoolean(getSetting(ctx, key, Boolean.toString(Default)));
    }

    public static void setBoolean(Context ctx, String key, boolean value) {
        setSetting(ctx, key, Boolean.toString(value));
    }

    public static String getSetting(Context ctx, String key, String Default) {
        SharedPreferences sharedPreferences = ctx.getApplicationContext().getSharedPreferences("Settings", Context.MODE_PRIVATE);
        return sharedPreferences.getString(key, Default);
    }

    public static void setSetting(Context ctx, String key, String value) {
        SharedPreferences sharedPreferences = ctx.getApplicationContext().getSharedPreferences("Settings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        //editor.apply();
        editor.commit();
    }


}
