plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktfmt)
}

android {
    namespace = "com.rk.ai"
    compileSdk {
        version = release(37)
    }

    defaultConfig { minSdk = 26 }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin { jvmToolchain(21) }

}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.animation)

    implementation(project(":core:main"))
    implementation(project(":core:components"))
    implementation(project(":core:resources"))

    implementation(project(":features:terminal"))

    implementation(libs.kotlinx.coroutines)

    implementation(libs.okhttp)

    implementation(libs.koog.agents)

    implementation("ai.koog:http-client-ktor:1.2.0")
}