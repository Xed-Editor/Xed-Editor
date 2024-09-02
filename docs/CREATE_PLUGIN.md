## **Creating Your First Plugin**

### **Step 1: Enable Access to App Files**
1. Open the app and navigate to **Settings > Application**.
2. Click on **Access private app files**.

### **Step 2: Navigate to the File Browser**
1. Return to the main screen of the app.
2. Open the **File Browser**.

### **Step 3: Create the Plugin Directory**
1. Locate and open the folder named **"files"**.
2. Inside the **"files"** folder, create a new folder named **"plugins"** (if it doesn't already exist).

### **Step 4: Create Your Plugin Folder**
1. Inside the **"plugins"** folder, create a new folder named **"MyPlugin"**.

### **Step 5: Create and Configure `manifest.json`**
1. In the **"MyPlugin"** folder, create a new file named **"manifest.json"**.
2. Copy and paste the following code into `manifest.json`, then modify the details as needed:
   ```json
   {
     "name": "MyPlugin",
     "packageName": "com.example.myplugin",
     "author": "John Doe",
     "version": "1.0.0",
     "versionCode": 1,
     "script": "main.bsh",
     "icon": "icon.png"
   }
   ```

### **Step 6: Create the Script File**
1. Still inside the **"MyPlugin"** folder, create a new file named **"main.bsh"**.

### **Step 7: Add an Icon
1. Include a PNG file named **"icon.png"** in the **"MyPlugin"** folder.
   
### **Step 8: Write Your Plugin Code**
1. Open the **"main.bsh"** file you created earlier.
2. Write the following code to display a toast message, which will verify that your plugin is working correctly:

   ```java

   //never forget to adding semicolon in the end of expressions
 
   //show a toast
   api.toast("hello from plugin");
   
   ```
 3. See [API.kt](https://github.com/RohitKushvaha01/Xed-Editor/blob/dev/libPlugin/src/main/java/com/rk/libPlugin/server/api/API.kt) for available api calls

4. Read [API Documentation](/docs/APIDocumentation) 

5. Application context is globally available similar to api class you can use "app" anyware in the script
   

### **Step 9: Enable Your Plugin**
1. Go to **Manage Plugins** in the app's settings.
2. Enable your plugin from the list.
   
   **Note:** If your plugin is not visible in the Manage Plugins settings, try force-stopping the app and reopening it.

### **Step 10: Test Your Plugin**
1. Your plugin should now be enabled and functioning. If you'd like to learn more about coding in BeanShell, you can refer to the [BeanShell Quickstart Guide](http://www.beanshell.org/manual/quickstart.html).
