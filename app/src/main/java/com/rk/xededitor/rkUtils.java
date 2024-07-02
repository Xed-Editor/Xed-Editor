package com.rk.xededitor;

import static com.rk.xededitor.MainActivity.Data.activity;

import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.rk.xededitor.MainActivity.Data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

public class rkUtils {
  
  
  public static String calculateMD5(File file) {
    try {
      // Create a FileInputStream to read the file
      FileInputStream fis = new FileInputStream(file);
      
      // Create a MessageDigest instance for MD5
      MessageDigest md = MessageDigest.getInstance("MD5");
      
      // Create a buffer to read bytes from the file
      byte[] buffer = new byte[1024];
      int bytesRead;
      
      // Read the file and update the MessageDigest
      while ((bytesRead = fis.read(buffer)) != -1) {
        md.update(buffer, 0, bytesRead);
      }
      
      // Close the FileInputStream
      fis.close();
      
      // Get the MD5 digest bytes
      byte[] mdBytes = md.digest();
      
      // Convert the byte array into a hexadecimal string
      StringBuilder hexString = new StringBuilder();
      for (byte b : mdBytes) {
        hexString.append(String.format("%02x", b));
      }
      
      return hexString.toString();
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
  
  public static void toast(Context context, String message) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
  }
  
  public static void toast(String msg) {
    toast(activity, msg);
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
  
  public static long took(Runnable runnable){
    var start = System.currentTimeMillis();
    runnable.run();
    return System.currentTimeMillis()-start;
  }
  
  public static String getMimeType(Context context, DocumentFile documentFile) {
    String mimeType = context.getContentResolver().getType(documentFile.getUri());
    if (mimeType == null) {
      // Fallback: get MIME type from file extension
      String extension = MimeTypeMap.getFileExtensionFromUrl(documentFile.getUri().toString());
      mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }
    return mimeType;
  }
}
