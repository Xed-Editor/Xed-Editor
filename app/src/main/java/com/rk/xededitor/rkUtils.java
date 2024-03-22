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
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.File;
import android.content.res.AssetManager;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class rkUtils {

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
 public static int dpToPx(float dp,Context ctx) {
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

  public static void unzip(String zipFilePath, String destDirectory) throws IOException {
    File destDir = new File(destDirectory);
    if (!destDir.exists()) {
      destDir.mkdir();
    }
    ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
    ZipEntry entry = zipIn.getNextEntry();
    // iterates over entries in the zip file
    while (entry != null) {
      String filePath = destDirectory + File.separator + entry.getName();
      if (!entry.isDirectory()) {
        // if the entry is a file, extracts it
        extractFile(zipIn, filePath);
      } else {
        // if the entry is a directory, make the directory
        File dir = new File(filePath);
        dir.mkdir();
      }
      zipIn.closeEntry();
      entry = zipIn.getNextEntry();
    }
    zipIn.close();
  }

  private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
    byte[] bytesIn = new byte[4096];
    int read = 0;
    while ((read = zipIn.read(bytesIn)) != -1) {
      bos.write(bytesIn, 0, read);
    }
    bos.close();
  }

  

  public static String getPublicDirectory() {
    return "/storage/emulated/0/Android/data/com.rk.xededitor";
  }
}
