// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinParcelize) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktfmt) apply false
}

subprojects {
    plugins.withId(rootProject.libs.plugins.ktfmt.get().pluginId) {
        configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
            kotlinLangStyle()
            maxWidth.set(120)
        }
    }
}