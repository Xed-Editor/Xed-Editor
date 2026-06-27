package com.rk.application

import com.rk.App
import com.rk.feature.FeatureRegistry
import com.rk.terminal.TerminalFeature
import com.rk.extension.ExtensionFeature

class XedApplication : App() {
    override fun onCreate() {
        super.onCreate()

        // Register pluggable features
        FeatureRegistry.register(TerminalFeature())
        FeatureRegistry.register(ExtensionFeature())

        // Initialize features
        FeatureRegistry.initFeatures(this)
    }
}
