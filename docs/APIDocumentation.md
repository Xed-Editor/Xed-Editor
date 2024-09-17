# Complete API Documentation

This document provides comprehensive documentation for all methods available in the API class. and can be used in plugins and beanshell scripts

## Table of Contents

1. [Activity Context Methods](#activity-context-methods)
2. [Activity Lifecycle Methods](#activity-lifecycle-methods)
3. [UI Interaction Methods](#ui-interaction-methods)
4. [Utility Methods](#utility-methods)

## Activity Context Methods

### `getActivityContext(): Activity?`

Retrieves the current activity context.

**Returns:** The current `Activity` context, or `null` if not available.

### `setActivityContext(activity: Activity?)`

Sets the current activity context.
**Note**: when you set activity context any popups and error messages will use that activity

**Parameters:**
- `activity`: The `Activity` to set as the current context.

### `resetActivityContext()`

Resets the activity context to the main activity.

### `getMainActivity(): Activity?`

Retrieves the current MainActivity instance.

**Returns:** The current `MainActivity` instance, or `null` if not available.

**Usage Example:**
```java
mainActivity = api.getMainActivity();
if (mainActivity != null) {
    System.out.println("MainActivity obtained successfully.");
    // Do something with mainActivity
} else {
    System.out.println("Failed to obtain MainActivity.");
}
```

## Activity Lifecycle Methods

### `onActivityCreate(id: String, activityEvent: PluginLifeCycle.ActivityEvent)`

Registers a callback to be executed when an activity is created.

**Parameters:**
- `id`: A unique identifier for this callback.
- `activityEvent`: An implementation of `PluginLifeCycle.ActivityEvent` interface.

### `onActivityDestroy(id: String, activityEvent: PluginLifeCycle.ActivityEvent)`

Registers a callback to be executed when an activity is destroyed.

**Parameters:**
- `id`: A unique identifier for this callback.
- `activityEvent`: An implementation of `PluginLifeCycle.ActivityEvent` interface.

### `onActivityPause(id: String, activityEvent: PluginLifeCycle.ActivityEvent)`

Registers a callback to be executed when an activity is paused.

**Parameters:**
- `id`: A unique identifier for this callback.
- `activityEvent`: An implementation of `PluginLifeCycle.ActivityEvent` interface.

### `onActivityResume(id: String, activityEvent: PluginLifeCycle.ActivityEvent)`

Registers a callback to be executed when an activity is resumed.

**Parameters:**
- `id`: A unique identifier for this callback.
- `activityEvent`: An implementation of `PluginLifeCycle.ActivityEvent` interface.

### `unregisterEvent(id: String)`

Unregisters a previously registered activity event callback.

**Parameters:**
- `id`: The unique identifier of the callback to unregister.

**Usage Example:**
```java
api.onActivityCreate("idCreate", new PluginLifeCycle.ActivityEvent() {
    public void onEvent(String id, Activity activity) {
        // Do something when the activity is created
    }
});

// Later, to unregister:
api.unregisterEvent("idCreate");
```

## UI Interaction Methods

### `toast(message: String)`

Displays a Toast message on the screen.

**Parameters:**
- `message`: The message to display in the toast.

**Usage Example:**
```java
api.toast("Hello from BeanShell!");
```

### `popup(title: String, message: String): AlertDialog?`

Displays a popup dialog with the specified title and message.

**Parameters:**
- `title`: The title of the popup dialog.
- `message`: The message to display in the dialog.

**Returns:** The `AlertDialog` object, or `null` if the dialog couldn't be created.

**Usage Example:**
```java
dialog = api.popup("Hello", "This is a popup message!");
```

### `input(title: String, message: String, inputInterface: InputInterface)`

Displays an input dialog and processes the user input using the InputInterface.

**Parameters:**
- `title`: The title of the input dialog.
- `message`: The message to display in the dialog.
- `inputInterface`: An implementation of the `InputInterface` to handle the user's input.

**Usage Example:**
```java
api.input("Enter Name", "Please enter your name:",new API.InputInterface() {
    public void onInputOK(String input) {
        System.out.println("User input: " + input);
    }
});
```

### `error(error: String)`

Displays an error dialog with the option to copy the error message to the clipboard.

**Parameters:**
- `error`: The error message to display.

**Usage Example:**
```java
api.error("Something went wrong.");
```

## Utility Methods

### `runOnUiThread(runnable: Runnable?)`

Runs the specified code on the UI thread.

**Parameters:**
- `runnable`: The `Runnable` to be executed on the UI thread.

**Usage Example:**
```java
api.runOnUiThread(new Runnable() {
    public void run() {
        System.out.println("Running on the UI thread!");
    }
});
```
