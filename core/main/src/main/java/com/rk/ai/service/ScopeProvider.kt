package com.rk.ai.service

import kotlinx.coroutines.CoroutineScope

interface ScopeProvider {
    val viewModelScope: CoroutineScope
}
