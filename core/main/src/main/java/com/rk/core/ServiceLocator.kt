package com.rk.core

import android.content.Context
import com.rk.ai.service.IdeService
import com.rk.ai.service.IdeServiceImpl
import com.rk.settings.SecureSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object ServiceLocator {
    private var applicationContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ideService: IdeService? = null

    private val services = mutableMapOf<String, Any>()

    fun init(context: Context) {
        applicationContext = context
        SecureSettingsStore.init(context)
    }

    fun getContext(): Context =
        applicationContext ?: throw IllegalStateException("ServiceLocator not initialized")

    fun provideIdeService(service: IdeService) {
        ideService = service
        register("IdeService", service)
    }

    fun getIdeService(): IdeService =
        ideService ?: throw IllegalStateException("IdeService not provided")

    fun <T : Any> register(name: String, service: T) {
        services[name] = service
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(name: String): T? =
        services[name] as? T

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolveOrThrow(name: String): T =
        services[name] as? T
            ?: throw IllegalStateException("Service not registered: $name")

    fun getScope(): CoroutineScope = scope

    fun shutdown() {
        services.clear()
        ideService = null
    }
}
