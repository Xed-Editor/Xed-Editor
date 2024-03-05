package com.rk.xededitor;

import android.view.Display;
import androidx.documentfile.provider.DocumentFile;
import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.graphics.Color;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.content.Context;
import android.content.res.Configuration;
import com.rk.xededitor.activities.MainActivity.*;
public class rkUtils {

  public static boolean isDarkMode(Context ctx) {
    return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
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

  public static void ni(Context context) {
    toast(context, "This feature is not implemented");
  }
  public static void ni(Context context,String name) {
    toast(context, name+" is not implemented");
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
}
