After installing termux you must allow **Karbon**, permission to run commands in termux environment
This permission can be enabled by following these steps:

<img src="screenshots/execPerm.png" />
<img src="screenshots/Perm.png" />
<img src="screenshots/Aperm.png" />

After allowing execute permission we need to allow external apps in termux.properties

run :

```bash
nano ~/.termux/termux.properties
```

then add ```allow-external-apps = true```

<img src="screenshots/externalApp.png" />

after this open **karbon >> settings >> terminal** if everything is done right the termux-exec switch will be active

Make sure to grant **Display Over other apps** permission to termux
