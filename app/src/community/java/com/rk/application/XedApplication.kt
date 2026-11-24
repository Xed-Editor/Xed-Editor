package com.rk.application

import com.rk.App
import com.rk.ExtensionFeature
import com.rk.TerminalFeature
import com.rk.ai.AiFeature
import com.rk.app.AppFlavour
import com.rk.feature.FeatureRegistry
import com.rk.git.GitFeature
import com.rk.runner.RunnerFeature
import com.rk.store.StoreFeature

/**
 * Application entry point for the `community` distribution.
 *
 * Registers every feature, including the store (`:features:store`, which ships themes and icon packs) and
 * `ExtensionFeature` provided by the bundled `:features:extensions` module.
 */
class XedApplication : App() {
    override fun onCreate() {
        // Publish the compile-time flavour (see app/build.gradle.kts) before any shared code runs.
        AppFlavour.init(BuildConfig.FLAVOUR)

        super.onCreate()

        // Register pluggable features
        FeatureRegistry.register(TerminalFeature())
        FeatureRegistry.register(StoreFeature())
        FeatureRegistry.register(ExtensionFeature())
        FeatureRegistry.register(RunnerFeature())
        FeatureRegistry.register(GitFeature())
        FeatureRegistry.register(AiFeature())

        // Initialize core features
        FeatureRegistry.initFeatures(this)
    }
}
