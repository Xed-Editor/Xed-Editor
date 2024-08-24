package com.rk.xededitor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.MainActivity.StaticData
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.security.MessageDigest

object rkUtils {
    private var mHandler = Handler(Looper.getMainLooper())

    fun runOnUiThread(runnable: Runnable?) {
        mHandler.post(runnable!!)
    }






    val currentEditor: CodeEditor?
        get() = StaticData.fragments[StaticData.mTabLayout.selectedTabPosition].editor

    fun writeToFile(file: File?, data: String?) {
        var bufferedWriter: BufferedWriter? = null
        try {
            // Create a FileOutputStream
            val fileOutputStream = FileOutputStream(file)

            // Wrap the FileOutputStream with an OutputStreamWriter
            val outputStreamWriter = OutputStreamWriter(fileOutputStream)

            // Wrap the OutputStreamWriter with a BufferedWriter
            bufferedWriter = BufferedWriter(outputStreamWriter)

            // Write data to the file
            bufferedWriter.write(data)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            // Close the BufferedWriter
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun calculateMD5(file: File?): String? {
        try {
            // Create a FileInputStream to read the file
            val fis = FileInputStream(file)

            // Create a MessageDigest instance for MD5
            val md = MessageDigest.getInstance("MD5")

            // Create a buffer to read bytes from the file
            val buffer = ByteArray(1024)
            var bytesRead: Int

            // Read the file and update the MessageDigest
            while ((fis.read(buffer).also { bytesRead = it }) != -1) {
                md.update(buffer, 0, bytesRead)
            }

            // Close the FileInputStream
            fis.close()

            // Get the MD5 digest bytes
            val mdBytes = md.digest()

            // Convert the byte array into a hexadecimal string
            val hexString = StringBuilder()
            for (b in mdBytes) {
                hexString.append(String.format("%02x", b))
            }

            return hexString.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun shareText(ctx: Context, text: String?) {
        val sendIntent = Intent()
        sendIntent.setAction(Intent.ACTION_SEND)
        sendIntent.putExtra(Intent.EXTRA_TEXT, text)
        sendIntent.setType("text/plain")

        val shareIntent = Intent.createChooser(sendIntent, null)
        ctx.startActivity(shareIntent)
    }

    @JvmStatic
    fun toast(context: Context?, message: String?) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun dpToPx(dp: Float, ctx: Context): Int {
        val density = ctx.resources.displayMetrics.density
        return Math.round(dp * density)
    }

    fun ni(context: Context) {
        toast(context, context.resources.getString(R.string.ni))
    }

    fun took(runnable: Runnable): Long {
        val start = System.currentTimeMillis()
        runnable.run()
        return System.currentTimeMillis() - start
    }

    fun getMimeType(context: Context, documentFile: DocumentFile): String? {
        var mimeType = context.contentResolver.getType(documentFile.uri)
        if (mimeType == null) {
            // Fallback: get MIME type from file extension
            val extension = MimeTypeMap.getFileExtensionFromUrl(documentFile.uri.toString())
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return mimeType
    }
}
