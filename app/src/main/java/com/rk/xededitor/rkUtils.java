package com.rk.xededitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.rk.xededitor.MainActivity.MainActivity;
import com.rk.xededitor.MainActivity.TreeViewX.TreeNode;
import com.rk.xededitor.Settings.SettingsActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

public class rkUtils {


    public static void addToapplyPrefsOnRestart(Context ctx, String key, String value) {
        String jsonString = rkUtils.getSetting(ctx, "applyOnBoot", "{}");

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            jsonObject.put(key, value);
            String updatedJsonString = jsonObject.toString();
            rkUtils.setSetting(ctx, "applyOnBoot", updatedJsonString);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public static void applyPrefs(Context ctx){
        String jsonString = rkUtils.getSetting(ctx, "applyOnBoot", "{}");
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> keys = jsonObject.keys();

            // Loop through the keys
            while (keys.hasNext()) {
                String key = keys.next();
                String value = (String) jsonObject.get(key);
                rkUtils.setSetting(ctx,key,value);
                jsonObject.remove(key);
            }
String updatedJsonString = jsonObject.toString();

        // Update the preferences with the modified JSON string
        rkUtils.setSetting(ctx, "applyOnBoot", updatedJsonString);


        }catch (Exception e){
            e.printStackTrace();
        }

        }
    public static boolean isDarkMode(Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean isValidColor(String colorString) {
        try {
            Color.parseColor(colorString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void toast(String msg) {
        toast(MainActivity.getActivity(), msg);
    }

    public static int dpToPx(float dp, Context ctx) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    public static void ni(Context context) {
        toast(context, "This feature is not implemented");
    }

    public static void ni(Context context, String name) {
        toast(context, name + " is not implemented");
    }

    public static void setVisibility(View v, boolean visible) {
        if (visible) {
            v.setVisibility(View.VISIBLE);
        } else {
            v.setVisibility(View.GONE);
        }
    }

    public static int getScreenHeight(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return displayMetrics.heightPixels;
    }

    public static int getScreenWidth(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return displayMetrics.widthPixels;
    }

    public static int Percentage(int value, int percent) {
        return (value * percent) / 100;
    }

    public static void looper(DocumentFile rootFolder, TreeNode root, int indent) {
        if (rootFolder != null && rootFolder.isDirectory()) {
            for (DocumentFile file : rootFolder.listFiles()) {
                if (file.isDirectory()) {
                    String folderName = file.getName();
                    TreeNode thisFolder = new TreeNode(file, folderName, false, indent);
                    root.addChild(thisFolder);
                } else {
                    String fileName = file.getName();
                    root.addChild(new TreeNode(file, fileName, true, indent));
                }
            }
        }
    }

    public static void copyFileFromAssetsToInternalStorage(
            Context context, String fileName, String destinationPath) {
        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            // Open your local file as the input stream
            inputStream = context.getAssets().open(fileName);

            // Path to the just created empty file
            String outFileName = destinationPath;

            // Open the empty file as the output stream
            outputStream = new FileOutputStream(outFileName);

            // Transfer bytes from the input file to the output file
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            // Close the streams
            outputStream.flush();
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isOled(Context ctx) {
       return Boolean.parseBoolean(rkUtils.getSetting(ctx,"isOled","false"));
    }
    public static String getSetting(Context ctx,String key,String Default){
        SharedPreferences sharedPreferences = ctx.getApplicationContext().getSharedPreferences("Settings", Context.MODE_PRIVATE);
        return sharedPreferences.getString(key,Default);
    }
    public static void setSetting(Context ctx,String key,String value){
        SharedPreferences sharedPreferences = ctx.getApplicationContext().getSharedPreferences("Settings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key,value);
        editor.apply();
    }
    public int dpToPx(int dp,Context ctx) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

}
