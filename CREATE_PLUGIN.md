# Creating plugins for Xed Editor

## Quick start
```
#clone this repo

git clone https://github.com/RohitKushvaha01/xedPluginDemo


#profit
```





## if you want to
## Create the project from scratch


- Create a empty project in android studio
![Screenshot from 2024-06-25 11-17-20](https://github.com/RohitKushvaha01/Xed-Editor/assets/99863818/6791db46-0987-46b9-b5f3-ded2038dd2bf)


- Download xedPlugin.aar library from [Releases](https://github.com/RohitKushvaha01/Xed-Editor/releases)
- Put this aar file inside your $PROJECT/libs
![Screenshot from 2024-06-25 11-23-25](https://github.com/RohitKushvaha01/Xed-Editor/assets/99863818/48c188be-8b30-46ee-8128-eb8b85b3fb10)

- Add the dependencies
```
dependencies {
    implementation(libs.material)?.let { runtimeOnly(it) }
    implementation(platform("io.github.Rosemoe.sora-editor:bom:0.23.4"))?.let { runtimeOnly(it) }
    implementation("io.github.Rosemoe.sora-editor:editor")?.let { runtimeOnly(it) }
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")?.let { runtimeOnly(it) }
    implementation(files("../libs/xedPlugin.aar"))
}
```
![Screenshot from 2024-06-25 11-26-41](https://github.com/RohitKushvaha01/Xed-Editor/assets/99863818/966a8135-96e2-4a21-abb7-1ac538e67ade)


- Add Metadata in the Manifest.xml
```
<meta-data android:name="xedpluginAPI" android:value="1"/>

 <!-- fully qualified class name that extends API   -->
<meta-data android:name="EntryPoint" android:value="com.rk.pluginTest"/>
```
![Screenshot from 2024-06-25 11-34-56](https://github.com/RohitKushvaha01/Xed-Editor/assets/99863818/67ab67f1-ee6c-40a3-acb7-35a65a16d43c)



- Extends API class

![Screenshot from 2024-06-25 11-37-12](https://github.com/RohitKushvaha01/Xed-Editor/assets/99863818/5d73040f-28dc-40b9-bec7-7c5e8edf335f)

- Dontobfuscate
add "-dontobfuscate" into the proguard rules

![Screenshot from 2024-06-25 11-41-35](https://github.com/RohitKushvaha01/Xed-Editor/assets/99863818/60f2bd7c-26bd-40e2-b7f2-5756e39468af)


- Disable minification
![Screenshot from 2024-06-25 11-42-50](https://github.com/RohitKushvaha01/Xed-Editor/assets/99863818/0e997a7c-087b-408a-a1b8-4e375dc8c0cd)

## Read APIDocumentation.md for the API documentation 



