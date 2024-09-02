## **API Class Documentation**
The `API` class is a singleton object that provides various utility methods and properties to interact with the Android application environment. It is intended for use by plugins to perform tasks such as displaying toasts, running commands, showing dialogs, and interacting with the main activity.

### **Package**
`com.rk.libPlugin.server.api`

### **Properties**

- **`application: Application?`**
  - **Description:** This property holds a reference to the application's `Application` instance. It is used internally by the API methods to interact with the Android context.
  - **Usage:** This is a nullable property and is generally managed by the API class itself. Plugins typically do not need to interact with it directly.

- **`handler: Handler`**
  - **Description:** This property holds a `Handler` associated with the main thread's `Looper`. It is used to execute code on the main UI thread.
  - **Usage:** Used internally by the `runOnUiThread` method to post tasks to the main thread.

### **Methods**

- **`fun getInstance(): Any?`**
  - **Description:** Retrieves the singleton instance of the `API` class.
  - **Returns:** The instance of the `API` class or `null` if an error occurs.
  - **Usage:** This method is typically not necessary for plugin developers to call directly.

- **`fun getMainActivity(): Activity?`**
  - **Description:** Attempts to retrieve the current instance of the `MainActivity` by reflecting on the `BaseActivity` class.
  - **Returns:** The `Activity` instance representing the main activity or `null` if not found.
  - **Usage:** Plugins can use this method to obtain the main activity instance, which is useful for UI-related tasks.

- **`fun toast(message: String)`**
  - **Description:** Displays a toast message to the user.
  - **Parameters:** 
    - `message`: The message string to be displayed in the toast.
  - **Usage:** Call this method to show a brief message to the user, e.g., `api.toast("Hello, World!")`.

- **`fun runCommand(command: String): Command`**
  - **Description:** Executes a command string and returns a `Command` object that represents the execution.
  - **Parameters:**
    - `command`: The command string to be executed.
  - **Returns:** A `Command` object.
  - **Usage:** Call this method to execute commands within the plugin environment, e.g., `val result = api.runCommand("some_command")`.

- **`fun runOnUiThread(runnable: Runnable?)`**
  - **Description:** Executes the provided `Runnable` on the main UI thread.
  - **Parameters:**
    - `runnable`: A `Runnable` object containing the code to be executed on the UI thread.
  - **Usage:** Use this method when you need to interact with the UI from a background thread, e.g., `api.runOnUiThread { /* UI code */ }`.

- **`fun showPopup(title: String, message: String): AlertDialog?`**
  - **Description:** Displays a popup dialog with a title and message.
  - **Parameters:**
    - `title`: The title of the popup dialog.
    - `message`: The message body of the popup dialog.
  - **Returns:** The `AlertDialog` instance if successfully displayed, otherwise `null`.
  - **Usage:** Call this method to show a simple alert dialog, e.g., `api.showPopup("Notice", "This is a popup message.")`.

- **`fun showError(error: String)`**
  - **Description:** Displays an error dialog with the option to copy the error message to the clipboard.
  - **Parameters:**
    - `error`: The error message to be displayed.
  - **Usage:** Call this method to alert the user of an error and offer to copy the error message, e.g., `api.showError("An unexpected error occurred.")`.

---

### **General Usage Notes**
- The `API` class is designed to be accessed by plugins to simplify common tasks like UI updates and command execution.
- Most methods that interact with the UI should be called from the main UI thread. The `runOnUiThread` method is provided to help plugins run UI-related code safely from background threads.
