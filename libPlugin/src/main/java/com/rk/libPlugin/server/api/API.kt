package com.rk.libPlugin.server.api

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jaredrummler.ktsh.Shell
import com.rk.libPlugin.R

// This class will be available to every plugin
object API {
    var application: Application? = null

   //not for plugin use
    fun getInstance(): Any? {
        return try {
            val apiClass = API::class.java
            val instanceField = apiClass.getDeclaredField("INSTANCE").apply {
                isAccessible = true
            }
            instanceField.get(null)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }




    /**
     * Retrieves the current MainActivity instance.
     *
     * Usage:
     *
     * ```java
     * mainActivity = api.getMainActivity();
     * if (mainActivity != null) {
     *     print("MainActivity obtained successfully.");
     *     //do something
     * } else {
     *     print("Failed to obtain MainActivity.");
     * }
     * ```
     */
    fun getMainActivity(): Activity? {
        val companionField =
            Class.forName("com.rk.xededitor.BaseActivity").getDeclaredField("Companion").apply {
                isAccessible = true
            }
        val companionObject = companionField.get(null)

        val getActivityMethod =
            companionObject::class.java.getDeclaredMethod("getActivity", Class::class.java)

        return getActivityMethod.invoke(
            companionObject, Class.forName("com.rk.xededitor.MainActivity.MainActivity")
        ) as Activity?
    }

    /**
     * Displays a Toast message on the screen.
     *
     * Usage:
     *
     * ```java
     * api.toast("Hello from BeanShell!");
     * ```
     */
    fun toast(message: String) {
        runOnUiThread {
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Executes a shell command and returns the result.
     *
     * Usage:
     *
     * ```java
     * result = api.runCommand("ls -la");
     * if (result.isSuccess()) {
     *     print("Command output: " + result.stdout().toString());
     * } else {
     *     print("Command failed: " + result.stderr().toString());
     * }
     *
     * //todo
     * Note: Make sure to shutdown the Shell
     * ```
     */
    fun runCommand(command: String): Shell.Command.Result {
        return Shell("sh").run(command)
    }

    val handler = Handler(Looper.getMainLooper())

    /**
     * Runs the specified code on the UI thread.
     *
     * Usage:
     *
     * ```java
     * api.runOnUiThread(new Runnable() {
     *     public void run() {
     *         print("Running on the UI thread!");
     *     }
     * });
     * ```
     */
    fun runOnUiThread(runnable: Runnable?) {
        handler.post(runnable!!)
    }

    /**
     * Displays a popup dialog with the specified title and message.
     *
     * Usage:
     *
     * ```java
     * dialog = api.showPopup("Hello", "This is a popup message!");
     * ```
     */
    fun showPopup(title: String, message: String): AlertDialog? {
        var popup: AlertDialog? = null
        runOnUiThread {
            getMainActivity()?.let {
                popup = MaterialAlertDialogBuilder(it).setTitle(title).setMessage(message)
                    .setPositiveButton("OK", null).show()
            }
        }
        return popup
    }

    /**
     * Displays an input dialog and processes the user input using the InputInterface.
     *
     * Usage:
     *
     * ```java
     * inputInterface = new com.rk.libPlugin.server.api.API$InputInterface() {
     *     public void onInputOK(String input) {
     *         print("User input: " + input);
     *     }
     * };
     *
     * api.input("Enter Name", "Please enter your name:", inputInterface);
     * ```
     */
    interface InputInterface {
        fun onInputOK(input: String)
    }

    fun input(title: String, message: String, inputInterface: InputInterface) {
        runOnUiThread {
            val popupView: View =
                LayoutInflater.from(getMainActivity()).inflate(R.layout.popup_new, null)
            val editText = popupView.findViewById<EditText>(R.id.name)
            editText.setHint("Input")

            MaterialAlertDialogBuilder(getMainActivity()!!).setTitle(title).setMessage(message)
                .setView(popupView)
                .setPositiveButton("OK") { _, _ ->
                    val text = editText.text.toString()
                    Thread {
                        inputInterface.onInputOK(text)
                    }.start()

                }.show()
        }
    }

    /**
     * Displays an error dialog with the option to copy the error message to the clipboard.
     *
     * Usage:
     *
     * ```java
     * api.showError("Something went wrong.");
     * ```
     */
    fun showError(error: String) {
        runOnUiThread {
            getMainActivity()?.let {
                MaterialAlertDialogBuilder(it).setTitle("Error").setNeutralButton("Copy") { _, _ ->
                    val clipboard =
                        application!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("label", error)
                    clipboard.setPrimaryClip(clip)
                }.setPositiveButton("OK", null).setMessage(error).show()
            }
        }
    }
}
