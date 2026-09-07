plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.terminal"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }
}

dependencies {
    implementation(project(":core:main"))
    implementation(project(":core:components"))
    implementation(project(":core:resources"))
    implementation(project(":features:runner"))
    implementation(project(":features:terminal:xed-cli"))
    implementation(project(":features:terminal:proot"))
    implementation(project(":features:terminal:link2symlink"))

    // Editor dependency for ExtraKeys code editor settings
    implementation(project(":editor"))
    
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.utilcode)
    implementation(libs.okhttp)
    implementation(libs.nanohttpd)
    implementation(libs.androidx.browser)
    implementation(libs.gson)
    implementation(libs.semver)
    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.core)

    implementation("com.github.termux.termux-app:terminal-view:v0.118.3")
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.3")

}
