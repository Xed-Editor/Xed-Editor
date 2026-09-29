package com.rk.application

import com.rk.App
import com.rk.TerminalFeature
import com.rk.ai.AiFeature
import com.rk.app.AppFlavour
import com.rk.feature.FeatureRegistry
import com.rk.git.GitFeature
import com.rk.runner.RunnerFeature

/**
 * Application entry point for the `playstore` distribution.
 *
 * The Play Store build deliberately does not bundle the `:features:extensions` module, because Play
 * policy forbids loading executable code at runtime, so its feature is not registered here.
 */
class XedApplication : App() {
    override fun onCreate() {
        // Publish the compile-time flavour (see app/build.gradle.kts) before any shared code runs.
        AppFlavour.init(BuildConfig.FLAVOUR)

        super.onCreate()

        // Register pluggable features
        FeatureRegistry.register(TerminalFeature())
        FeatureRegistry.register(RunnerFeature())
        FeatureRegistry.register(GitFeature())
        FeatureRegistry.register(AiFeature())

        // Initialize core features
        FeatureRegistry.initFeatures(this)
    }
}
