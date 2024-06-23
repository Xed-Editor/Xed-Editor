package com.rk.xededitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import static com.rk.xededitor.MainActivity.Data.*;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;
import com.rk.xededitor.MainActivity.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Iterator;

public class rkUtils {








    public static boolean isValidColor(String colorString) {
        try {
            Color.parseColor(colorString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

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

    public static String getMimeType(Context context, DocumentFile documentFile) {
        String mimeType = context.getContentResolver().getType(documentFile.getUri());
        if (mimeType == null) {
            // Fallback: get MIME type from file extension
            String extension = MimeTypeMap.getFileExtensionFromUrl(documentFile.getUri().toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return mimeType;
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


    public int dpToPx(int dp,Context ctx) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

}
