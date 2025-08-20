# Publishing Your Plugin to the Registry

This guide explains how to add your own plugin to the **Extensions Registry** hosted on GitHub.

## Project Structure

All plugins are stored inside the `plugins/` directory of this repository.
Each plugin must be placed in its own folder, named after its **Plugin ID** (the package name).

```
repo-root/
└── plugins/
    └── com.example.myplugin/   ← Plugin ID (package name)
        ├── plugin.apk          ← Compiled extension APK
        └── plugin.json         ← Plugin metadata file
```

## Plugin Metadata (`plugin.json`)

Each plugin must include a `plugin.json` file with the following structure:

```json
{
  "id": "com.example.myplugin",
  "name": "My Awesome Plugin",
  "mainClass": "com.example.myplugin.Main",
  "version": "1.0.0",
  "description": "A short description of what your plugin does.",
  "authors": ["Your Name"],
  "minAppVersion": -1,
  "targetAppVersion": -1,
  "icon": "icon.png",
  "screenshots": ["screenshot1.png", "screenshot2.png"],
  "repository": "https://github.com/yourname/myplugin",
  "tags": ["tag1", "tag2"]
}
```

### Field Details

* **id** → Unique identifier of your extension (**package name**).
* **name** → Human-readable plugin name.
* **mainClass** → Entry point class of your plugin.
* **version** → Plugin version (default: `"1.0.0"`).
* **description** → Short explanation of your plugin.
* **authors** → List of authors.
* **minAppVersion** → Minimum supported app version (`-1` = supports all).
* **targetAppVersion** → Targeted app version (`-1` = supports all).
* **icon** → Optional icon file.
* **screenshots** → List of screenshot image paths.
* **repository** → Link to plugin’s source code repo.
* **tags** → List of keywords describing the plugin.

## How to Publish Your Plugin

1. **Fork this repository**
   Click the **Fork** button in the top-right corner of this repo.

2. **Add your plugin**
   Inside the `plugins/` folder, create a new folder named after your **plugin ID (package name)**.
   Place your **`plugin.apk`** and **`plugin.json`** inside.

3. **Commit & Push**

   ```bash
   git add plugins/com.example.myplugin
   git commit -m "Add my awesome plugin"
   git push
   ```

4. **Open a Pull Request (PR)**
   Go to your fork on GitHub and open a PR back to the main repository.

---

## Example

```
plugins/
└── com.example.hello/
    ├── my-plugin.apk
    └── plugin.json
```

```json
{
  "id": "com.example.hello",
  "name": "Hello World Plugin",
  "mainClass": "com.example.hello.Main",
  "version": "1.0.0",
  "description": "Adds a friendly hello message.",
  "authors": ["CoolAuthor", "AnotherCoolAuthor"],
  "repository": "https://github.com/vivek/hello-plugin",
  "tags": ["greeting", "example"]
}
```

---

That’s it! Once your PR is merged, your plugin will be available in the registry.
