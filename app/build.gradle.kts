import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest

import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}


android {
    namespace = "com.rk.application"
    compileSdk = 35

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
                "/home/rohit/Android/xed-signing/signing.properties"
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
        release {
            isMinifyEnabled = false
            isCrunchPngs = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "app_name", "Xed-Debug")
        }
    }

    flavorDimensions += "store"

    productFlavors {
        create("Fdroid") {
            dimension = "store"
            targetSdk = 28
        }

        create("PlayStore") {
            dimension = "store"
            targetSdk = 35
        }
    }

    //values in this will be overridden by the flavours
    defaultConfig {
        applicationId = "com.rk.xededitor"
        minSdk = 26

        //needed for running proot
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28

        //versioning
        versionCode = 56
        versionName = "3.1.3"
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
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }


}

//androidComponents {
//    beforeVariants { variantBuilder ->
//        if (variantBuilder.flavorName == "playStore") {
//            variantBuilder.enable = true
//        }
//    }
//
//    onVariants { variant ->
//        if (variant.productFlavors.any { it.second == "PlayStore" }) {
//            dependencies {
//                add(variant.name + "Implementation", project(":core:xed"))
//            }
//        }
//    }
//}


fun downloadFile(localUrl: String, remoteUrl: String, expectedChecksum: String) {
    val digest = MessageDigest.getInstance("SHA-256")

    val file = File(projectDir, localUrl)
    if (file.exists()) {
        val buffer = ByteArray(8192)
        val input = FileInputStream(file)
        while (true) {
            val readBytes = input.read(buffer)
            if (readBytes < 0) break
            digest.update(buffer, 0, readBytes)
        }
        var checksum = BigInteger(1, digest.digest()).toString(16)
        while (checksum.length < 64) { checksum = "0$checksum" }
        if (checksum == expectedChecksum) {
            return
        } else {
            logger.warn("Deleting old local file with wrong hash: $localUrl: expected: $expectedChecksum, actual: $checksum")
            file.delete()
        }
    }

    logger.quiet("Downloading $remoteUrl ...")

    file.parentFile.mkdirs()
    val out = BufferedOutputStream(FileOutputStream(file))

    val connection = URI(remoteUrl).toURL().openConnection()
    val digestStream = DigestInputStream(connection.inputStream, digest)
    digestStream.transferTo(out)
    out.close()

    var checksum = BigInteger(1, digest.digest()).toString(16)
    while (checksum.length < 64) { checksum = "0$checksum" }
    if (checksum != expectedChecksum) {
        file.delete()
        throw GradleException("Wrong checksum for $remoteUrl:\n Expected: $expectedChecksum\n Actual:   $checksum")
    }
}

tasks.register("downloadPrebuilt") {
    doLast {
        val prootTag = "proot-2025.01.15-r2"
        val prootVersion = "5.1.107-66"
        var prootUrl = "https://github.com/termux-play-store/termux-packages/releases/download/${prootTag}/libproot-loader-ARCH-${prootVersion}.so"

        downloadFile("src/main/jniLibs/armeabi-v7a/libproot-loader.so", prootUrl.replace("ARCH", "arm"), "eb1d64e9ef875039534ce7a8eeffa61bbc4c0ae5722cb48c9112816b43646a3e")
        downloadFile("src/main/jniLibs/arm64-v8a/libproot-loader.so", prootUrl.replace("ARCH", "aarch64"), "8814b72f760cd26afe5350a1468cabb6622b4871064947733fcd9cd06f1c8cb8")
        downloadFile("src/main/jniLibs/x86_64/libproot-loader.so", prootUrl.replace("ARCH", "x86_64"), "1a52cc9cc5fdecbf4235659ffeac8c51e4fefd7c75cc205f52d4884a3a0a0ba1")
        prootUrl = "https://github.com/termux-play-store/termux-packages/releases/download/${prootTag}/libproot-loader32-ARCH-${prootVersion}.so"
        downloadFile("src/main/jniLibs/arm64-v8a/libproot-loader32.so", prootUrl.replace("ARCH", "aarch64"), "ff56a5e3a37104f6778420d912e3edf31395c15d1528d28f0eb7d13a64481b99")
        downloadFile("src/main/jniLibs/x86_64/libproot-loader32.so", prootUrl.replace("ARCH", "x86_64"), "5460a597e473f57f0d33405891e35ca24709173ca0a38805d395e3544ab8b1b4")
    }
}

tasks.register("removeProotLoaders"){
    fun rm(path:String){
        val file = File(projectDir,path)

        if(file.exists()){
            logger.quiet("Deleting $path")
            if(file.delete().not()){
                logger.error("Failed to delete $path")
            }
        }
    }

    rm("src/main/jniLibs/armeabi-v7a/libproot-loader.so")
    rm("src/main/jniLibs/arm64-v8a/libproot-loader.so")
    rm("src/main/jniLibs/x86_64/libproot-loader.so")
    rm("src/main/jniLibs/arm64-v8a/libproot-loader32.so")
    rm("src/main/jniLibs/x86_64/libproot-loader32.so")
}

afterEvaluate {
    android.applicationVariants.all { variant ->
        if (variant.flavorName == "PlayStore") {
            variant.javaCompileProvider.dependsOn("downloadPrebuilt")
        }else{
            variant.javaCompileProvider.dependsOn("removeProotLoaders")
        }
        true
    }
}




dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:main"))
}
