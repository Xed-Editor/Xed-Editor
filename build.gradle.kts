plugins {
    alias(libs.plugins.android.baselineprofile) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.ktfmt) apply false
}

subprojects {
    plugins.withId(rootProject.libs.plugins.ktfmt.get().pluginId) {
        configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
            kotlinLangStyle()
            maxWidth.set(120)
        }
    }

    plugins.withId(rootProject.libs.plugins.android.library.get().pluginId) {
        configure<com.android.build.api.dsl.LibraryExtension> {
            defaultConfig {
                externalNativeBuild {
                    cmake {
                        // Force identical linker optimization (O2) on all environments
                        arguments(
                            "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-O2",
                            "-DCMAKE_EXE_LINKER_FLAGS=-Wl,-O2",
                        )
                    }
                }
            }
        }
    }
}
