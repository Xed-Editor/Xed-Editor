package com.rk.projects

import com.rk.utils.application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git

/**
 * Writes the real files for a [ProjectConfig] onto disk.
 *
 * The scaffolder is pure file IO (java.io.File) so it works against any path the app can write to:
 * the sandbox home (exec-capable, required for build templates) or Documents/XED (fine for plain
 * editing). Callers decide the parent directory via [ProjectConfig.parentDir].
 */
object ProjectScaffolder {

    sealed interface Result {
        data class Success(val projectRoot: File) : Result

        data class Failure(val message: String, val cause: Throwable? = null) : Result
    }

    suspend fun scaffold(config: ProjectConfig): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                    val root = File(config.parentDir, config.name)
                    if (root.exists()) {
                        return@withContext Result.Failure("A project named \"${config.name}\" already exists here.")
                    }
                    if (!root.mkdirs()) {
                        return@withContext Result.Failure(
                            "Could not create project directory. Check storage permissions for ${config.parentDir.absolutePath}."
                        )
                    }

                    when (config.template) {
                        ProjectTemplate.NONE -> scaffoldNone(root, config)
                        ProjectTemplate.PYTHON3 -> scaffoldPython(root, config, python3 = true)
                        ProjectTemplate.PYTHON -> scaffoldPython(root, config, python3 = false)
                        ProjectTemplate.NODEJS -> scaffoldNode(root, config)
                        ProjectTemplate.WEB -> scaffoldWeb(root, config)
                        ProjectTemplate.MINECRAFT_MOD -> scaffoldMinecraft(root, config)
                        ProjectTemplate.ANDROID_COMPOSE -> scaffoldAndroidCompose(root, config)
                    }

                    if (config.initGit) initGitRepository(root, config)

