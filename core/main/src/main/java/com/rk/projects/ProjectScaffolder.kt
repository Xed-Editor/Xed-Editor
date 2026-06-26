package com.rk.projects

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

    // ---- Minecraft --------------------------------------------------------------

    private fun scaffoldMinecraft(root: File, config: ProjectConfig) {
        when (config.modLoader) {
            ModLoader.FORGE -> scaffoldForge(root, config)
            else -> scaffoldFabric(root, config)
        }
    }

    private fun scaffoldFabric(root: File, config: ProjectConfig) {
        val modId = config.resolvedModId()
        val pkg = config.packageName.ifBlank { "com.example.$modId" }
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
        val pkg = config.packageName.ifBlank { "com.example.$modId" }
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

    // ---- Android Jetpack Compose ------------------------------------------------

    private fun scaffoldAndroidCompose(root: File, config: ProjectConfig) {
        val pkg = config.packageName.ifBlank { "com.example.${config.name.lowercase().replace(Regex("[^a-z0-9]"), "")}" }
        val pkgPath = pkg.replace('.', '/')
        val jdk = config.jdkVersion.ifBlank { "17" }
        val appLabel = config.name

        root.write(
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    google()
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

        root.write(
            "build.gradle.kts",
            """
            plugins {
                id("com.android.application") version "8.5.2" apply false
                id("org.jetbrains.kotlin.android") version "2.0.0" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx2048m
            android.useAndroidX=true
            kotlin.code.style=official
            android.nonTransitiveRClass=true
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/build.gradle.kts",
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
                id("org.jetbrains.kotlin.plugin.compose")
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
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
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
                val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
                implementation(composeBom)
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.activity:activity-compose:1.9.2")
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.material3:material3")
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/AndroidManifest.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">

                <application
                    android:allowBackup="true"
                    android:label="$appLabel"
                    android:theme="@style/Theme.App">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
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

        root.write(
            "app/src/main/java/$pkgPath/MainActivity.kt",
            """
            package $pkg

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Surface
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            Surface { Greeting("$appLabel") }
                        }
                    }
                }
            }

            @Composable
            fun Greeting(name: String) {
                Text(text = "Hello, ${'$'}name!")
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/res/values/themes.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar" />
            </resources>
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/res/values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">$appLabel</string>
            </resources>
            """
                .trimIndent() + "\n",
        )

        root.write(
            ".gitignore",
            """
            *.iml
            .gradle/
            local.properties
            .idea/
            build/
            captures/
            .externalNativeBuild/
            .cxx/
            """
                .trimIndent() + "\n",
        )

        writeGradleWrapper(root, "8.9")
        root.write(
            "README.md",
            """
            # ${config.name}

            Jetpack Compose Android app (Java $jdk).

            > Build from an exec-capable filesystem (the terminal sandbox), not /sdcard.
            > The Gradle wrapper jar is fetched on first run via `gradle wrapper` if missing.

            ```bash
            ./gradlew assembleDebug
            ```
            """
                .trimIndent() + "\n",
        )
    }

    // ---- Gradle wrapper (properties only; the jar is fetched on first run) -------

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
    }
}
