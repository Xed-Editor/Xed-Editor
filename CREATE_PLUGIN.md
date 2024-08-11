# Creating your first plugin
1. Go to **Settings >> Application** then click on **Access private app files**
2. now go back to main screen and open file browser
3. open folder named **"files"** then create a folder named **"plugins"** if not already there
4. create a folder **"MyPlugin"** inside that plugins folder
5. create a new file named **"manifest.json"** inside **"MyPlugin"** folder and paste this you modify this if you want

```
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

6. now create a new file named **"main.bsh"** inside **"MyPlugin"**
7. and put a png file named **"icon.png"** for icon (currently on version v2.6.0 karbon does not support adding files on private directory you have to find a workaround to add icon.png into private app files you can use terminal to download stuff)
8. open main.bsh
9. and write some code to show a toast to check if everything is working

```
//note : accessing kotlin classes is buggy and not supported
//import classes from karbon or java
import com.rk.xededitor.rkUtils;

//create a Runnable because plugins run on 
//a background thread and to intract with ui you should
// create a runnable and pass it to rkUtils.runOnUiThread method

Runnable myRunnable = new Runnable() {
    public void run() {

        //the 'app' variable is a instance of Application class this variable is passed by the plugin server
        rkUtils.toast(app,"yo");
    }
};

rkUtils.runOnUiThread(myRunnable);

```

10. lastly you have to enable your plugin from manage plugins activity (if your plugin is not visible in the manage plugin settings try force stopping the app)
11. thats it if you want to learn how to code in beanshell i highly recommend [beanshell quickstart](http://www.beanshell.org/manual/quickstart.html)
