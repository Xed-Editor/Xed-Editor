# Plugins
Plugins allow you to customize and expand the features of Karbon in many ways. They provide flexibility to modify its behavior and extend its functionality.

Karbon uses BeanShell as its scripting language.

## Creating a Plugin
To create a plugin, the easiest way is to clone the example plugin repository called [HelloWorld](https://github.com/Xed-Editor/HelloWorld-Plugin-Example).

You can do this using Karbon's built-in terminal by running the following commands:

```bash
git clone https://github.com/Xed-Editor/HelloWorld-Plugin-Example
cd HelloWorld-Plugin-Example
```

After running these commands, you'll see several files in the directory:

```
HelloWorld-Plugin-Example/
├── icon.png
├── LICENSE
├── main.bsh
├── manifest.json
└── README.md
```

- **icon.png**: This image file serves as the icon for the plugin when installed in Karbon.
- **LICENSE**: This is a legal document and is not directly related to the plugin's functionality.
- **main.bsh**: This is the most important file that contains the code for the plugin.
- **manifest.json**: This file contains information about the plugin, like its name, description, author, and other details.
- **README.md**: This file provides documentation about the plugin but is not directly related to its functionality.

Let's take a look at the contents of the `manifest.json` file:

```json
{
  "name": "Hello world",
  "packageName": "com.rk.hw",
  "author": "Rohit",
  "version": "1.0.0",
  "versionCode": 1,
  "script": "main.bsh",
  "description": "example plugin",
  "icon": "icon.png"
}
```

- **name**: The display name of the plugin.
- **packageName**: The unique identifier for the plugin.
- **author**: The name of the plugin's creator.
- **version**: The current version of the plugin.
- **versionCode**: An internal version number that increases with each release.
- **script**: The main script file that contains the plugin's code.
- **description**: A brief description of the plugin.
- **icon**: The icon file used for the plugin.

### Example Plugin: HelloWorld
Our goal is to create a plugin that asks the user for their name and displays a greeting when the app starts. To do this, open the `main.bsh` file in the Karbon editor and add the following code:

```java
// The plugin system provides us with the variables 'app' and 'api'.
// 'app' is an instance of the Application class, and 'api' provides useful functions to simplify tasks.

// Our goal is to ask the user for their name and say hello when the app starts.

// Display an input popup asking for the user's name
title = "Enter Name";
msg = "Please enter your name";

// Implementing an interface to handle the user's input
onResult = InputInterface() {
    void onInputOK(input) {
        // Display a toast message with the user's name
        api.toast("Hello " + input);
    }
}

// Show the input popup
api.input(title, msg, onResult);


//NOTE this plugins will only run after a cold start

// That's it! You can call any function from Karbon or Android.
```

### Installing the Plugin
After saving the file, you need to copy your plugin into the plugin directory (`/data/data/com.rk.xededitor/plugins`). 

If you find this process complicated, you can also compress your plugin's files into a ZIP file and install it through the plugin settings in Karbon. 

This makes it easier to manage and install plugins without dealing with file paths manually.