                    Result.Success(root)
                }
                .getOrElse { Result.Failure(it.message ?: "Unknown error while creating project", it) }
        }

    // ---- helpers ----------------------------------------------------------------

    private fun File.write(relativePath: String, content: String) {
        val target = File(this, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(content.trimStart('\n'))
    }

    /**
     * Initialises a git repository and creates an initial commit. Failures are swallowed so a git
     * problem (e.g. on a filesystem that rejects it) never aborts project creation.
     */
    private fun initGitRepository(root: File, config: ProjectConfig) {
        runCatching {
            Git.init().setDirectory(root).call().use { git ->
                git.add().addFilepattern(".").call()
                val author = config.author.ifBlank { "Xed" }
                git.commit()
                    .setAuthor(author, "noreply@xed-editor")
                    .setCommitter(author, "noreply@xed-editor")
                    .setMessage("Initial commit")
                    .call()
            }
        }
    }

    // ---- None -------------------------------------------------------------------

    private fun scaffoldNone(root: File, config: ProjectConfig) {
        root.write("README.md", "# ${config.name}\n")
    }

    // ---- Python -----------------------------------------------------------------

    private fun scaffoldPython(root: File, config: ProjectConfig, python3: Boolean) {
        val shebang = if (python3) "#!/usr/bin/env python3" else "#!/usr/bin/env python"
        root.write(
            "main.py",
            """
            $shebang
            # ${config.name} - entry point


            def main() -> None:
                print("Hello from ${config.name}!")


            if __name__ == "__main__":
                main()
            """
                .trimIndent() + "\n",
        )
        root.write("requirements.txt", "")
        root.write(
            ".gitignore",
            """
            __pycache__/
            *.py[cod]
            .venv/
            venv/
            .env
            """
                .trimIndent() + "\n",
        )
        root.write("README.md", "# ${config.name}\n\nRun with:\n\n```bash\n${if (python3) "python3" else "python"} main.py\n```\n")
    }

    // ---- Node.js ----------------------------------------------------------------

    private fun scaffoldNode(root: File, config: ProjectConfig) {
        val pkg = config.name.lowercase().replace(Regex("[^a-z0-9-_]"), "-").trim('-').ifBlank { "app" }
        root.write(
            "package.json",
            """
            {
              "name": "$pkg",
              "version": "1.0.0",
              "description": "${config.name}",
              "main": "index.js",
              "type": "commonjs",
              "scripts": {
                "start": "node index.js",
                "test": "echo \"Error: no test specified\" && exit 1"
              },
              "keywords": [],
              "author": "${config.author}",
              "license": "MIT"
            }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "index.js",
            """
            "use strict";

            function main() {
              console.log("Hello from ${config.name}!");
            }

            main();
            """
                .trimIndent() + "\n",
        )
        root.write(
            ".gitignore",
            """
            node_modules/
            npm-debug.log*
            .env
            dist/
            """
                .trimIndent() + "\n",
        )
        root.write(
            "README.md",
            "# ${config.name}\n\n```bash\nnpm install\nnpm start\n```\n",
        )
    }

    // ---- Static Web -------------------------------------------------------------

    private fun scaffoldWeb(root: File, config: ProjectConfig) {
        root.write(
            "index.html",
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>${config.name}</title>
                <link rel="stylesheet" href="style.css" />
            </head>
            <body>
                <main>
                    <h1>${config.name}</h1>
                    <p id="message">Hello from ${config.name}!</p>
                </main>
                <script src="script.js"></script>
            </body>
            </html>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "style.css",
            """
            :root { color-scheme: light dark; }
            body {
                font-family: system-ui, sans-serif;
                margin: 0;
                display: grid;
                place-items: center;
                min-height: 100vh;
            }
            main { text-align: center; padding: 1.5rem; }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "script.js",
            """
            document.addEventListener("DOMContentLoaded", () => {
              const el = document.getElementById("message");
              if (el) el.textContent = "${config.name} is running!";
            });
            """
                .trimIndent() + "\n",
        )
        root.write(".gitignore", "node_modules/\ndist/\n.env\n")
        root.write("README.md", "# ${config.name}\n\nOpen `index.html` and use the HTML preview/runner.\n")
    }

    // ---- Minecraft --------------------------------------------------------------
    private fun scaffoldMinecraft(root: File, config: ProjectConfig) {
        when (config.modLoader) {
            ModLoader.FORGE -> scaffoldForge(root, config)
            else -> scaffoldFabric(root, config)
        }
    }

    private fun scaffoldFabric(root: File, config: ProjectConfig) {
        val modId = config.resolvedModId()
        val pkg = config.resolvedPackageName()
        val pkgPath = pkg.replace('.', '/')
        val mainClass = config.name.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "ExampleMod" }
        val mc = config.minecraftVersion.ifBlank { "1.21.1" }
        val jdk = config.jdkVersion.ifBlank { "21" }

        root.write(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx2G
            org.gradle.parallel=true

            # Fabric Properties (adjust loader/api/yarn versions for your target Minecraft version)
            minecraft_version=$mc
            yarn_mappings=$mc+build.1
            loader_version=0.16.9

            # Mod Properties
            mod_version=${config.modVersion}
            maven_group=$pkg
            archives_base_name=$modId

            # Fabric API
            fabric_version=0.100.0+$mc
            """
                .trimIndent() + "\n",
        )

        root.write(
            "settings.gradle",
            """
            pluginManagement {
                repositories {
                    maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "build.gradle",
            """
            plugins {
                id 'fabric-loom' version '1.7-SNAPSHOT'
                id 'maven-publish'
            }

            version = project.mod_version
            group = project.maven_group

            base { archivesName = project.archives_base_name }

            repositories {
                mavenCentral()
            }

            dependencies {
                minecraft "com.mojang:minecraft:${'$'}{project.minecraft_version}"
                mappings "net.fabricmc:yarn:${'$'}{project.yarn_mappings}:v2"
                modImplementation "net.fabricmc:fabric-loader:${'$'}{project.loader_version}"
                modImplementation "net.fabricmc.fabric-api:fabric-api:${'$'}{project.fabric_version}"
            }

            processResources {
                inputs.property "version", project.version
                filesMatching("fabric.mod.json") {
                    expand "version": project.version
                }
            }

            java {
                sourceCompatibility = JavaVersion.VERSION_$jdk
                targetCompatibility = JavaVersion.VERSION_$jdk
                withSourcesJar()
            }

            tasks.withType(JavaCompile).configureEach {
                it.options.release = $jdk
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/fabric.mod.json",
            """
            {
              "schemaVersion": 1,
              "id": "$modId",
              "version": "${'$'}{version}",
              "name": "${config.name}",
              "description": "${config.modDescription.ifBlank { config.name }}",
              "authors": ["${config.author}"],
              "license": "MIT",
              "environment": "*",
              "entrypoints": {
                "main": ["$pkg.$mainClass"]
              },
              "mixins": ["$modId.mixins.json"],
              "depends": {
                "fabricloader": ">=0.16.9",
                "minecraft": "~$mc",
                "java": ">=$jdk",
                "fabric-api": "*"
              }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/$modId.mixins.json",
            """
            {
              "required": true,
              "package": "$pkg.mixin",
              "compatibilityLevel": "JAVA_$jdk",
              "mixins": [],
              "client": [],
              "injectors": { "defaultRequire": 1 }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/java/$pkgPath/$mainClass.java",
            """
            package $pkg;

            import net.fabricmc.api.ModInitializer;
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            public class $mainClass implements ModInitializer {
                public static final String MOD_ID = "$modId";
                public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

                @Override
                public void onInitialize() {
                    LOGGER.info("${config.name} initialized!");
                }
            }
            """
                .trimIndent() + "\n",
        )

        writeGradleWrapper(root, "8.10")
        root.write("README.md", minecraftReadme(config, "Fabric", mc, jdk))
    }

    private fun scaffoldForge(root: File, config: ProjectConfig) {
        val modId = config.resolvedModId()
        val pkg = config.resolvedPackageName()
        val pkgPath = pkg.replace('.', '/')
        val mainClass = config.name.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "ExampleMod" }
        val mc = config.minecraftVersion.ifBlank { "1.20.1" }
        val jdk = config.jdkVersion.ifBlank { "17" }

        root.write(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx3G
            org.gradle.daemon=false

            minecraft_version=$mc
            # Pick a Forge build that matches your Minecraft version (see https://files.minecraftforge.net)
            forge_version=47.3.0
            mod_id=$modId
            mod_version=${config.modVersion}
            maven_group=$pkg
            """
                .trimIndent() + "\n",
        )

        root.write(
            "settings.gradle",
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    maven { url = 'https://maven.minecraftforge.net/' }
                }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "build.gradle",
            """
            plugins {
                id 'net.minecraftforge.gradle' version '[6.0,6.2)'
                id 'java'
            }

            version = project.mod_version
            group = project.maven_group

            java {
                toolchain.languageVersion = JavaLanguageVersion.of($jdk)
            }

            minecraft {
                mappings channel: 'official', version: project.minecraft_version
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                minecraft "net.minecraftforge:forge:${'$'}{project.minecraft_version}-${'$'}{project.forge_version}"
            }

            jar {
                manifest {
                    attributes([
                        "Specification-Title"   : project.mod_id,
                        "Implementation-Title"  : project.mod_id,
                        "Implementation-Version": project.version
                    ])
                }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/META-INF/mods.toml",
            """
            modLoader="javafml"
            loaderVersion="[47,)"
            license="MIT"

            [[mods]]
            modId="$modId"
            version="${config.modVersion}"
            displayName="${config.name}"
            authors="${config.author}"
            description='''
            ${config.modDescription.ifBlank { config.name }}
            '''

            [[dependencies.$modId]]
                modId="forge"
                mandatory=true
                versionRange="[47,)"
                ordering="NONE"
                side="BOTH"

            [[dependencies.$modId]]
                modId="minecraft"
                mandatory=true
                versionRange="[$mc]"
                ordering="NONE"
                side="BOTH"
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/pack.mcmeta",
            """
            {
              "pack": {
                "description": "${config.name} resources",
                "pack_format": 15
              }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/java/$pkgPath/$mainClass.java",
            """
            package $pkg;

            import net.minecraftforge.fml.common.Mod;
            import net.minecraftforge.eventbus.api.IEventBus;
            import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
            import org.slf4j.Logger;
            import com.mojang.logging.LogUtils;

            @Mod($mainClass.MOD_ID)
            public class $mainClass {
                public static final String MOD_ID = "$modId";
                private static final Logger LOGGER = LogUtils.getLogger();

                public $mainClass() {
                    IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
                    LOGGER.info("${config.name} loading");
                }
            }
            """
                .trimIndent() + "\n",
        )

        writeGradleWrapper(root, "8.8")
        root.write("README.md", minecraftReadme(config, "Forge", mc, jdk))
    }

    private fun minecraftReadme(config: ProjectConfig, loader: String, mc: String, jdk: String): String =
        """
        # ${config.name}

        $loader mod for Minecraft $mc (Java $jdk).

        > Built with Gradle. Run from an exec-capable filesystem (the terminal sandbox),
        > not from /sdcard (Documents/Downloads), because shared storage is mounted noexec.

        ```bash
        ./gradlew build
        ```
        """
            .trimIndent() + "\n"

    // ---- Android Jetpack Compose (faithful Android Studio "Empty Activity" project) -----

    private fun scaffoldAndroidCompose(root: File, config: ProjectConfig) {
        val pkg = config.resolvedPackageName()
        val pkgPath = pkg.replace('.', '/')
        val jdk = config.jdkVersion.ifBlank { "17" }
        val appNoSpace = config.name.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "App" }
        val themeName = appNoSpace.replaceFirstChar { it.uppercase() } + "Theme"
        val styleName = "Theme.$appNoSpace"

        // settings.gradle.kts
        root.write(
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    google {
                        content {
                            includeGroupByRegex("com\.android.*")
                            includeGroupByRegex("com\.google.*")
                            includeGroupByRegex("androidx.*")
                        }
                    }
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = "${config.name}"
            include(":app")
            """
                .trimIndent() + "\n",
        )

        // Root build.gradle.kts (version-catalog aliases)
        root.write(
            "build.gradle.kts",
            """
            // Top-level build file where you can add configuration options common to all sub-projects/modules.
            plugins {
                alias(libs.plugins.android.application) apply false
                alias(libs.plugins.kotlin.android) apply false
                alias(libs.plugins.kotlin.compose) apply false
            }
            """
                .trimIndent() + "\n",
        )

        // Version catalog
        root.write(
            "gradle/libs.versions.toml",
            """
            [versions]
            agp = "8.5.2"
            kotlin = "2.0.0"
            coreKtx = "1.13.1"
            junit = "4.13.2"
            junitVersion = "1.2.1"
            espressoCore = "3.6.1"
            lifecycleRuntimeKtx = "2.8.6"
            activityCompose = "1.9.2"
            composeBom = "2024.09.00"

            [libraries]
            androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
            junit = { group = "junit", name = "junit", version.ref = "junit" }
            androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
            androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
            androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
            androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
            androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
            androidx-ui = { group = "androidx.compose.ui", name = "ui" }
            androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
            androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
            androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
            androidx-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
            androidx-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
            androidx-material3 = { group = "androidx.compose.material3", name = "material3" }

            [plugins]
            android-application = { id = "com.android.application", version.ref = "agp" }
            kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
            kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
            """
                .trimIndent() + "\n",
        )

        // gradle.properties
        root.write(
            "gradle.properties",
            """
            # Project-wide Gradle settings.
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            org.gradle.parallel=true
            android.useAndroidX=true
            kotlin.code.style=official
            android.nonTransitiveRClass=true
            """
                .trimIndent() + "\n",
        )

        // app/build.gradle.kts
        root.write(
            "app/build.gradle.kts",
            """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.android)
                alias(libs.plugins.kotlin.compose)
            }

            android {
                namespace = "$pkg"
                compileSdk = 34

                defaultConfig {
                    applicationId = "$pkg"
                    minSdk = 24
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0"

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    vectorDrawables { useSupportLibrary = true }
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_$jdk
                    targetCompatibility = JavaVersion.VERSION_$jdk
                }
                kotlinOptions { jvmTarget = "$jdk" }
                buildFeatures { compose = true }
            }

            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(platform(libs.androidx.compose.bom))
                implementation(libs.androidx.ui)
                implementation(libs.androidx.ui.graphics)
                implementation(libs.androidx.ui.tooling.preview)
                implementation(libs.androidx.material3)
                testImplementation(libs.junit)
                androidTestImplementation(libs.androidx.junit)
                androidTestImplementation(libs.androidx.espresso.core)
                androidTestImplementation(platform(libs.androidx.compose.bom))
                androidTestImplementation(libs.androidx.ui.test.junit4)
                debugImplementation(libs.androidx.ui.tooling)
                debugImplementation(libs.androidx.ui.test.manifest)
            }
            """
                .trimIndent() + "\n",
        )

        root.write("app/proguard-rules.pro", "# Add project specific ProGuard rules here.\n")
        root.write("app/.gitignore", "/build\n")

        // AndroidManifest.xml
        root.write(
            "app/src/main/AndroidManifest.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools">

                <application
                    android:allowBackup="true"
                    android:dataExtractionRules="@xml/data_extraction_rules"
                    android:fullBackupContent="@xml/backup_rules"
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name"
                    android:roundIcon="@mipmap/ic_launcher_round"
                    android:supportsRtl="true"
                    android:theme="@style/$styleName"
                    tools:targetApi="31">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true"
                        android:label="@string/app_name"
                        android:theme="@style/$styleName">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
            """
                .trimIndent() + "\n",
        )

        // MainActivity.kt
        root.write(
            "app/src/main/java/$pkgPath/MainActivity.kt",
            """
            package $pkg

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.activity.enableEdgeToEdge
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.Scaffold
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.tooling.preview.Preview
            import $pkg.ui.theme.$themeName

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    enableEdgeToEdge()
                    setContent {
                        $themeName {
                            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                Greeting(
                                    name = "Android",
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }
                        }
                    }
                }
            }

            @Composable
            fun Greeting(name: String, modifier: Modifier = Modifier) {
                Text(text = "Hello ${'$'}name!", modifier = modifier)
            }

            @Preview(showBackground = true)
            @Composable
            fun GreetingPreview() {
                $themeName { Greeting("Android") }
            }
            """
                .trimIndent() + "\n",
        )

        // Theme package
        root.write(
            "app/src/main/java/$pkgPath/ui/theme/Color.kt",
            """
            package $pkg.ui.theme

            import androidx.compose.ui.graphics.Color

            val Purple80 = Color(0xFFD0BCFF)
            val PurpleGrey80 = Color(0xFFCCC2DC)
            val Pink80 = Color(0xFFEFB8C8)

            val Purple40 = Color(0xFF6650a4)
            val PurpleGrey40 = Color(0xFF625b71)
            val Pink40 = Color(0xFF7D5260)
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/java/$pkgPath/ui/theme/Theme.kt",
            """
            package $pkg.ui.theme

            import android.os.Build
            import androidx.compose.foundation.isSystemInDarkTheme
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.darkColorScheme
            import androidx.compose.material3.dynamicDarkColorScheme
            import androidx.compose.material3.dynamicLightColorScheme
            import androidx.compose.material3.lightColorScheme
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.platform.LocalContext

            private val DarkColorScheme = darkColorScheme(
                primary = Purple80,
                secondary = PurpleGrey80,
                tertiary = Pink80,
            )

            private val LightColorScheme = lightColorScheme(
                primary = Purple40,
                secondary = PurpleGrey40,
                tertiary = Pink40,
            )

            @Composable
            fun $themeName(
                darkTheme: Boolean = isSystemInDarkTheme(),
                dynamicColor: Boolean = true,
                content: @Composable () -> Unit,
            ) {
                val colorScheme = when {
                    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        val context = LocalContext.current
                        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }
                    darkTheme -> DarkColorScheme
                    else -> LightColorScheme
                }

                MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/java/$pkgPath/ui/theme/Type.kt",
            """
            package $pkg.ui.theme

            import androidx.compose.material3.Typography
            import androidx.compose.ui.text.TextStyle
            import androidx.compose.ui.text.font.FontFamily
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.sp

            val Typography = Typography(
                bodyLarge = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.5.sp,
                ),
            )
            """
                .trimIndent() + "\n",
        )

        // Resources
        root.write(
            "app/src/main/res/values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">${config.name}</string>
            </resources>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/values/colors.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="black">#FF000000</color>
                <color name="white">#FFFFFFFF</color>
            </resources>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/values/themes.xml",
            """
            <resources xmlns:tools="http://schemas.android.com/tools">
                <style name="$styleName" parent="android:Theme.Material.Light.NoActionBar" />
            </resources>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/values-night/themes.xml",
            """
            <resources xmlns:tools="http://schemas.android.com/tools">
                <style name="$styleName" parent="android:Theme.Material.NoActionBar" />
            </resources>
            """
                .trimIndent() + "\n",
        )

        // Backup / data-extraction rules
        root.write(
            "app/src/main/res/xml/backup_rules.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <full-backup-content>
            </full-backup-content>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/xml/data_extraction_rules.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <data-extraction-rules>
                <cloud-backup>
                </cloud-backup>
            </data-extraction-rules>
            """
                .trimIndent() + "\n",
        )

        // Launcher icons (vector adaptive + legacy fallback — fully buildable without binaries)
        root.write(
            "app/src/main/res/drawable/ic_launcher_background.xml",
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path android:fillColor="#3DDC84" android:pathData="M0,0h108v108h-108z" />
            </vector>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/drawable/ic_launcher_foreground.xml",
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path android:fillColor="#FFFFFF" android:pathData="M54,34 a20,20 0 1,0 0,40 a20,20 0 1,0 0,-40 z" />
            </vector>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@drawable/ic_launcher_background" />
                <foreground android:drawable="@drawable/ic_launcher_foreground" />
                <monochrome android:drawable="@drawable/ic_launcher_foreground" />
            </adaptive-icon>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@drawable/ic_launcher_background" />
                <foreground android:drawable="@drawable/ic_launcher_foreground" />
                <monochrome android:drawable="@drawable/ic_launcher_foreground" />
            </adaptive-icon>
            """
                .trimIndent() + "\n",
        )
        // Legacy (<26) fallback so the icon resolves on all minSdk-24 devices without binary assets.
        val legacyIcon =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
                <item android:drawable="@drawable/ic_launcher_background" />
                <item android:drawable="@drawable/ic_launcher_foreground" />
            </layer-list>
            """
                .trimIndent() + "\n"
        root.write("app/src/main/res/mipmap/ic_launcher.xml", legacyIcon)
        root.write("app/src/main/res/mipmap/ic_launcher_round.xml", legacyIcon)

        // Tests
        root.write(
            "app/src/test/java/$pkgPath/ExampleUnitTest.kt",
            """
            package $pkg

            import org.junit.Assert.assertEquals
            import org.junit.Test

            class ExampleUnitTest {
                @Test
                fun addition_isCorrect() {
                    assertEquals(4, 2 + 2)
                }
            }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/androidTest/java/$pkgPath/ExampleInstrumentedTest.kt",
            """
            package $pkg

            import androidx.test.ext.junit.runners.AndroidJUnit4
            import androidx.test.platform.app.InstrumentationRegistry
            import org.junit.Assert.assertEquals
            import org.junit.Test
            import org.junit.runner.RunWith

            @RunWith(AndroidJUnit4::class)
            class ExampleInstrumentedTest {
                @Test
                fun useAppContext() {
                    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
                    assertEquals("$pkg", appContext.packageName)
                }
            }
            """
                .trimIndent() + "\n",
        )

        // Root .gitignore (Android Studio default)
        root.write(
            ".gitignore",
            """
            *.iml
            .gradle
            /local.properties
            /.idea
            .DS_Store
            /build
            /captures
            .externalNativeBuild
            .cxx
            local.properties
            """
                .trimIndent() + "\n",
        )

        writeGradleWrapper(root, "8.9")

        root.write(
            "README.md",
            """
            # ${config.name}

            A Jetpack Compose Android app generated as a complete Android Studio project
            (version catalog, theme package, adaptive icons, tests).

            ## Building
            Requires the Android SDK. Install it from the editor's **Dependencies** download dialog,
            or point Gradle at an existing SDK via `local.properties`:

            ```
            sdk.dir=/path/to/android-sdk
            ```

            Then:

            ```bash
            ./gradlew assembleDebug
            ```

            > Build from an exec-capable filesystem (the terminal sandbox), not /sdcard.
            """
                .trimIndent() + "\n",
        )
    }

    // ---- Gradle wrapper (real, working: scripts + jar copied from bundled assets) ------

    private fun writeGradleWrapper(root: File, gradleVersion: String) {
        root.write(
            "gradle/wrapper/gradle-wrapper.properties",
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """
                .trimIndent() + "\n",
        )

        // Copy the bundled wrapper jar + launcher scripts so `./gradlew` works out of the box.
        copyAsset("templates/gradle-wrapper.jar", File(root, "gradle/wrapper/gradle-wrapper.jar"))
        val gradlew = File(root, "gradlew")
        copyAsset("templates/gradlew", gradlew)
        copyAsset("templates/gradlew.bat", File(root, "gradlew.bat"))
        runCatching { gradlew.setExecutable(true, false) }
    }

    /** Copies a bundled asset to [target], creating parent dirs. No-op (logged) if the asset is missing. */
    private fun copyAsset(assetPath: String, target: File) {
        runCatching {
            target.parentFile?.mkdirs()
            application!!.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
