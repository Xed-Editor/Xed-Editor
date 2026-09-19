package com.rk.application

import com.rk.App
import com.rk.ExtensionFeature
import com.rk.TerminalFeature
import com.rk.feature.FeatureRegistry
import com.rk.git.GitFeature
import com.rk.ai.AiFeature
import com.rk.runner.RunnerFeature

class XedApplication : App() {
    override fun onCreate() {
        super.onCreate()

        // Register pluggable features
        FeatureRegistry.register(TerminalFeature())
        FeatureRegistry.register(ExtensionFeature())
        FeatureRegistry.register(RunnerFeature())
        FeatureRegistry.register(GitFeature())
        FeatureRegistry.register(AiFeature())

        // Initialize core features
        FeatureRegistry.initFeatures(this)
    }
}
