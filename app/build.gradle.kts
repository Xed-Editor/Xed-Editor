import java.io.ByteArrayOutputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}


fun getGitCommitHash(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}

fun getGitCommitDate(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "show", "-s", "--format=%cI", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}

fun getFullGitCommitHash(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}




android {
    namespace = "com.rk.xededitor"
    compileSdk = 34
    android.buildFeatures.buildConfig = true
    
    
    println("Building for commit ${getGitCommitHash()}")
    
    
    //todo remove this before release
    lintOptions {
        disable("MissingTranslation")
    }
    
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    
    signingConfigs {
        create("release") {
            val isGITHUB_ACTION = System.getenv("GITHUB_ACTIONS") == "true"
            
            val propertiesFilePath = if (isGITHUB_ACTION) {
                "/tmp/signing.properties"
            } else {
                "/home/rohit/signing.properties"
            }
            
            val propertiesFile = File(propertiesFilePath)
            if (propertiesFile.exists()) {
                val properties = Properties()
                properties.load(propertiesFile.inputStream())
                keyAlias = properties["keyAlias"] as String?
                keyPassword = properties["keyPassword"] as String?
                storeFile = if (isGITHUB_ACTION) {
                    File("/tmp/xed.keystore")
                } else {
                    (properties["storeFile"] as String?)?.let { File(it) }
                }
                
                storePassword = properties["storePassword"] as String?
            } else {
                println("Signing properties file not found at $propertiesFilePath")
            }
        }
        getByName("debug") {
            storeFile = file(layout.buildDirectory.dir("../testkey.keystore"))
            storePassword = "testkey"
            keyAlias = "testkey"
            keyPassword = "testkey"
        }
    }
    
    
    buildTypes {
        getByName("release") {
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${getFullGitCommitHash()}\"")
            buildConfigField("String", "GIT_SHORT_COMMIT_HASH", "\"${getGitCommitHash()}\"")
            buildConfigField("String", "GIT_COMMIT_DATE", "\"${getGitCommitDate()}\"")
            isMinifyEnabled = false
            isCrunchPngs = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${getFullGitCommitHash()}\"")
            buildConfigField("String", "GIT_SHORT_COMMIT_HASH", "\"${getGitCommitHash()}\"")
            buildConfigField("String", "GIT_COMMIT_DATE", "\"${getGitCommitDate()}\"")
        }
    }
    
    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 26
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 34
        versionName = "2.7.4"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    
    buildFeatures {
        viewBinding = true
        compose = true
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources {
            exclude("META-INF/**")
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.asynclayoutinflater)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.activity)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.navigation.compose)
    implementation(libs.terminal.view)
    implementation(libs.terminal.emulator)
    implementation(libs.utilcode)
    implementation(libs.sshj)
    implementation(libs.commons.net)
    implementation(libs.gson)
    implementation(libs.jcodings)
    implementation(libs.joni)
    implementation(libs.snakeyaml.engine)
    implementation(libs.jdt.annotation)
    implementation(libs.ktsh)
    implementation(libs.swiperefreshlayout)
    implementation(libs.okhttp)
    implementation(libs.org.eclipse.jgit)
    implementation(libs.coil.compose)
    implementation(libs.bsh)
    implementation(libs.material.motion.compose.core)
    implementation(libs.datastore.preferences)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":feature:runner"))
    implementation(project(":feature:filetree"))
    implementation(project(":feature:settings"))
    implementation(project(":core:commons"))
    implementation(project(":core:components"))
    implementation(project(":editor:editor"))
    implementation(project(":editor:language-textmate"))
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
