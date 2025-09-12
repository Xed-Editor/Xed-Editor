plugins {
    //alias(libs.plugins.android.test)
    id("com.android.test")
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.rk.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // This benchmark buildType is used for benchmarking, and should function like your
        // release build (for example, with minification on). It"s signed with a debug key
        // for easy local/CI testing.
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }

    flavorDimensions += listOf("store")
    productFlavors {
        create("Fdroid") { dimension = "store" }
        create("PlayStore") { dimension = "store" }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.ext.junit)
    implementation(libs.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}