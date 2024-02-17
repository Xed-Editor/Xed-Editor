package com.rk.xededitor;

import android.content.Context;
import android.widget.Toast;
import android.graphics.Color;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.content.Context;
import android.content.res.Configuration;

public class rkUtils {
  
 public static boolean isDarkMode(Context ctx) {
    int x = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    return x == Configuration.UI_MODE_NIGHT_YES;
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
}
